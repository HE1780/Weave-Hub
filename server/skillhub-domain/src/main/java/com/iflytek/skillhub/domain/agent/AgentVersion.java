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
    private AgentVersionStatus status = AgentVersionStatus.SCANNING;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_visibility", length = 16)
    private AgentVisibility requestedVisibility;

    @Column(name = "bundle_ready", nullable = false)
    private boolean bundleReady;

    @Column(name = "download_ready", nullable = false)
    private boolean downloadReady;

    @Column(name = "yanked_at")
    private Instant yankedAt;

    @Column(name = "yanked_by", length = 128)
    private String yankedBy;

    @Column(name = "yank_reason", columnDefinition = "TEXT")
    private String yankReason;

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
    public AgentVisibility getRequestedVisibility() { return requestedVisibility; }
    public boolean isBundleReady() { return bundleReady; }
    public boolean isDownloadReady() { return downloadReady; }
    public Instant getYankedAt() { return yankedAt; }
    public String getYankedBy() { return yankedBy; }
    public String getYankReason() { return yankReason; }
    public String getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void setStatus(AgentVersionStatus status) { this.status = status; }
    public void setRequestedVisibility(AgentVisibility requestedVisibility) {
        this.requestedVisibility = requestedVisibility;
    }
    public void setBundleReady(boolean bundleReady) { this.bundleReady = bundleReady; }
    public void setDownloadReady(boolean downloadReady) { this.downloadReady = downloadReady; }

    public void markScanPassed() {
        require(status == AgentVersionStatus.SCANNING,
                "Only SCANNING versions can be marked scan-passed (was " + status + ")");
        this.status = AgentVersionStatus.UPLOADED;
    }

    public void markScanFailed() {
        require(status == AgentVersionStatus.SCANNING,
                "Only SCANNING versions can be marked scan-failed (was " + status + ")");
        this.status = AgentVersionStatus.SCAN_FAILED;
    }

    public void retryScan() {
        require(status == AgentVersionStatus.SCAN_FAILED,
                "Only SCAN_FAILED versions can be re-scanned (was " + status + ")");
        this.status = AgentVersionStatus.SCANNING;
    }

    public void submitForReview(AgentVisibility requestedVisibility) {
        require(status == AgentVersionStatus.UPLOADED,
                "Only UPLOADED versions can be submitted for review (was " + status + ")");
        this.status = AgentVersionStatus.PENDING_REVIEW;
        this.requestedVisibility = requestedVisibility;
    }

    public void autoPublish() {
        require(status == AgentVersionStatus.UPLOADED,
                "Only UPLOADED versions can be auto-published (was " + status + ")");
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

    public void withdrawReview() {
        require(status == AgentVersionStatus.PENDING_REVIEW,
                "Only PENDING_REVIEW versions can be withdrawn (was " + status + ")");
        this.status = AgentVersionStatus.UPLOADED;
    }

    public void yank(String reason, String by) {
        require(status == AgentVersionStatus.PUBLISHED,
                "Only PUBLISHED versions can be yanked (was " + status + ")");
        this.status = AgentVersionStatus.YANKED;
        this.yankedAt = Instant.now();
        this.yankedBy = by;
        this.yankReason = reason;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainBadRequestException(message);
        }
    }
}
