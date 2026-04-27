package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLabelServiceTest {

    private final AgentRepository agentRepository = mock(AgentRepository.class);
    private final LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
    private final AgentLabelRepository agentLabelRepository = mock(AgentLabelRepository.class);
    private final LabelPermissionChecker labelPermissionChecker = new LabelPermissionChecker();

    private AgentLabelService service;
    private Agent agent;
    private LabelDefinition recommended;
    private LabelDefinition privileged;

    @BeforeEach
    void setUp() throws Exception {
        service = new AgentLabelService(
                agentRepository,
                labelDefinitionRepository,
                agentLabelRepository,
                labelPermissionChecker,
                3
        );
        agent = new Agent(7L, "demo-agent", "Demo Agent", "user-owner", AgentVisibility.PUBLIC);
        setId(agent, 100L);

        recommended = new LabelDefinition("ai-writing", LabelType.RECOMMENDED, true, 0, "user-admin");
        setLabelId(recommended, 1L);
        privileged = new LabelDefinition("official", LabelType.PRIVILEGED, true, 1, "user-admin");
        setLabelId(privileged, 2L);

        when(agentRepository.findById(100L)).thenReturn(Optional.of(agent));
        when(labelDefinitionRepository.findBySlugIgnoreCase("ai-writing")).thenReturn(Optional.of(recommended));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(privileged));
    }

    @Test
    void constructorRejectsNonPositivePerAgentLimit() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new AgentLabelService(
                agentRepository,
                labelDefinitionRepository,
                agentLabelRepository,
                labelPermissionChecker,
                0
        ));
        assertEquals("skillhub.label.max-per-agent must be greater than 0", ex.getMessage());
    }

    @Test
    void attachLabelByOwnerAddsAssociation() {
        when(agentLabelRepository.findByAgentId(100L)).thenReturn(List.of());
        when(agentLabelRepository.findByAgentIdAndLabelId(100L, 1L)).thenReturn(Optional.empty());
        AgentLabel saved = new AgentLabel(100L, 1L, "user-owner");
        when(agentLabelRepository.save(any(AgentLabel.class))).thenReturn(saved);

        AgentLabel result = service.attachLabel(100L, "ai-writing", "user-owner", Map.of(), Set.of());

        assertSame(saved, result);
        verify(agentLabelRepository, times(1)).save(any(AgentLabel.class));
    }

    @Test
    void attachLabelIsIdempotentWhenAlreadyAttached() {
        AgentLabel existing = new AgentLabel(100L, 1L, "user-owner");
        when(agentLabelRepository.findByAgentId(100L)).thenReturn(List.of(existing));
        when(agentLabelRepository.findByAgentIdAndLabelId(100L, 1L)).thenReturn(Optional.of(existing));

        AgentLabel result = service.attachLabel(100L, "ai-writing", "user-owner", Map.of(), Set.of());

        assertSame(existing, result);
        verify(agentLabelRepository, never()).save(any(AgentLabel.class));
    }

    @Test
    void attachLabelRejectsAnonymous() {
        assertThrows(DomainForbiddenException.class,
                () -> service.attachLabel(100L, "ai-writing", null, Map.of(), Set.of()));
        verify(agentLabelRepository, never()).save(any(AgentLabel.class));
    }

    @Test
    void attachLabelRejectsNonOwnerNonAdmin() {
        assertThrows(DomainForbiddenException.class,
                () -> service.attachLabel(100L, "ai-writing", "user-other", Map.of(), Set.of()));
    }

    @Test
    void attachLabelRejectsPrivilegedForNonSuperAdmin() {
        assertThrows(DomainForbiddenException.class,
                () -> service.attachLabel(100L, "official", "user-owner", Map.of(), Set.of()));
    }

    @Test
    void attachLabelAcceptsPrivilegedForSuperAdmin() {
        when(agentLabelRepository.findByAgentId(100L)).thenReturn(List.of());
        when(agentLabelRepository.findByAgentIdAndLabelId(100L, 2L)).thenReturn(Optional.empty());
        AgentLabel saved = new AgentLabel(100L, 2L, "user-platform");
        when(agentLabelRepository.save(any(AgentLabel.class))).thenReturn(saved);

        AgentLabel result = service.attachLabel(100L, "official", "user-platform", Map.of(), Set.of("SUPER_ADMIN"));
        assertSame(saved, result);
    }

    @Test
    void attachLabelEnforcesMaxPerAgent() {
        AgentLabel a = new AgentLabel(100L, 11L, "x");
        AgentLabel b = new AgentLabel(100L, 12L, "x");
        AgentLabel c = new AgentLabel(100L, 13L, "x");
        when(agentLabelRepository.findByAgentId(100L)).thenReturn(List.of(a, b, c));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.attachLabel(100L, "ai-writing", "user-owner", Map.of(), Set.of()));
        assertEquals("label.agent.too_many", ex.messageCode());
    }

    @Test
    void detachLabelRemovesAssociation() {
        AgentLabel existing = new AgentLabel(100L, 1L, "user-owner");
        when(agentLabelRepository.findByAgentIdAndLabelId(100L, 1L)).thenReturn(Optional.of(existing));

        service.detachLabel(100L, "ai-writing", "user-owner", Map.of(), Set.of());

        verify(agentLabelRepository).delete(existing);
    }

    @Test
    void detachLabelMissingAssociationThrows() {
        when(agentLabelRepository.findByAgentIdAndLabelId(100L, 1L)).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.detachLabel(100L, "ai-writing", "user-owner", Map.of(), Set.of()));
        assertEquals("label.agent.not_found", ex.messageCode());
    }

    @Test
    void namespaceAdminCanAttachAcrossOwnership() {
        when(agentLabelRepository.findByAgentId(100L)).thenReturn(List.of());
        when(agentLabelRepository.findByAgentIdAndLabelId(100L, 1L)).thenReturn(Optional.empty());
        AgentLabel saved = new AgentLabel(100L, 1L, "user-admin");
        when(agentLabelRepository.save(any(AgentLabel.class))).thenReturn(saved);

        AgentLabel result = service.attachLabel(
                100L,
                "ai-writing",
                "user-admin",
                Map.of(7L, NamespaceRole.ADMIN),
                Set.of()
        );

        assertSame(saved, result);
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    private void setLabelId(LabelDefinition label, Long id) throws Exception {
        Field idField = LabelDefinition.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(label, id);
    }
}
