package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Pulls the three required agent files (AGENT.md, soul.md, workflow.yaml) out of
 * an extracted entry list.
 *
 * Reuses {@link SkillPackageArchiveExtractor} for zip extraction (the extractor is
 * agnostic about which package shape it's reading) and {@link com.iflytek.skillhub.domain.agent.AgentPackageValidator}
 * for structural validation. This helper just bridges them: given the validated
 * entries, returns the inline string contents that AgentPublishService persists.
 */
public final class AgentPackageContents {

    public static final String AGENT_MD = "AGENT.md";
    public static final String SOUL_MD = "soul.md";
    public static final String WORKFLOW_YAML = "workflow.yaml";

    private AgentPackageContents() {}

    public static Extracted extract(List<PackageEntry> entries) {
        String manifest = readNamed(entries, AGENT_MD);
        String soul = readNamed(entries, SOUL_MD);
        String workflow = readNamed(entries, WORKFLOW_YAML);
        return new Extracted(manifest, soul, workflow);
    }

    private static String readNamed(List<PackageEntry> entries, String name) {
        for (PackageEntry entry : entries) {
            if (entry.path().equals(name)) {
                return new String(entry.content(), StandardCharsets.UTF_8);
            }
        }
        throw new DomainBadRequestException("Required agent file missing: " + name);
    }

    public record Extracted(String manifestYaml, String soulMd, String workflowYaml) {}
}
