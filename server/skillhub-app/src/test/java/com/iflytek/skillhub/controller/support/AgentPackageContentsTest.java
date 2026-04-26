package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentPackageContentsTest {

    private PackageEntry entry(String path, String content) {
        byte[] bytes = content.getBytes();
        return new PackageEntry(path, bytes, bytes.length, "text/plain");
    }

    @Test
    void extracts_three_files_when_all_present() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", "manifest"),
                entry("soul.md", "soul"),
                entry("workflow.yaml", "wf"),
                entry("examples/usage.md", "ignored extra")
        );

        AgentPackageContents.Extracted result = AgentPackageContents.extract(entries);

        assertEquals("manifest", result.manifestYaml());
        assertEquals("soul", result.soulMd());
        assertEquals("wf", result.workflowYaml());
    }

    @Test
    void throws_when_AGENT_md_missing() {
        List<PackageEntry> entries = List.of(
                entry("soul.md", "soul"),
                entry("workflow.yaml", "wf")
        );
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> AgentPackageContents.extract(entries));
        assertTrue(ex.getMessage().contains("AGENT.md"));
    }

    @Test
    void throws_when_soul_md_missing() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", "x"),
                entry("workflow.yaml", "wf")
        );
        assertThrows(DomainBadRequestException.class, () -> AgentPackageContents.extract(entries));
    }

    @Test
    void throws_when_workflow_yaml_missing() {
        List<PackageEntry> entries = List.of(
                entry("AGENT.md", "x"),
                entry("soul.md", "x")
        );
        assertThrows(DomainBadRequestException.class, () -> AgentPackageContents.extract(entries));
    }
}
