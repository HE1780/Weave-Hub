# Agent Package Format v1 — Design

**Status:** Locked (2026-04-26)
**Owner:** Agent feature stream
**Related:** `docs/todo.md` (Agent配置管理功能实施计划)

## Summary

Defines the on-disk layout and validation contract for an Agent package. The format mirrors the existing Skill package convention: a directory of conventional files at root, validated for structural integrity only. Body shape and orchestration semantics are interpreted at runtime by the executor (built later), not by the registry.

## Motivation

Skillhub currently distributes Skill packages — directories with `SKILL.md` at root plus supporting files. We need a sibling artifact, the **Agent package**, that bundles a persona ("soul") with an orchestration of skill invocations.

Constraints driving this design:

- **Reuse the existing pattern.** The skill package validator (`SkillPackageValidator`, `SkillPackagePolicy`) and metadata parser (`SkillMetadataParser`) are battle-tested. Agents should follow the same conventions so authors, parsers, and storage all behave consistently.
- **Author freedom.** Orchestration is not something the registry should police. Authors compose freely; the executor interprets.
- **Easy to author.** YAML for structured parts, markdown for human content, plain text for the persona.

## Concept

An Agent is composed of four conceptual parts:

1. **Soul** — the persona/system prompt the LLM adopts when this agent runs.
2. **AGENT.md** — the manifest. Same role as `SKILL.md` for skills.
3. **Orchestration** — the workflow that defines which steps run, in what order, with what inputs.
4. **Skills** — existing Skill packages the agent depends on, referenced by name.

The package bundles the first three as files; skills are external dependencies referenced by name.

## Package Layout

```
<agent-package>/
├── AGENT.md          # manifest, required at root
├── soul.md           # freeform persona prompt, required at root
└── workflow.yaml     # orchestration, required at root
```

All three files **must exist at the package root**. Subdirectories may exist for additional assets (examples, fixtures, etc.) and are subject to the same path/extension/size policies as the skill package format.

## File Contracts

### AGENT.md (manifest)

Same shape as `SKILL.md`: YAML frontmatter delimited by `---`, followed by a markdown body.

```markdown
---
name: customer-support-agent
description: Triages incoming support tickets and drafts responses.
version: 1.0.0
soulFile: soul.md
workflowFile: workflow.yaml
skills:
  - name: ticket-classifier
  - name: knowledge-base-search
---

Human-readable description, usage examples, changelog notes.
```

**Required frontmatter fields** (validated):
- `name` (string)
- `description` (string)

**Optional frontmatter fields** (convention only — not validated):
- `version` — opaque string
- `soulFile` — relative path to the soul file (default: `soul.md`)
- `workflowFile` — relative path to the workflow file (default: `workflow.yaml`)
- `skills` — list of `{ name, version? }` entries declaring skill dependencies; `version` if present is an opaque string, not a constraint expression

Any additional frontmatter keys are preserved verbatim in the parsed metadata's generic map (same behavior as `SkillMetadata.frontmatter`).

The markdown body is opaque — preserved as-is for display.

### soul.md (persona prompt)

- Freeform markdown / plain text.
- Required to exist at root and be UTF-8 text (already enforced by `SkillPackagePolicy.validateContentMatchesExtension` for `.md`).
- No frontmatter required, no schema, no validation of content.
- The entire file content is the system prompt the executor feeds to the LLM at agent invocation time.

### workflow.yaml (orchestration)

- Required to exist at root.
- Must be parseable YAML.
- **No schema enforcement on the parsed structure.** Authors orchestrate freely.

The convention (interpreted by the executor, not the validator) is:

```yaml
steps:
  - id: classify
    type: skill
    skill: ticket-classifier
    inputs:
      ticket: $.input.ticket

  - id: reflect
    type: think
    prompt: "Given the classification, decide if escalation is needed."
    inputs:
      classification: $.steps.classify.output

  - id: search
    type: skill
    skill: knowledge-base-search
    inputs:
      category: $.steps.classify.output.category

output: $.steps.search.output
```

