package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentPackageValidatorTest {

    private final AgentPackageValidator validator =
            new AgentPackageValidator(new AgentMetadataParser());

    private PackageEntry entry(String path, String content) {
        byte[] bytes = content.getBytes();
        return new PackageEntry(path, bytes, bytes.length, "text/plain");
    }

    private static final String VALID_AGENT_MD = """
            ---
            name: a
            description: d
            ---
            body
            """;

    private static final String VALID_WORKFLOW_YAML = """
            steps:
              - id: greet
                type: llm
                prompt: hello
            """;

    @Test
    void happy_path_accepts_minimal_agent_package() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", VALID_AGENT_MD),
                entry("soul.md", "You are helpful."),
                entry("workflow.yaml", VALID_WORKFLOW_YAML)
        );

        AgentPackageValidator.ValidationResult result = validator.validate(entries);

        assertTrue(result.errors().isEmpty(), () -> "expected no errors, got: " + result.errors());
        assertEquals("a", result.metadata().name());
    }

    @Test
    void missing_AGENT_md_is_reported() {
        List<PackageEntry> entries = List.of(
                entry("soul.md", "x"),
                entry("workflow.yaml", VALID_WORKFLOW_YAML)
        );
        var result = validator.validate(entries);
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(s -> s.contains("AGENT.md")));
    }

    @Test
    void missing_soul_md_is_reported() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", VALID_AGENT_MD),
                entry("workflow.yaml", VALID_WORKFLOW_YAML)
        );
        var result = validator.validate(entries);
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(s -> s.contains("soul.md")));
    }

    @Test
    void missing_workflow_yaml_is_reported() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", VALID_AGENT_MD),
                entry("soul.md", "x")
        );
        var result = validator.validate(entries);
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(s -> s.contains("workflow.yaml")));
    }

    @Test
    void invalid_workflow_yaml_is_reported() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", VALID_AGENT_MD),
                entry("soul.md", "x"),
                entry("workflow.yaml", "key: value\n  bad: indent")
        );
        var result = validator.validate(entries);
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(s -> s.toLowerCase().contains("workflow")));
    }

    @Test
    void path_traversal_entry_is_rejected() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", VALID_AGENT_MD),
                entry("soul.md", "x"),
                entry("workflow.yaml", VALID_WORKFLOW_YAML),
                entry("../escape.txt", "evil")
        );
        var result = validator.validate(entries);
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void invalid_AGENT_md_frontmatter_is_reported() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", "---\nno-name-field: true\n---\n"),
                entry("soul.md", "x"),
                entry("workflow.yaml", VALID_WORKFLOW_YAML)
        );
        var result = validator.validate(entries);
        assertFalse(result.errors().isEmpty());
    }
}
