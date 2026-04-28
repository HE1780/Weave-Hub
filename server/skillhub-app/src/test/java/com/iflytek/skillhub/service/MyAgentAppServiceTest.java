package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.social.AgentStar;
import com.iflytek.skillhub.domain.agent.social.AgentStarRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.dto.AgentSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyAgentAppServiceTest {
    private final AgentStarRepository agentStarRepository = mock(AgentStarRepository.class);
    private final AgentRepository agentRepository = mock(AgentRepository.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final MyAgentAppService service = new MyAgentAppService(
            agentStarRepository, agentRepository, namespaceRepository);

    @Test
    void listMyAgentStars_paginatesAndOrdersByStarOrdering() {
        AgentStar s1 = new AgentStar(11L, "user1");
        AgentStar s2 = new AgentStar(22L, "user1");
        Page<AgentStar> starPage = new PageImpl<>(List.of(s1, s2), PageRequest.of(0, 10), 2);
        when(agentStarRepository.findByUserId(eq("user1"), eq(PageRequest.of(0, 10))))
                .thenReturn(starPage);

        Agent a1 = agent(11L, 100L, "a-one", "Agent One");
        Agent a2 = agent(22L, 100L, "a-two", "Agent Two");
        when(agentRepository.findByIdIn(List.of(11L, 22L))).thenReturn(List.of(a1, a2));

        Namespace ns = namespace(100L, "global");
        when(namespaceRepository.findByIdIn(List.of(100L))).thenReturn(List.of(ns));

        PageResponse<AgentSummaryResponse> result = service.listMyAgentStars("user1", 0, 10);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).id()).isEqualTo(11L);
        assertThat(result.items().get(0).slug()).isEqualTo("a-one");
        assertThat(result.items().get(0).namespace()).isEqualTo("global");
        assertThat(result.items().get(1).id()).isEqualTo(22L);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
    }

    @Test
    void listMyAgentStars_handlesEmptyPage() {
        when(agentStarRepository.findByUserId(eq("user1"), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<AgentSummaryResponse> result = service.listMyAgentStars("user1", 0, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void listMyAgentStars_filtersOutDeletedAgents() {
        AgentStar orphanStar = new AgentStar(99L, "user1");
        Page<AgentStar> starPage = new PageImpl<>(List.of(orphanStar), PageRequest.of(0, 10), 1);
        when(agentStarRepository.findByUserId(eq("user1"), eq(PageRequest.of(0, 10))))
                .thenReturn(starPage);
        when(agentRepository.findByIdIn(List.of(99L))).thenReturn(List.of()); // agent gone

        PageResponse<AgentSummaryResponse> result = service.listMyAgentStars("user1", 0, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(1); // total reflects star rows, not resolved agents
    }

    private Agent agent(Long id, Long namespaceId, String slug, String displayName) {
        Agent agent = new Agent(namespaceId, slug, displayName, "owner-x", AgentVisibility.PUBLIC);
        ReflectionTestUtils.setField(agent, "id", id);
        ReflectionTestUtils.setField(agent, "status", AgentStatus.ACTIVE);
        ReflectionTestUtils.setField(agent, "starCount", 0);
        ReflectionTestUtils.setField(agent, "ratingAvg", BigDecimal.ZERO);
        ReflectionTestUtils.setField(agent, "ratingCount", 0);
        ReflectionTestUtils.setField(agent, "downloadCount", 0);
        ReflectionTestUtils.setField(agent, "updatedAt", Instant.parse("2026-04-01T00:00:00Z"));
        return agent;
    }

    private Namespace namespace(Long id, String slug) {
        Namespace ns = new Namespace(slug, slug, "owner-x");
        ReflectionTestUtils.setField(ns, "id", id);
        return ns;
    }
}
