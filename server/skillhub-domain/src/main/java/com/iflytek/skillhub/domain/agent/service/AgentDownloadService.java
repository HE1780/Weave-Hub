package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentTag;
import com.iflytek.skillhub.domain.agent.AgentTagRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatsRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.event.AgentDownloadedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Domain service that delivers packaged agents to callers. Mirrors
 * {@link com.iflytek.skillhub.domain.skill.service.SkillDownloadService} but
 * works with the agent_version.package_object_key field directly (the
 * uploaded zip is the canonical artifact, no per-file fallback bundle).
 */
@Service
public class AgentDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AgentDownloadService.class);

    private final NamespaceRepository namespaceRepository;
    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentVersionStatsRepository agentVersionStatsRepository;
    private final AgentTagRepository agentTagRepository;
    private final ObjectStorageService objectStorageService;
    private final AgentVisibilityChecker visibilityChecker;
    private final ApplicationEventPublisher eventPublisher;

    public AgentDownloadService(NamespaceRepository namespaceRepository,
                                AgentRepository agentRepository,
                                AgentVersionRepository agentVersionRepository,
                                AgentVersionStatsRepository agentVersionStatsRepository,
                                AgentTagRepository agentTagRepository,
                                ObjectStorageService objectStorageService,
                                AgentVisibilityChecker visibilityChecker,
                                ApplicationEventPublisher eventPublisher) {
        this.namespaceRepository = namespaceRepository;
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.agentVersionStatsRepository = agentVersionStatsRepository;
        this.agentTagRepository = agentTagRepository;
        this.objectStorageService = objectStorageService;
        this.visibilityChecker = visibilityChecker;
        this.eventPublisher = eventPublisher;
    }

    public record DownloadResult(
            Supplier<InputStream> contentSupplier,
            String filename,
            long contentLength,
            String contentType,
            String presignedUrl
    ) {
        public InputStream openContent() {
            return contentSupplier.get();
        }
    }

    public DownloadResult downloadLatest(String namespaceSlug,
                                         String agentSlug,
                                         String currentUserId,
                                         Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Agent agent = resolveAgent(namespace.getId(), agentSlug);
        assertCanDownload(namespace, agent, currentUserId, userNsRoles);

        AgentVersion latest = agentVersionRepository
                .findFirstByAgentIdAndStatusOrderByPublishedAtDesc(agent.getId(), AgentVersionStatus.PUBLISHED)
                .orElseThrow(() -> new DomainBadRequestException("error.agent.version.latest.unavailable", agentSlug));

        return downloadVersion(agent, latest);
    }

    public DownloadResult downloadVersion(String namespaceSlug,
                                          String agentSlug,
                                          String versionStr,
                                          String currentUserId,
                                          Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Agent agent = resolveAgent(namespace.getId(), agentSlug);
        assertCanDownload(namespace, agent, currentUserId, userNsRoles);

        AgentVersion version = agentVersionRepository.findByAgentIdAndVersion(agent.getId(), versionStr)
                .orElseThrow(() -> new DomainBadRequestException("error.agent.version.notFound", versionStr));

        assertDownloadableVersion(agent, version, currentUserId, userNsRoles);
        return downloadVersion(agent, version);
    }

    public DownloadResult downloadByTag(String namespaceSlug,
                                        String agentSlug,
                                        String tagName,
                                        String currentUserId,
                                        Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Agent agent = resolveAgent(namespace.getId(), agentSlug);
        assertCanDownload(namespace, agent, currentUserId, userNsRoles);

        AgentTag tag = agentTagRepository.findByAgentIdAndTagName(agent.getId(), tagName)
                .orElseThrow(() -> new DomainBadRequestException("error.agent.tag.notFound", tagName));

        AgentVersion version = agentVersionRepository.findById(tag.getVersionId())
                .orElseThrow(() -> new DomainBadRequestException("error.agent.tag.version.notFound", tagName));

        return downloadVersion(agent, version);
    }

    private DownloadResult downloadVersion(Agent agent, AgentVersion version) {
        DownloadResult result = buildDownloadResult(agent, version);
        if (version.getStatus() == AgentVersionStatus.PUBLISHED) {
            agentRepository.incrementDownloadCount(agent.getId());
            agentVersionStatsRepository.incrementDownloadCount(version.getId(), agent.getId());
            eventPublisher.publishEvent(new AgentDownloadedEvent(agent.getId(), version.getId()));
        }
        return result;
    }

    private DownloadResult buildDownloadResult(Agent agent, AgentVersion version) {
        String storageKey = version.getPackageObjectKey();
        if (storageKey == null || storageKey.isBlank() || !objectStorageService.exists(storageKey)) {
            log.warn("Agent package missing on storage [agentId={}, versionId={}, version={}]",
                    agent.getId(), version.getId(), version.getVersion());
            throw new DomainBadRequestException("error.agent.bundle.notFound");
        }
        ObjectMetadata metadata = objectStorageService.getMetadata(storageKey);
        String filename = buildFilename(agent, version);
        String presignedUrl = objectStorageService.generatePresignedUrl(storageKey, Duration.ofMinutes(10), filename);
        long size = metadata != null ? metadata.size() : (version.getPackageSizeBytes() != null ? version.getPackageSizeBytes() : 0L);
        String contentType = metadata != null && metadata.contentType() != null
                ? metadata.contentType()
                : "application/zip";
        return new DownloadResult(
                () -> objectStorageService.getObject(storageKey),
                filename,
                size,
                contentType,
                presignedUrl
        );
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug));
    }

    private Agent resolveAgent(Long namespaceId, String agentSlug) {
        return agentRepository.findByNamespaceIdAndSlug(namespaceId, agentSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentSlug));
    }

    private void assertCanDownload(Namespace namespace,
                                   Agent agent,
                                   String currentUserId,
                                   Map<Long, NamespaceRole> userNsRoles) {
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new DomainBadRequestException("error.agent.status.notActive");
        }
        if (currentUserId == null && !isAnonymousDownloadAllowed(namespace, agent)) {
            throw new DomainForbiddenException("error.agent.access.denied", agent.getSlug());
        }
        if (!visibilityChecker.canAccess(agent, currentUserId, normalizeRoles(userNsRoles), Set.of())) {
            throw new DomainForbiddenException("error.agent.access.denied", agent.getSlug());
        }
    }

    private boolean isAnonymousDownloadAllowed(Namespace namespace, Agent agent) {
        return namespace.getType() == NamespaceType.GLOBAL
                && agent.getVisibility() == AgentVisibility.PUBLIC;
    }

    private void assertDownloadableVersion(Agent agent,
                                           AgentVersion version,
                                           String currentUserId,
                                           Map<Long, NamespaceRole> userNsRoles) {
        switch (version.getStatus()) {
            case PUBLISHED -> {
                // Anyone with agent access can download.
            }
            case DRAFT, SCANNING, SCAN_FAILED, UPLOADED, PENDING_REVIEW, REJECTED -> {
                boolean isOwner = currentUserId != null && currentUserId.equals(agent.getOwnerId());
                NamespaceRole role = normalizeRoles(userNsRoles).get(agent.getNamespaceId());
                boolean isAdmin = role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
                if (!isOwner && !isAdmin) {
                    throw new DomainBadRequestException("error.agent.version.notDownloadable", version.getVersion());
                }
            }
            default -> throw new DomainBadRequestException("error.agent.version.notDownloadable", version.getVersion());
        }
    }

    private Map<Long, NamespaceRole> normalizeRoles(Map<Long, NamespaceRole> userNsRoles) {
        return userNsRoles == null ? Map.of() : userNsRoles;
    }

    private String buildFilename(Agent agent, AgentVersion version) {
        String baseName = agent.getDisplayName();
        if (baseName == null || baseName.isBlank()) {
            baseName = agent.getSlug();
        }
        return String.format("%s-%s.zip", sanitizeFilename(baseName), version.getVersion());
    }

    private String sanitizeFilename(String value) {
        String sanitized = value
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? "agent" : sanitized;
    }
}
