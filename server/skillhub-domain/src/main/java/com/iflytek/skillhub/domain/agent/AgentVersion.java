package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_version")
public class AgentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(nullable = false, length = 64)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentVersionStatus status = AgentVersionStatus.DRAFT;

    @Column(name = "soul_md", columnDefinition = "TEXT")
    private String soulMd;

    @Column(name = "workflow_yaml", columnDefinition = "TEXT")
    private String workflowYaml;

    @Column(name = "manifest_yaml", columnDefinition = "TEXT")
    private String manifestYaml;

    @Column(name = "package_object_key", length = 256)
    private String packageObjectKey;

    @Column(name = "package_size_bytes")
    private Long packageSizeBytes;

    @Column(name = "submitted_by", nullable = false, length = 64)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    protected AgentVersion() {}

    public AgentVersion(Long agentId, String version, String submittedBy,
                        String manifestYaml, String soulMd, String workflowYaml,
                        String packageObjectKey, Long packageSizeBytes) {
        this.agentId = agentId;
        this.version = version;
        this.submittedBy = submittedBy;
        this.manifestYaml = manifestYaml;
        this.soulMd = soulMd;
        this.workflowYaml = workflowYaml;
        this.packageObjectKey = packageObjectKey;
        this.packageSizeBytes = packageSizeBytes;
    }

    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public String getVersion() { return version; }
    public AgentVersionStatus getStatus() { return status; }
    public String getSoulMd() { return soulMd; }
    public String getWorkflowYaml() { return workflowYaml; }
    public String getManifestYaml() { return manifestYaml; }
    public String getPackageObjectKey() { return packageObjectKey; }
    public Long getPackageSizeBytes() { return packageSizeBytes; }
    public String getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void submitForReview() {
        require(status == AgentVersionStatus.DRAFT,
                "Only DRAFT versions can be submitted for review (was " + status + ")");
        this.status = AgentVersionStatus.PENDING_REVIEW;
    }

    public void autoPublish() {
        require(status == AgentVersionStatus.DRAFT,
                "Only DRAFT versions can be auto-published (was " + status + ")");
        this.status = AgentVersionStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void approve() {
        require(status == AgentVersionStatus.PENDING_REVIEW,
                "Only PENDING_REVIEW versions can be approved (was " + status + ")");
        this.status = AgentVersionStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void reject() {
        require(status == AgentVersionStatus.PENDING_REVIEW,
                "Only PENDING_REVIEW versions can be rejected (was " + status + ")");
        this.status = AgentVersionStatus.REJECTED;
    }

    public void archive() {
        require(status == AgentVersionStatus.PUBLISHED,
                "Only PUBLISHED versions can be archived (was " + status + ")");
        this.status = AgentVersionStatus.ARCHIVED;
    }

    public void resubmitDraft() {
        require(status == AgentVersionStatus.REJECTED,
                "Only REJECTED versions can be returned to DRAFT (was " + status + ")");
        this.status = AgentVersionStatus.DRAFT;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainBadRequestException(message);
        }
    }
}
