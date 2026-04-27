package com.iflytek.skillhub.domain.agent.report;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentReportServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentReportRepository reportRepository;
    @Mock private AuditLogService auditLogService;

    private AgentReportService service;

    @BeforeEach
    void setUp() {
        service = new AgentReportService(agentRepository, reportRepository, auditLogService);
    }

    private Agent active(long namespaceId, long agentId, String slug, String ownerId) {
        Agent a = new Agent(namespaceId, slug, slug, ownerId, AgentVisibility.PUBLIC);
        setField(a, "id", agentId);
        return a;
    }

    @Test
    void submitReport_createsPendingReport() {
        Agent agent = active(1L, 10L, "planner", "owner-1");
        when(agentRepository.findById(10L)).thenReturn(Optional.of(agent));
        when(reportRepository.existsByAgentIdAndReporterIdAndStatus(10L, "user-1", AgentReportStatus.PENDING))
                .thenReturn(false);
        when(reportRepository.save(any(AgentReport.class))).thenAnswer(inv -> {
            AgentReport r = inv.getArgument(0);
            setField(r, "id", 99L);
            return r;
        });

        AgentReport report = service.submitReport(10L, "user-1", "Inappropriate content", "details", "127.0.0.1", "JUnit");

        assertThat(report.getStatus()).isEqualTo(AgentReportStatus.PENDING);
        assertThat(report.getReason()).isEqualTo("Inappropriate content");
        assertThat(report.getDetails()).isEqualTo("details");
        verify(auditLogService).record("user-1", "REPORT_AGENT", "AGENT", 10L, null, "127.0.0.1", "JUnit",
                "{\"reportId\":99}");
    }

    @Test
    void submitReport_trimsReasonAndNullsBlankDetails() {
        Agent agent = active(1L, 10L, "planner", "owner-1");
        when(agentRepository.findById(10L)).thenReturn(Optional.of(agent));
        when(reportRepository.save(any(AgentReport.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentReport report = service.submitReport(10L, "user-1", "  reason  ", "   ", null, null);

        assertThat(report.getReason()).isEqualTo("reason");
        assertThat(report.getDetails()).isNull();
    }

    @Test
    void submitReport_rejectsDuplicatePendingReport() {
        Agent agent = active(1L, 10L, "planner", "owner-1");
        when(agentRepository.findById(10L)).thenReturn(Optional.of(agent));
        when(reportRepository.existsByAgentIdAndReporterIdAndStatus(10L, "user-1", AgentReportStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submitReport(10L, "user-1", "spam", null, null, null))
                .isInstanceOf(DomainBadRequestException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void submitReport_rejectsBlankReason() {
        assertThrows(DomainBadRequestException.class,
                () -> service.submitReport(10L, "user-1", "  ", null, null, null));
        verify(agentRepository, never()).findById(any());
    }

    @Test
    void submitReport_rejectsNullReason() {
        assertThrows(DomainBadRequestException.class,
                () -> service.submitReport(10L, "user-1", null, null, null, null));
    }

    @Test
    void submitReport_rejectsSelfReport() {
        Agent agent = active(1L, 10L, "planner", "owner-1");
        when(agentRepository.findById(10L)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.submitReport(10L, "owner-1", "spam", null, null, null))
                .isInstanceOf(DomainBadRequestException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void submitReport_rejectsArchivedAgent() {
        Agent agent = active(1L, 10L, "planner", "owner-1");
        setField(agent, "status", AgentStatus.ARCHIVED);
        when(agentRepository.findById(10L)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.submitReport(10L, "user-1", "spam", null, null, null))
                .isInstanceOf(DomainBadRequestException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void submitReport_throwsWhenAgentMissing() {
        when(agentRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.submitReport(10L, "user-1", "spam", null, null, null));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
