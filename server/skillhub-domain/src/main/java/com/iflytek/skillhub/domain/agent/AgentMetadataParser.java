package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parses an AGENT.md file (YAML frontmatter + markdown body) into AgentMetadata.
 *
 * <p>Mirrors the structure of {@code SkillMetadataParser} but accepts the
 * agent-specific frontmatter fields documented in
 * {@code docs/adr/0001-agent-package-format.md}.
 */
public class AgentMetadataParser {

    private static final String FRONTMATTER_DELIMITER = "---";

    public AgentMetadata parse(String content) {
        if (content == null || content.isBlank()) {
            throw new DomainBadRequestException("AGENT.md content is empty");
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            throw new DomainBadRequestException("AGENT.md must start with '---' frontmatter delimiter");
        }

        int firstDelimiterEnd = trimmed.indexOf('\n', FRONTMATTER_DELIMITER.length());
        if (firstDelimiterEnd == -1) {
            throw new DomainBadRequestException("AGENT.md frontmatter is empty");
        }

        int secondDelimiterStart = trimmed.indexOf(FRONTMATTER_DELIMITER, firstDelimiterEnd + 1);
        if (secondDelimiterStart == -1) {
            throw new DomainBadRequestException("AGENT.md missing closing '---' frontmatter delimiter");
        }

        String yamlContent = trimmed.substring(firstDelimiterEnd + 1, secondDelimiterStart).trim();
        String body = trimmed.substring(secondDelimiterStart + FRONTMATTER_DELIMITER.length()).trim();

        Map<String, Object> frontmatter = parseFrontmatter(yamlContent);

        String name = requireString(frontmatter, "name");
        String description = requireString(frontmatter, "description");
        String version = optionalString(frontmatter, "version");
        String soulFile = optionalString(frontmatter, "soulFile", AgentMetadata.DEFAULT_SOUL_FILE);
        String workflowFile = optionalString(frontmatter, "workflowFile", AgentMetadata.DEFAULT_WORKFLOW_FILE);
        List<AgentSkillRef> skills = parseSkills(frontmatter.get("skills"));

        return new AgentMetadata(name, description, version, soulFile, workflowFile, skills, body, frontmatter);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontmatter(String yamlContent) {
        try {
            Object parsed = new Yaml().load(yamlContent);
            if (parsed == null) {
                throw new DomainBadRequestException("AGENT.md frontmatter is empty");
            }
            if (!(parsed instanceof Map)) {
                throw new DomainBadRequestException("AGENT.md frontmatter must be a YAML mapping");
            }
            return (Map<String, Object>) parsed;
        } catch (DomainBadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainBadRequestException("AGENT.md YAML parse error: " + e.getMessage());
        }
    }

    private String requireString(Map<String, Object> frontmatter, String key) {
        Object value = frontmatter.get(key);
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new DomainBadRequestException("AGENT.md frontmatter missing required field: " + key);
        }
        return value.toString();
    }

    private String optionalString(Map<String, Object> frontmatter, String key) {
        return optionalString(frontmatter, key, null);
    }

    private String optionalString(Map<String, Object> frontmatter, String key, String defaultValue) {
        Object value = frontmatter.get(key);
        if (value == null || (value instanceof String s && s.isBlank())) {
            return defaultValue;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<AgentSkillRef> parseSkills(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (!(raw instanceof List<?> list)) {
            throw new DomainBadRequestException("AGENT.md frontmatter 'skills' must be a list");
        }
        List<AgentSkillRef> refs = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map)) {
                throw new DomainBadRequestException("AGENT.md frontmatter 'skills' entries must be mappings");
            }
            Map<String, Object> map = (Map<String, Object>) item;
            Object nameObj = map.get("name");
            if (nameObj == null || nameObj.toString().isBlank()) {
                throw new DomainBadRequestException("AGENT.md frontmatter skill entry missing 'name'");
            }
            Object versionObj = map.get("version");
            String version = versionObj == null ? null : versionObj.toString();
            refs.add(new AgentSkillRef(nameObj.toString(), version));
        }
        return refs;
    }
}
