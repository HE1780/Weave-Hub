package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentMetadataParserTest {

    private final AgentMetadataParser parser = new AgentMetadataParser();

    @Test
    void parses_minimal_valid_AGENT_md() {
        String content = """
                ---
                name: customer-support-agent
                description: Triages incoming tickets.
                ---

                Body content.
                """;
        AgentMetadata m = parser.parse(content);

        assertEquals("customer-support-agent", m.name());
        assertEquals("Triages incoming tickets.", m.description());
        assertNull(m.version());
        assertEquals(AgentMetadata.DEFAULT_SOUL_FILE, m.soulFile());
        assertEquals(AgentMetadata.DEFAULT_WORKFLOW_FILE, m.workflowFile());
        assertTrue(m.skills().isEmpty());
        assertEquals("Body content.", m.body());
    }

    @Test
    void parses_optional_fields_version_soulFile_workflowFile() {
        String content = """
                ---
                name: a
                description: d
                version: 1.2.3
                soulFile: persona/soul.md
                workflowFile: workflows/main.yaml
                ---
                """;
        AgentMetadata m = parser.parse(content);

        assertEquals("1.2.3", m.version());
        assertEquals("persona/soul.md", m.soulFile());
        assertEquals("workflows/main.yaml", m.workflowFile());
    }

    @Test
    void parses_skills_list_with_mixed_shapes() {
        String content = """
                ---
                name: a
                description: d
                skills:
                  - name: ticket-classifier
                  - name: knowledge-base-search
                    version: 2.0.0
                ---
                """;
        AgentMetadata m = parser.parse(content);

        assertEquals(2, m.skills().size());
        assertEquals("ticket-classifier", m.skills().get(0).name());
        assertNull(m.skills().get(0).version());
        assertEquals("knowledge-base-search", m.skills().get(1).name());
        assertEquals("2.0.0", m.skills().get(1).version());
    }

    @Test
    void throws_when_name_is_missing() {
        String content = """
                ---
                description: d
                ---
                """;
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> parser.parse(content));
        assertTrue(ex.getMessage().toLowerCase().contains("name"));
    }

    @Test
    void throws_when_description_is_missing() {
        String content = """
                ---
                name: a
                ---
                """;
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> parser.parse(content));
        assertTrue(ex.getMessage().toLowerCase().contains("description"));
    }

    @Test
    void preserves_unknown_keys_in_frontmatter_map() {
        String content = """
                ---
                name: a
                description: d
                customField: hello
                tags:
                  - foo
                  - bar
                ---
                """;
        AgentMetadata m = parser.parse(content);

        assertEquals("hello", m.frontmatter().get("customField"));
        assertEquals(java.util.List.of("foo", "bar"), m.frontmatter().get("tags"));
    }

    @Test
    void throws_on_empty_content() {
        assertThrows(DomainBadRequestException.class, () -> parser.parse(""));
        assertThrows(DomainBadRequestException.class, () -> parser.parse(null));
    }

    @Test
    void throws_on_missing_frontmatter_delimiter() {
        String content = "name: a\ndescription: d\n";
        assertThrows(DomainBadRequestException.class, () -> parser.parse(content));
    }
}