Conventional fields:
- `steps[]` — list of step objects, executed in array order.
- Each step typically has `id`, `type`, plus type-specific fields:
  - `type: skill` → `skill: <name>`, optional `inputs` map.
  - `type: think` → `prompt: <text>`, optional `inputs` map.
  - Other type values are allowed; the executor decides what to do with them.
- `inputs` values may use JSONPath-style refs (`$.input.*`, `$.steps.<id>.output.*`) — the executor resolves them; the validator does not.
- `output` (top-level) — JSONPath ref to the agent's final result.

The validator does **not** check uniqueness of step ids, presence of `type`, value of `type`, resolution of `skill` against declared deps, JSONPath syntax, or any other semantic property.

## Validation Contract

The agent package validator mirrors `SkillPackageValidator` and reuses `SkillPackagePolicy` for shared rules.

### Reused from `SkillPackagePolicy` (no changes)
- Path normalization (no `..`, no absolute paths, no drive prefixes)
- Extension allowlist (`.md`, `.yaml`, `.yml`, etc. are already on the list)
- Content-vs-extension matching (UTF-8 for text, magic bytes for binary)
- File count, single-file size, total package size limits

### Agent-specific (new in `AgentPackageValidator`)
1. **`AGENT.md` exists at root.** Error if missing.
2. **`soul.md` exists at root.** Error if missing.
3. **`workflow.yaml` exists at root.** Error if missing.
4. **`AGENT.md` frontmatter parses** with required fields `name` + `description`. Errors mirror the existing `SkillPackageValidator` translation of `SkillMetadataParser` exceptions.
5. **`workflow.yaml` parses as YAML.** Error if malformed. No schema check on the parsed structure.

### Deliberately NOT validated
- Step ids unique / non-empty
- `type` is a known value
- `skill` field references a declared dep
- `inputs` paths resolve
- skill version pins satisfy any constraint
- workflow.yaml has any specific top-level shape

These are runtime concerns the executor (out of scope for this spec) handles. Registry storage and listing only require structural integrity.

## Parsing Contract

A new `AgentMetadataParser` mirrors `SkillMetadataParser`:

```java
public record AgentMetadata(
    String name,
    String description,
    String version,
    String body,
    Map<String, Object> frontmatter
) {}
```

Same parsing semantics as `SkillMetadataParser.parse(content)`:
- Frontmatter delimited by `---`.
- Required: `name`, `description`. Optional: `version`. All other keys captured in the generic map.
- Snake YAML primary parser; loose line-based fallback for malformed YAML.

A future refactor may extract a shared `MarkdownFrontmatterParser`. For v1, copying the pattern into `AgentMetadataParser` keeps the change minimal and doesn't touch the existing skill code path.

## Frontend Implications (this session's scope)

The frontend agent UI work in this session does NOT depend on the validator or parser being implemented. It needs only the **shape** of an `Agent` object the API will eventually return:

```ts
interface Agent {
  name: string;
  description: string;
  version?: string;
  body?: string;          // rendered AGENT.md body
  soul?: string;          // contents of soul.md
  workflow?: unknown;     // parsed workflow.yaml (passed through as-is)
  frontmatter?: Record<string, unknown>;
}
```

Mock fixtures for `agents.tsx` and `agent-detail.tsx` use this shape. When the backend is built later, it returns the same shape and the frontend doesn't change.

## Out of Scope (this spec)

- Backend implementation of `AgentMetadataParser`, `AgentPackageValidator`, `AgentController`, database schema.
- Frontend integration with a real backend.
- Workflow executor.
- Agent publishing UI.
- Agent execution UI / runtime.

These are subsequent specs / plans.

## Open Questions

None for v1. The deliberate minimalism (structural validation only, freeform orchestration body) lets us evolve the executor and richer schema later without breaking already-published packages.
