package com.iflytek.skillhub.domain.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.social.AgentRatingRepository;
import com.iflytek.skillhub.domain.agent.social.AgentStarRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Permanently deletes an Agent and all of its persisted artifacts so the slug
 * may be uploaded again without residual conflicts.
 *
 * <p>Mirrors {@link com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService}
 * but trimmed for the agent surface: no tags, no labels, no promotion, no version
 * stats, no security scans, no per-file rows. Cascades:
 * <ul>
 *   <li>{@code agent_version} rows (and {@code agent_review_task} via FK CASCADE)</li>
 *   <li>{@code agent_star} rows (no FK CASCADE — explicit delete)</li>
 *   <li>{@code agent_rating} rows (no FK CASCADE — explicit delete)</li>
 *   <li>Object-storage zips for each version's {@code packageObjectKey}</li>
 *   <li>The {@code agent} row itself</li>
 * </ul>
 */
@Service
public class AgentHardDeleteService {

    private static final Logger log = LoggerFactory.getLogger(AgentHardDeleteService.class);

    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentStarRepository agentStarRepository;
    private final AgentRatingRepository agentRatingRepository;
    private final ObjectStorageService objectStorageService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AgentHardDeleteService(AgentRepository agentRepository,
                                  AgentVersionRepository agentVersionRepository,
                                  AgentStarRepository agentStarRepository,
                                  AgentRatingRepository agentRatingRepository,
                                  ObjectStorageService objectStorageService,
                                  AuditLogService auditLogService,
                                  ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.agentStarRepository = agentStarRepository;
        this.agentRatingRepository = agentRatingRepository;
        this.objectStorageService = objectStorageService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void hardDeleteAgent(Agent agent, String actorUserId, String clientIp, String userAgent) {
        List<AgentVersion> versions = agentVersionRepository.findByAgentIdOrderBySubmittedAtDesc(agent.getId());

        List<String> storageKeys = new ArrayList<>();
        for (AgentVersion version : versions) {
            String key = version.getPackageObjectKey();
            if (key != null && !key.isBlank()) {
                storageKeys.add(key);
            }
        }
        deleteStorageAfterCommit(agent, storageKeys);

        agentStarRepository.deleteByAgentId(agent.getId());
        agentRatingRepository.deleteByAgentId(agent.getId());
        agentVersionRepository.deleteByAgentId(agent.getId());
        agentRepository.delete(agent);

        auditLogService.record(
                actorUserId,
                "DELETE_AGENT_HARD",
                "AGENT",
                agent.getId(),
                null,
                clientIp,
                userAgent,
                toAuditPayload(agent)
        );
    }

    private void deleteStorageAfterCommit(Agent agent, List<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tryDeleteStorage(storageKeys);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tryDeleteStorage(storageKeys);
            }
        });
    }

    private void tryDeleteStorage(List<String> storageKeys) {
        try {
            objectStorageService.deleteObjects(storageKeys);
        } catch (RuntimeException ex) {
            log.error("Failed to delete agent storage objects after hard-delete commit [keys={}]", storageKeys, ex);
        }
    }

    private String toAuditPayload(Agent agent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("namespaceId", agent.getNamespaceId());
        payload.put("slug", agent.getSlug());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize agent hard-delete audit payload", e);
        }
    }
}
