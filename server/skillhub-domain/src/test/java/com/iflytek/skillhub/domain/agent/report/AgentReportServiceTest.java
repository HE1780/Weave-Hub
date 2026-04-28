package com.iflytek.skillhub.domain.agent.report;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.report.event.AgentReportDismissedEvent;
import com.iflytek.skillhub.domain.agent.report.event.AgentReportResolvedEvent;
import com.iflytek.skillhub.domain.agent.service.AgentLifecycleService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentReportServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentReportRepository reportRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private AgentLifecycleService agentLifecycleService;
    @Mock private GovernanceNotificationService governanceNotificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AgentReportService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-29T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new AgentReportService(agentRepository, reportRepository, auditLogService,
                agentLifecycleService, governanceNotificationService, eventPublisher, clock);
    }

    private Agent active(long namespaceId, long agentId, String slug, String ownerId) {
        Agent a = new Agent(namespaceId, slug, slug, ownerId, AgentVisibility.PUBLIC);
        setField(a, "id", agentId);
        return a;
    }

    private AgentReport pending(long reportId, long agentId, String reporterId) {
        AgentReport r = new AgentReport(agentId, 1L, reporterId, "Spam", "details");
        setField(r, "id", reportId);
        return r;
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

    @Test
    void resolveReport_resolveOnly_marksResolvedAndPublishesEvent() {
        AgentReport report = pending(99L, 10L, "user-1");
        when(reportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(AgentReport.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentReport saved = service.resolveReport(99L, "admin-1", "ok", "127.0.0.1", "JUnit");

        assertThat(saved.getStatus()).isEqualTo(AgentReportStatus.RESOLVED);
        assertThat(saved.getHandledBy()).isEqualTo("admin-1");
        assertThat(saved.getHandleComment()).isEqualTo("ok");
        assertThat(saved.getHandledAt()).isEqualTo(Instant.parse("2026-04-29T12:00:00Z"));
        verify(auditLogService).record("admin-1", "RESOLVE_AGENT_REPORT", "AGENT_REPORT", 99L, null,
                "127.0.0.1", "JUnit", null);
        verifyNoInteractions(agentLifecycleService);

        ArgumentCaptor<AgentReportResolvedEvent> captor = ArgumentCaptor.forClass(AgentReportResolvedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().reportId()).isEqualTo(99L);
        assertThat(captor.getValue().dispositionLabel()).isEqualTo("resolve_only");

        verify(governanceNotificationService).notifyUser(
                eq("user-1"), eq("REPORT"), eq("AGENT_REPORT"), eq(99L), any(), contains("RESOLVED"));
    }

    @Test
    void resolveReport_resolveAndArchive_invokesAdminArchive() {
        AgentReport report = pending(99L, 10L, "user-1");
        when(reportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(AgentReport.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolveReport(99L, "admin-1", AgentReportDisposition.RESOLVE_AND_ARCHIVE, "violation",
                "127.0.0.1", "JUnit");

        verify(agentLifecycleService).archiveAsAdmin(eq(10L), eq("admin-1"), eq("127.0.0.1"), eq("JUnit"),
                eq("violation"));
        ArgumentCaptor<AgentReportResolvedEvent> captor = ArgumentCaptor.forClass(AgentReportResolvedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().dispositionLabel()).isEqualTo("resolve_and_archive");
    }

    @Test
    void resolveReport_alreadyHandled_throws() {
        AgentReport report = pending(99L, 10L, "user-1");
        report.setStatus(AgentReportStatus.RESOLVED);
        when(reportRepository.findById(99L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.resolveReport(99L, "admin-1", "ok", null, null))
                .isInstanceOf(DomainBadRequestException.class);
        verify(reportRepository, never()).save(any());
        verifyNoInteractions(agentLifecycleService);
    }

    @Test
    void resolveReport_missingReport_throwsNotFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.resolveReport(99L, "admin-1", "ok", null, null));
    }

    @Test
    void dismissReport_marksDismissedAndPublishesEvent() {
        AgentReport report = pending(99L, 10L, "user-1");
        when(reportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(AgentReport.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentReport saved = service.dismissReport(99L, "admin-1", "  not-actionable  ", "127.0.0.1", "JUnit");

        assertThat(saved.getStatus()).isEqualTo(AgentReportStatus.DISMISSED);
        assertThat(saved.getHandledBy()).isEqualTo("admin-1");
        assertThat(saved.getHandleComment()).isEqualTo("not-actionable");
        verify(auditLogService).record("admin-1", "DISMISS_AGENT_REPORT", "AGENT_REPORT", 99L, null,
                "127.0.0.1", "JUnit", null);
        verifyNoInteractions(agentLifecycleService);

        ArgumentCaptor<AgentReportDismissedEvent> captor = ArgumentCaptor.forClass(AgentReportDismissedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().reportId()).isEqualTo(99L);
        assertThat(captor.getValue().agentId()).isEqualTo(10L);

        verify(governanceNotificationService).notifyUser(
                eq("user-1"), eq("REPORT"), eq("AGENT_REPORT"), eq(99L), any(), contains("DISMISSED"));
    }

    @Test
    void listByStatus_returnsRepositoryPage() {
        Page<AgentReport> mockPage = new PageImpl<>(List.of());
        when(reportRepository.findByStatus(eq(AgentReportStatus.PENDING), any(Pageable.class)))
                .thenReturn(mockPage);

        Page<AgentReport> result = service.listByStatus(AgentReportStatus.PENDING, PageRequest.of(0, 20));

        assertThat(result).isSameAs(mockPage);
    }

    @Test
    void dismissReport_alreadyHandled_throws() {
        AgentReport report = pending(99L, 10L, "user-1");
        report.setStatus(AgentReportStatus.DISMISSED);
        when(reportRepository.findById(99L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.dismissReport(99L, "admin-1", null, null, null))
                .isInstanceOf(DomainBadRequestException.class);
        verify(reportRepository, never()).save(any());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field;
            try {
                field = target.getClass().getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                field = target.getClass().getSuperclass().getDeclaredField(fieldName);
            }
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
