package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates an unzipped agent package against the contract defined in
 * docs/adr/0001-agent-package-format.md.
 *
 * Required at root: AGENT.md, soul.md, workflow.yaml.
 * - AGENT.md must parse via AgentMetadataParser (covers required frontmatter fields).
 * - workflow.yaml must parse as valid YAML (semantics interpreted by the executor, not us).
 * - File paths must satisfy SkillPackagePolicy (no traversal, allowed extension, size caps).
 */
public class AgentPackageValidator {

    private static final String AGENT_MD = "AGENT.md";
    private static final String SOUL_MD = "soul.md";
    private static final String WORKFLOW_YAML = "workflow.yaml";

    private final AgentMetadataParser metadataParser;

    public AgentPackageValidator(AgentMetadataParser metadataParser) {
        this.metadataParser = metadataParser;
    }

    public ValidationResult validate(List<PackageEntry> entries) {
        List<String> errors = new ArrayList<>();
        Set<String> normalizedPaths = new HashSet<>();
        PackageEntry agentMd = null;
        PackageEntry soulMd = null;
        PackageEntry workflowYaml = null;

        long totalSize = 0;

        for (PackageEntry entry : entries) {
            String normalized;
            try {
                normalized = SkillPackagePolicy.normalizeEntryPath(entry.path());
            } catch (IllegalArgumentException e) {
                errors.add("Invalid path: " + entry.path() + " (" + e.getMessage() + ")");
                continue;
            }
            if (!normalizedPaths.add(normalized)) {
                errors.add("Duplicate entry: " + normalized);
                continue;
            }
            if (entry.size() > SkillPackagePolicy.MAX_SINGLE_FILE_SIZE) {
                errors.add("File too large: " + normalized);
            }
            totalSize += entry.size();

            switch (normalized) {
                case AGENT_MD -> agentMd = entry;
                case SOUL_MD -> soulMd = entry;
                case WORKFLOW_YAML -> workflowYaml = entry;
                default -> { /* allowed: extra files, validated by extension */ }
            }
        }

        if (totalSize > SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE) {
            errors.add("Total package size exceeds limit");
        }
        if (entries.size() > SkillPackagePolicy.MAX_FILE_COUNT) {
            errors.add("Too many files in package");
        }

        if (agentMd == null) {
            errors.add("Required file missing at package root: AGENT.md");
        }
        if (soulMd == null) {
            errors.add("Required file missing at package root: soul.md");
        }
        if (workflowYaml == null) {
            errors.add("Required file missing at package root: workflow.yaml");
        }

        AgentMetadata metadata = null;
        if (agentMd != null) {
            try {
                metadata = metadataParser.parse(new String(agentMd.content(), StandardCharsets.UTF_8));
            } catch (DomainBadRequestException e) {
                errors.add("AGENT.md invalid: " + e.getMessage());
            }
        }

        if (workflowYaml != null) {
            try {
                String content = new String(workflowYaml.content(), StandardCharsets.UTF_8);
                new Yaml().load(content);
            } catch (YAMLException e) {
                errors.add("workflow.yaml is not valid YAML: " + e.getMessage());
            }
        }

        return new ValidationResult(errors, metadata);
    }

    public record ValidationResult(List<String> errors, AgentMetadata metadata) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}
