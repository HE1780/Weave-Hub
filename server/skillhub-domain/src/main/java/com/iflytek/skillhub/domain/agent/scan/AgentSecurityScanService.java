package com.iflytek.skillhub.domain.agent.scan;

import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Placeholder agent security scanner. DEV ONLY: synchronously transitions
 * SCANNING -> UPLOADED so the rest of the publish flow can be exercised
 * without a real scanner backend. Aligned with SecurityScanService surface
 * (triggerScan + isEnabled) so a real async implementation can replace this
 * later without changing call sites.
 */
@Service
public class AgentSecurityScanService {

    private static final Logger log = LoggerFactory.getLogger(AgentSecurityScanService.class);

    private final AgentVersionRepository agentVersionRepository;

    public AgentSecurityScanService(AgentVersionRepository agentVersionRepository) {
        this.agentVersionRepository = agentVersionRepository;
    }

    public boolean isEnabled() {
        return true;
    }

    @Transactional
    public void triggerScan(Long versionId, String publisherId) {
        AgentVersion version = agentVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("AgentVersion not found: " + versionId));
        log.info("Agent scan placeholder: passing versionId={} publisherId={}", versionId, publisherId);
        version.markScanPassed();
        agentVersionRepository.save(version);
    }
}
