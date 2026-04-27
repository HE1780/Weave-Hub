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
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDownloadServiceTest {

    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final AgentRepository agentRepository = mock(AgentRepository.class);
    private final AgentVersionRepository agentVersionRepository = mock(AgentVersionRepository.class);
    private final AgentVersionStatsRepository agentVersionStatsRepository = mock(AgentVersionStatsRepository.class);
    private final AgentTagRepository agentTagRepository = mock(AgentTagRepository.class);
    private final ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AgentVisibilityChecker visibilityChecker = new AgentVisibilityChecker();

    private AgentDownloadService service;
    private Namespace globalNamespace;
    private Agent publicAgent;

    @BeforeEach
    void setUp() throws Exception {
        service = new AgentDownloadService(
                namespaceRepository,
                agentRepository,
                agentVersionRepository,
                agentVersionStatsRepository,
                agentTagRepository,
                objectStorageService,
                visibilityChecker,
                eventPublisher
        );
        globalNamespace = new Namespace("global", "Global", "owner");
        globalNamespace.setType(NamespaceType.GLOBAL);
        setId(globalNamespace, 1L);

        publicAgent = new Agent(1L, "planner", "Planner", "owner-1", AgentVisibility.PUBLIC);
        setId(publicAgent, 100L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(globalNamespace));
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(publicAgent));
    }

    @Test
    void downloadLatestStreamsBundleAndPublishesEvent() {
        AgentVersion published = publishedVersion(200L, "1.0.0", "packages/100/200/bundle.zip");
        when(agentVersionRepository.findFirstByAgentIdAndStatusOrderByPublishedAtDesc(100L, AgentVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(published));
        stubStorage("packages/100/200/bundle.zip", "hello");

        AgentDownloadService.DownloadResult result = service.downloadLatest("global", "planner", "user-1", Map.of());

        assertNotNull(result);
        assertEquals("Planner-1.0.0.zip", result.filename());
        assertEquals("application/zip", result.contentType());
        try (InputStream stream = result.openContent()) {
            assertNotNull(stream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        verify(eventPublisher).publishEvent(any(AgentDownloadedEvent.class));
        verify(agentRepository).incrementDownloadCount(100L);
        verify(agentVersionStatsRepository).incrementDownloadCount(200L, 100L);
    }

    @Test
    void downloadLatestRejectsAnonymousOnNonGlobalNamespace() {
        Namespace teamNs = new Namespace("team", "Team", "owner");
        try {
            setId(teamNs, 9L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(teamNs));
        Agent teamAgent = new Agent(9L, "planner", "Planner", "owner-1", AgentVisibility.PUBLIC);
        try {
            setId(teamAgent, 101L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(agentRepository.findByNamespaceIdAndSlug(9L, "planner")).thenReturn(Optional.of(teamAgent));

        assertThrows(DomainForbiddenException.class,
                () -> service.downloadLatest("team", "planner", null, Map.of()));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void downloadLatestRejectsArchivedAgent() {
        publicAgent.archive();
        assertThrows(DomainBadRequestException.class,
                () -> service.downloadLatest("global", "planner", "user-1", Map.of()));
    }

    @Test
    void downloadLatestRequiresPublishedVersion() {
        when(agentVersionRepository.findFirstByAgentIdAndStatusOrderByPublishedAtDesc(100L, AgentVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        assertThrows(DomainBadRequestException.class,
                () -> service.downloadLatest("global", "planner", "user-1", Map.of()));
    }

    @Test
    void downloadVersionAllowsOwnerOnUnpublishedDraft() {
        AgentVersion draft = draftVersion(201L, "0.9.0", "packages/100/201/bundle.zip");
        when(agentVersionRepository.findByAgentIdAndVersion(100L, "0.9.0")).thenReturn(Optional.of(draft));
        stubStorage("packages/100/201/bundle.zip", "draft");

        AgentDownloadService.DownloadResult result = service.downloadVersion(
                "global", "planner", "0.9.0", "owner-1", Map.of());
        assertNotNull(result);
        // event NOT fired for non-published downloads
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void downloadVersionRejectsNonOwnerOnUnpublished() {
        AgentVersion draft = draftVersion(201L, "0.9.0", "packages/100/201/bundle.zip");
        when(agentVersionRepository.findByAgentIdAndVersion(100L, "0.9.0")).thenReturn(Optional.of(draft));

        assertThrows(DomainBadRequestException.class,
                () -> service.downloadVersion("global", "planner", "0.9.0", "user-other", Map.of()));
    }

    @Test
    void downloadVersionRejectsMissingBundle() {
        AgentVersion published = publishedVersion(202L, "1.1.0", "packages/100/202/bundle.zip");
        when(agentVersionRepository.findByAgentIdAndVersion(100L, "1.1.0")).thenReturn(Optional.of(published));
        when(objectStorageService.exists("packages/100/202/bundle.zip")).thenReturn(false);

        assertThrows(DomainBadRequestException.class,
                () -> service.downloadVersion("global", "planner", "1.1.0", "user-1", Map.of()));
    }

    @Test
    void downloadByTagResolvesVersion() {
        AgentVersion published = publishedVersion(203L, "1.2.0", "packages/100/203/bundle.zip");
        AgentTag stable = new AgentTag(100L, "stable", 203L, "owner-1");
        when(agentTagRepository.findByAgentIdAndTagName(100L, "stable")).thenReturn(Optional.of(stable));
        when(agentVersionRepository.findById(203L)).thenReturn(Optional.of(published));
        stubStorage("packages/100/203/bundle.zip", "tagged");

        AgentDownloadService.DownloadResult result = service.downloadByTag(
                "global", "planner", "stable", "user-1", Map.of());
        assertEquals("Planner-1.2.0.zip", result.filename());
        verify(eventPublisher).publishEvent(any(AgentDownloadedEvent.class));
    }

    private AgentVersion publishedVersion(long id, String version, String packageKey) {
        AgentVersion v = new AgentVersion(100L, version, "owner-1",
                "manifest", "soul", "workflow", packageKey, 5L);
        try {
            Field idField = AgentVersion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(v, id);
            Field statusField = AgentVersion.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(v, AgentVersionStatus.PUBLISHED);
            Field publishedAt = AgentVersion.class.getDeclaredField("publishedAt");
            publishedAt.setAccessible(true);
            publishedAt.set(v, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return v;
    }

    private AgentVersion draftVersion(long id, String version, String packageKey) {
        AgentVersion v = new AgentVersion(100L, version, "owner-1",
                "manifest", "soul", "workflow", packageKey, 5L);
        try {
            Field idField = AgentVersion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(v, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return v;
    }

    private void stubStorage(String key, String body) {
        when(objectStorageService.exists(key)).thenReturn(true);
        when(objectStorageService.getMetadata(key)).thenReturn(new ObjectMetadata((long) body.length(), "application/zip", Instant.now()));
        when(objectStorageService.getObject(key)).thenReturn(new ByteArrayInputStream(body.getBytes()));
        when(objectStorageService.generatePresignedUrl(eq(key), any(Duration.class), anyString())).thenReturn(null);
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
