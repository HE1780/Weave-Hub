package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.agent.*;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.label.AgentLabel;
import com.iflytek.skillhub.domain.label.AgentLabelRepository;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Materializes a promoted Agent into the target namespace. Mirrors the
 * SkillPromotionMaterializer flow against agent-side repositories.
 *
 * <p>Sequence: validate source PUBLISHED → assert no slug collision in target →
 * create new Agent (visibility=PUBLIC) → create new AgentVersion (status=PUBLISHED
 * via autoPublish) → seed AgentVersionStats with downloadCount=0 → copy all
 * AgentLabels (no namespace filter — LabelDefinition has no namespace scope) →
 * copy all AgentTags repointed at the new agent and version → publish
 * AgentPublishedEvent.
 */
@Component
public class AgentPromotionMaterializer implements PromotionMaterializer {

    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentVersionStatsRepository agentVersionStatsRepository;
    private final AgentTagRepository agentTagRepository;
    private final AgentLabelRepository agentLabelRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AgentPromotionMaterializer(AgentRepository agentRepository,
                                      AgentVersionRepository agentVersionRepository,
                                      AgentVersionStatsRepository agentVersionStatsRepository,
                                      AgentTagRepository agentTagRepository,
                                      AgentLabelRepository agentLabelRepository,
                                      ApplicationEventPublisher eventPublisher,
                                      Clock clock) {
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.agentVersionStatsRepository = agentVersionStatsRepository;
        this.agentTagRepository = agentTagRepository;
        this.agentLabelRepository = agentLabelRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public SourceType supportedSourceType() {
        return SourceType.AGENT;
    }

    @Override
    public MaterializationResult materialize(PromotionRequest request) {
        Agent source = agentRepository.findById(request.getSourceAgentId())
                .orElseThrow(() -> new DomainNotFoundException("agent.not_found", request.getSourceAgentId()));
        AgentVersion sourceVersion = agentVersionRepository.findById(request.getSourceAgentVersionId())
                .orElseThrow(() -> new DomainNotFoundException("agent_version.not_found", request.getSourceAgentVersionId()));

        if (sourceVersion.getStatus() != AgentVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("promotion.version_not_published", request.getSourceAgentVersionId());
        }

        agentRepository.findByNamespaceIdAndSlug(request.getTargetNamespaceId(), source.getSlug())
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.target_agent_conflict", source.getSlug());
                });

        Agent newAgent = new Agent(request.getTargetNamespaceId(), source.getSlug(),
                source.getDisplayName(), source.getOwnerId(), AgentVisibility.PUBLIC);
        if (source.getDescription() != null) {
            newAgent.setDescription(source.getDescription());
        }
        newAgent = agentRepository.save(newAgent);

        AgentVersion newVersion = new AgentVersion(newAgent.getId(), sourceVersion.getVersion(),
                request.getSubmittedBy(), sourceVersion.getManifestYaml(),
                sourceVersion.getSoulMd(), sourceVersion.getWorkflowYaml(),
                sourceVersion.getPackageObjectKey(), sourceVersion.getPackageSizeBytes());
        // Promotion materializes an already-reviewed source — skip the scan placeholder
        // and walk straight through SCANNING → UPLOADED → PUBLISHED on the new row.
        newVersion.markScanPassed();
        newVersion.autoPublish();
        newVersion = agentVersionRepository.save(newVersion);

        // Reset stats for the materialized agent — never inherit source download counts
        agentVersionStatsRepository.save(new AgentVersionStats(newVersion.getId(), newAgent.getId()));

        // Copy labels — LabelDefinition has no namespace concept (all labels are platform-scope
        // today). Copy all source labels to the new agent verbatim. If LabelDefinition gains a
        // namespace scope later, this is the natural place to filter.
        // Use request.getSourceAgentId() (mirrors skill flow's request.getSourceVersionId()) —
        // the freshly-loaded source.getId() may be null depending on persistence backing.
        List<AgentLabel> sourceLabels = agentLabelRepository.findByAgentId(request.getSourceAgentId());
        Long newAgentId = newAgent.getId();
        for (AgentLabel link : sourceLabels) {
            agentLabelRepository.save(new AgentLabel(newAgentId, link.getLabelId(),
                    request.getSubmittedBy()));
        }

        // Copy tags — they are owned by the agent itself, all follow.
        List<AgentTag> sourceTags = agentTagRepository.findByAgentId(request.getSourceAgentId());
        Long newVersionId = newVersion.getId();
        for (AgentTag t : sourceTags) {
            agentTagRepository.save(new AgentTag(newAgentId, t.getTagName(), newVersionId,
                    request.getSubmittedBy()));
        }

        eventPublisher.publishEvent(new AgentPublishedEvent(
                newAgent.getId(), newVersion.getId(), request.getTargetNamespaceId(),
                request.getSubmittedBy(), newVersion.getPublishedAt()));

        return new MaterializationResult(newAgent.getId());
    }
}
