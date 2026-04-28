package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.PromotionService;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromotionPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PromotionService promotionService;

    @MockBean
    private PromotionRequestRepository promotionRequestRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private com.iflytek.skillhub.domain.namespace.NamespaceRepository namespaceRepository;

    @MockBean
    private GovernanceQueryRepository governanceQueryRepository;

    @MockBean
    private RbacService rbacService;

    @MockBean
    private ReviewPermissionChecker permissionChecker;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    void submitPromotion_passesNamespaceRolesToService() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("user-1", List.of(new NamespaceMember(5L, "user-1", NamespaceRole.ADMIN)));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(promotionService.submitPromotion(10L, 20L, 30L, "user-1", Map.of(5L, NamespaceRole.ADMIN), Set.of()))
                .willReturn(request);
        stubPromotionResponse(request);

        mockMvc.perform(post("/api/v1/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceSkillId\":10,\"sourceVersionId\":20,\"targetNamespaceId\":30}")
                        .with(csrf())
                        .with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void listPendingPromotions_forbidsRegularUser() throws Exception {
        stubNamespaceRoles("user-1", List.of());
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(permissionChecker.canListPendingPromotions(Set.of())).willReturn(false);

        mockMvc.perform(get("/api/v1/promotions/pending").with(auth("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(promotionRequestRepository, never()).findByStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getPromotionDetail_allowsSubmitter() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("user-1", List.of());
        given(promotionRequestRepository.findById(1L)).willReturn(Optional.of(request));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(promotionService.canViewPromotion(request, "user-1", Set.of())).willReturn(true);
        stubPromotionResponse(request);

        mockMvc.perform(get("/api/v1/promotions/1").with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.submittedBy").value("user-1"));
    }

    @Test
    void getPromotionDetail_forbidsUnrelatedUser() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("user-9", List.of());
        given(promotionRequestRepository.findById(1L)).willReturn(Optional.of(request));
        given(rbacService.getUserRoleCodes("user-9")).willReturn(Set.of());
        given(promotionService.canViewPromotion(request, "user-9", Set.of())).willReturn(false);

        mockMvc.perform(get("/api/v1/promotions/1").with(auth("user-9")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void submitPromotion_dispatchesAgentBranch() throws Exception {
        PromotionRequest request = createAgentPromotionRequest(2L, "user-1");
        stubNamespaceRoles("user-1", List.of(new NamespaceMember(5L, "user-1", NamespaceRole.ADMIN)));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(promotionService.submitAgentPromotion(101L, 202L, 30L, "user-1",
                Map.of(5L, NamespaceRole.ADMIN), Set.of()))
                .willReturn(request);
        stubAgentPromotionResponse(request);

        mockMvc.perform(post("/api/v1/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"AGENT\",\"sourceAgentId\":101,\"sourceAgentVersionId\":202,\"targetNamespaceId\":30}")
                        .with(csrf())
                        .with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(2L))
                .andExpect(jsonPath("$.data.sourceType").value("AGENT"))
                .andExpect(jsonPath("$.data.sourceAgentId").value(101));
        verify(promotionService, never()).submitPromotion(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void submitAgentPromotion_omitsTargetNamespaceId_resolvesToGlobal() throws Exception {
        PromotionRequest request = createAgentPromotionRequest(2L, "user-1");
        stubNamespaceRoles("user-1", List.of(new NamespaceMember(5L, "user-1", NamespaceRole.ADMIN)));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        com.iflytek.skillhub.domain.namespace.Namespace globalNs =
                org.mockito.Mockito.mock(com.iflytek.skillhub.domain.namespace.Namespace.class);
        given(globalNs.getId()).willReturn(99L);
        given(namespaceRepository.findBySlug("global")).willReturn(Optional.of(globalNs));
        given(promotionService.submitAgentPromotion(101L, 202L, 99L, "user-1",
                Map.of(5L, NamespaceRole.ADMIN), Set.of()))
                .willReturn(request);
        stubAgentPromotionResponse(request);

        mockMvc.perform(post("/api/v1/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"AGENT\",\"sourceAgentId\":101,\"sourceAgentVersionId\":202}")
                        .with(csrf())
                        .with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(2L));
        verify(promotionService).submitAgentPromotion(
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(202L),
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void submitPromotion_agentBranch_missingSourceAgentVersionId_returns400() throws Exception {
        stubNamespaceRoles("user-1", List.of());
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());

        mockMvc.perform(post("/api/v1/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"AGENT\",\"sourceAgentId\":101,\"targetNamespaceId\":30}")
                        .with(csrf())
                        .with(auth("user-1")))
                .andExpect(status().isBadRequest());
        verify(promotionService, never()).submitAgentPromotion(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void listPromotions_filtersBySourceType() throws Exception {
        PromotionRequest agentReq = createAgentPromotionRequest(2L, "user-1");
        stubNamespaceRoles("admin-1", List.of());
        given(rbacService.getUserRoleCodes("admin-1")).willReturn(Set.of("SKILL_ADMIN"));
        given(promotionRequestRepository.findByStatusAndSourceType(
                org.mockito.ArgumentMatchers.eq(ReviewTaskStatus.PENDING),
                org.mockito.ArgumentMatchers.eq(com.iflytek.skillhub.domain.review.SourceType.AGENT),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(agentReq)));
        given(governanceQueryRepository.getPromotionResponses(List.of(agentReq)))
                .willReturn(List.of(buildAgentResponseDto(agentReq)));

        mockMvc.perform(get("/api/v1/promotions?status=PENDING&sourceType=AGENT").with(auth("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].sourceType").value("AGENT"))
                .andExpect(jsonPath("$.data.items[0].sourceAgentId").value(101));
        verify(promotionRequestRepository, never()).findByStatus(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void stubAgentPromotionResponse(PromotionRequest request) {
        given(governanceQueryRepository.getPromotionResponse(request)).willReturn(buildAgentResponseDto(request));
    }

    private PromotionResponseDto buildAgentResponseDto(PromotionRequest request) {
        return new PromotionResponseDto(
                request.getId(),
                com.iflytek.skillhub.domain.review.SourceType.AGENT,
                null,
                "team-a",
                null,
                null,
                request.getSourceAgentId(),
                "agent-a",
                "1.0.0",
                "global",
                null,
                request.getTargetAgentId(),
                request.getStatus().name(),
                request.getSubmittedBy(),
                "Submitter",
                request.getReviewedBy(),
                null,
                request.getReviewComment(),
                request.getSubmittedAt(),
                request.getReviewedAt()
        );
    }

    private void stubPromotionResponse(PromotionRequest request) {
        given(governanceQueryRepository.getPromotionResponse(request)).willReturn(new PromotionResponseDto(
                request.getId(),
                com.iflytek.skillhub.domain.review.SourceType.SKILL,
                request.getSourceSkillId(),
                "team-a",
                "skill-a",
                "1.0.0",
                null,
                null,
                null,
                "global",
                request.getTargetSkillId(),
                null,
                request.getStatus().name(),
                request.getSubmittedBy(),
                "Submitter",
                request.getReviewedBy(),
                null,
                request.getReviewComment(),
                request.getSubmittedAt(),
                request.getReviewedAt()
        ));
    }

    private void stubNamespaceRoles(String userId, List<NamespaceMember> members) {
        given(namespaceMemberRepository.findByUserId(userId)).willReturn(members);
    }

    private RequestPostProcessor auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                Set.of()
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return authentication(authenticationToken);
    }

    private PromotionRequest createPromotionRequest(Long id, String submittedBy) {
        PromotionRequest request = new PromotionRequest(10L, 20L, 30L, submittedBy);
        setField(request, "id", id);
        setField(request, "status", ReviewTaskStatus.PENDING);
        return request;
    }

    private PromotionRequest createAgentPromotionRequest(Long id, String submittedBy) {
        PromotionRequest request = PromotionRequest.forAgent(101L, 202L, 30L, submittedBy);
        setField(request, "id", id);
        setField(request, "status", ReviewTaskStatus.PENDING);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
