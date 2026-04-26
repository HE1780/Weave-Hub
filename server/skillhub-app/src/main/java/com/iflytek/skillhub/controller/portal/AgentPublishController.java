package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.controller.support.AgentPackageContents;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.agent.AgentPackageValidator;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.service.AgentPublishService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.AgentPublishResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Upload endpoint for Agent packages. Mirrors {@link SkillPublishController}.
 */
@RestController
@RequestMapping({"/api/v1/agents", "/api/web/agents"})
public class AgentPublishController extends BaseApiController {

    private final AgentPublishService agentPublishService;
    private final AgentPackageValidator agentPackageValidator;
    private final SkillPackageArchiveExtractor archiveExtractor;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final ObjectStorageService objectStorageService;

    public AgentPublishController(AgentPublishService agentPublishService,
                                  AgentPackageValidator agentPackageValidator,
                                  SkillPackageArchiveExtractor archiveExtractor,
                                  NamespaceRepository namespaceRepository,
                                  NamespaceMemberRepository namespaceMemberRepository,
                                  ObjectStorageService objectStorageService,
                                  ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.agentPublishService = agentPublishService;
        this.agentPackageValidator = agentPackageValidator;
        this.archiveExtractor = archiveExtractor;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.objectStorageService = objectStorageService;
    }

    @PostMapping("/{namespace}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentPublishResponse> publish(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") String visibility,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {

        if (principal == null) {
            throw new DomainForbiddenException("Authenticated principal required for publish");
        }

        AgentVisibility agentVisibility;
        try {
            agentVisibility = AgentVisibility.valueOf(visibility.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("Invalid visibility: " + visibility);
        }

        Namespace ns = namespaceRepository.findBySlug(namespace)
                .orElseThrow(() -> new DomainBadRequestException("Namespace not found: " + namespace));

        boolean isSuperAdmin = principal.platformRoles() != null
                && principal.platformRoles().contains("SUPER_ADMIN");
        if (!isSuperAdmin) {
            namespaceMemberRepository
                    .findByNamespaceIdAndUserId(ns.getId(), principal.userId())
                    .orElseThrow(() -> new DomainForbiddenException(
                            "Publisher is not a member of namespace " + namespace));
        }

        List<PackageEntry> entries;
        try {
            entries = archiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("Agent package invalid: " + e.getMessage());
        }

        AgentPackageValidator.ValidationResult validation = agentPackageValidator.validate(entries);
        if (!validation.isValid()) {
            throw new DomainBadRequestException(
                    "Agent package invalid: " + String.join("; ", validation.errors()));
        }

        AgentPackageContents.Extracted contents = AgentPackageContents.extract(entries);

        byte[] rawZip = file.getBytes();
        long size = rawZip.length;
        String objectKey = String.format("agents/%s/%s/%s/bundle.zip",
                namespace, validation.metadata().name(), validation.metadata().version());
        objectStorageService.putObject(
                objectKey, new ByteArrayInputStream(rawZip), size, "application/zip");

        AgentVersion version = agentPublishService.publish(
                ns.getId(),
                validation.metadata(),
                agentVisibility,
                contents.manifestYaml(),
                contents.soulMd(),
                contents.workflowYaml(),
                objectKey,
                size,
                principal.userId());

        AgentPublishResponse response = new AgentPublishResponse(
                version.getAgentId(),
                version.getId(),
                namespace,
                validation.metadata().name(),
                version.getVersion(),
                version.getStatus().name(),
                size);

        return ok("response.success.published", response);
    }
}
