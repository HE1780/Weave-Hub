package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClawHubAgentCompatControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;
    @MockBean private ClawHubAgentCompatAppService clawHubAgentCompatAppService;
    @MockBean private ClawHubCompatAppService clawHubCompatAppService;

    @Test
    void resolveByQuery_withTypeAgent_routesToAgentService() throws Exception {
        given(clawHubAgentCompatAppService.resolve(eq("planner"), eq("1.0.0"), any(), any()))
                .willReturn(new ClawHubResolveResponse(
                        new ClawHubResolveResponse.VersionInfo("1.0.0"),
                        new ClawHubResolveResponse.VersionInfo("1.0.0")));

        mockMvc.perform(get("/api/v1/resolve")
                        .param("slug", "planner")
                        .param("version", "1.0.0")
                        .param("type", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("1.0.0"));
        verifyNoInteractions(clawHubCompatAppService);
    }

    @Test
    void resolveByQuery_withoutType_routesToSkillService() throws Exception {
        mockMvc.perform(get("/api/v1/resolve")
                        .param("slug", "planner")
                        .param("version", "1.0.0"));
        verify(clawHubCompatAppService).resolveByQuery(eq("planner"), eq("1.0.0"), any(), any(), any());
    }

    @Test
    void resolvePath_withTypeAgent_routesToAgentService() throws Exception {
        given(clawHubAgentCompatAppService.resolve(eq("planner"), eq("latest"), any(), any()))
                .willReturn(new ClawHubResolveResponse(
                        new ClawHubResolveResponse.VersionInfo("2.0.0"),
                        new ClawHubResolveResponse.VersionInfo("2.0.0")));

        mockMvc.perform(get("/api/v1/resolve/planner").param("type", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("2.0.0"));
    }

    @Test
    void downloadPath_withTypeAgent_redirectsToAgentDownloadEndpoint() throws Exception {
        given(clawHubAgentCompatAppService.downloadLocationByPath(eq("planner"), eq("latest")))
                .willReturn("/api/v1/agents/global/planner/download");

        mockMvc.perform(get("/api/v1/download/planner").param("type", "agent"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/api/v1/agents/global/planner/download"));
        verifyNoInteractions(clawHubCompatAppService);
    }

    @Test
    void downloadQuery_withTypeAgent_redirectsToAgentDownloadEndpoint() throws Exception {
        given(clawHubAgentCompatAppService.downloadLocationByQuery(eq("team--planner"), eq("1.5.0")))
                .willReturn("/api/v1/agents/team/planner/versions/1.5.0/download");

        mockMvc.perform(get("/api/v1/download")
                        .param("slug", "team--planner")
                        .param("version", "1.5.0")
                        .param("type", "agent"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/api/v1/agents/team/planner/versions/1.5.0/download"));
    }

    @Test
    void downloadPath_withoutType_routesToSkillService() throws Exception {
        given(clawHubCompatAppService.downloadLocationByPath(eq("foo"), eq("latest")))
                .willReturn("/api/v1/skills/global/foo/download");

        mockMvc.perform(get("/api/v1/download/foo"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/api/v1/skills/global/foo/download"));
        verifyNoInteractions(clawHubAgentCompatAppService);
    }
}
