package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.agent.*;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;  // existing 5-arg record
import com.iflytek.skillhub.domain.label.AgentLabel;
import com.iflytek.skillhub.domain.label.AgentLabelRepository;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentPromotionMaterializerTest {

    private AgentRepository agentRepository;
    private AgentVersionRepository agentVersionRepository;
    private AgentVersionStatsRepository agentVersionStatsRepository;
    private AgentTagRepository agentTagRepository;
    private AgentLabelRepository agentLabelRepository;
    private ApplicationEventPublisher eventPublisher;
    private AgentPromotionMaterializer materializer;

    @BeforeEach
    void setup() {
        agentRepository = mock(AgentRepository.class);
        agentVersionRepository = mock(AgentVersionRepository.class);
        agentVersionStatsRepository = mock(AgentVersionStatsRepository.class);
        agentTagRepository = mock(AgentTagRepository.class);
        agentLabelRepository = mock(AgentLabelRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        materializer = new AgentPromotionMaterializer(
                agentRepository, agentVersionRepository, agentVersionStatsRepository,
                agentTagRepository, agentLabelRepository, eventPublisher,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void supportsAgentSourceType() {
        assertEquals(SourceType.AGENT, materializer.supportedSourceType());
    }

    @Test
    void materializeCreatesNewAgentVersionAndStatsAndPublishesEvent() {
        Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
        AgentVersion sourceVersion = new AgentVersion(10L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "objects/abc", 1024L);
        sourceVersion.autoPublish();

        Agent saved = new Agent(99L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PUBLIC);
        AgentVersion savedVersion = new AgentVersion(saved.getId() == null ? 999L : saved.getId(),
                "1.0.0", "owner-1", "manifest", "soul", "workflow", "objects/abc", 1024L);

        when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
        when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(sourceVersion));
        when(agentRepository.findByNamespaceIdAndSlug(99L, "review-bot")).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenReturn(saved);
        when(agentVersionRepository.save(any(AgentVersion.class))).thenReturn(savedVersion);
        when(agentTagRepository.findByAgentId(10L)).thenReturn(List.of());
        when(agentLabelRepository.findByAgentId(10L)).thenReturn(List.of());

        PromotionRequest req = PromotionRequest.forAgent(10L, 20L, 99L, "user-1");

        MaterializationResult result = materializer.materialize(req);

        assertNotNull(result);
        verify(agentRepository).save(any(Agent.class));
        verify(agentVersionRepository).save(any(AgentVersion.class));
        verify(agentVersionStatsRepository).save(any(AgentVersionStats.class));
        verify(eventPublisher).publishEvent(any(AgentPublishedEvent.class));
    }

    @Test
    void materializeThrowsOnSlugCollision() {
        Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
        AgentVersion sourceVersion = new AgentVersion(10L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "objects/abc", 1024L);
        sourceVersion.autoPublish();

        when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
        when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(sourceVersion));
        when(agentRepository.findByNamespaceIdAndSlug(99L, "review-bot"))
                .thenReturn(Optional.of(source));

        PromotionRequest req = PromotionRequest.forAgent(10L, 20L, 99L, "user-1");

        assertThrows(DomainBadRequestException.class, () -> materializer.materialize(req));
    }

    @Test
    void copiesAllAgentLabelsToNewAgent() {
        Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
        AgentVersion sourceVersion = new AgentVersion(10L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "objects/abc", 1024L);
        sourceVersion.autoPublish();

        AgentLabel a = new AgentLabel(source.getId(), 100L, "owner-1");
        AgentLabel b = new AgentLabel(source.getId(), 101L, "owner-1");

        when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
        when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(sourceVersion));
        when(agentRepository.findByNamespaceIdAndSlug(99L, "review-bot")).thenReturn(Optional.empty());
        Agent saved = new Agent(99L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PUBLIC);
        when(agentRepository.save(any(Agent.class))).thenReturn(saved);
        when(agentVersionRepository.save(any(AgentVersion.class)))
                .thenReturn(new AgentVersion(saved.getId(), "1.0.0", "owner-1",
                        "manifest", "soul", "workflow", "objects/abc", 1024L));
        when(agentTagRepository.findByAgentId(10L)).thenReturn(List.of());
        when(agentLabelRepository.findByAgentId(10L)).thenReturn(List.of(a, b));

        PromotionRequest req = PromotionRequest.forAgent(10L, 20L, 99L, "user-1");
        materializer.materialize(req);

        // All source labels copied to the new agent (LabelDefinition has no namespace concept;
        // there is no scope filter to apply).
        verify(agentLabelRepository, times(2)).save(any(AgentLabel.class));
    }
}
