/**
 * Agent package shape — frontend mirror of docs/adr/0001-agent-package-format.md.
 *
 * The backend is not yet implemented; these types are the contract the eventual
 * API will return. Mock data uses the same shape so the UI doesn't change when
 * the real fetch is wired up.
 */

export interface AgentSummary {
  name: string
  description: string
  version?: string
}

export interface AgentDetail extends AgentSummary {
  body?: string
  soul?: string
  workflow?: AgentWorkflow
  frontmatter?: Record<string, unknown>
}

export interface AgentWorkflow {
  steps: AgentWorkflowStep[]
  output?: string
}

export interface AgentWorkflowStep {
  id: string
  type?: string
  skill?: string
  prompt?: string
  inputs?: Record<string, string>
}
