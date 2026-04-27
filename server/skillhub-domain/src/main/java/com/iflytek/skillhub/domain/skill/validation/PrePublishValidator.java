package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;

import java.util.List;

/**
 * Extension point for content-aware validation that runs after package parsing but before a
 * skill or agent version is accepted for publishing.
 *
 * <p>Implementations that only need to scan {@link PackageEntry} content can be invoked from
 * the agent publish path (which does not produce a {@link SkillMetadata}) via
 * {@link #validateEntries(List, String, Long)}.
 */
public interface PrePublishValidator {
    ValidationResult validate(SkillPackageContext context);

    /**
     * Convenience overload for callers without a {@link SkillMetadata} — primarily the agent
     * publish flow, whose package format is described by {@code AgentMetadata} instead. The
     * context's {@code metadata} field is set to {@code null}; implementations that depend on
     * metadata should declare so or override this method.
     */
    default ValidationResult validateEntries(List<PackageEntry> entries,
                                             String publisherId,
                                             Long namespaceId) {
        return validate(new SkillPackageContext(entries, null, publisherId, namespaceId));
    }

    record SkillPackageContext(
        List<PackageEntry> entries,
        SkillMetadata metadata,
        String publisherId,
        Long namespaceId
    ) {}
}
