package com.iflytek.skillhub.domain.agent.scan;

import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentSecurityScanServiceTest {

    @Mock private AgentVersionRepository agentVersionRepository;
    @InjectMocks private AgentSecurityScanService service;

    @Test
    void isEnabled_returns_true_for_placeholder() {
        assertTrue(service.isEnabled());
    }

    @Test
    void triggerScan_flips_SCANNING_to_UPLOADED_and_persists() {
        AgentVersion version = new AgentVersion(
                7L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "key", 1L);
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(version));
        when(agentVersionRepository.save(any(AgentVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        service.triggerScan(70L, "owner-1");

        assertEquals(AgentVersionStatus.UPLOADED, version.getStatus());
        verify(agentVersionRepository).save(version);
    }

    @Test
    void triggerScan_throws_when_version_missing() {
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.triggerScan(70L, "owner-1"));
    }
}
