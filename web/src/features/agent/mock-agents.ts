import type { AgentDetail } from '@/api/agent-types'

/**
 * Static fixture used by useAgents/useAgentDetail until the backend lands.
 * Shape mirrors docs/adr/0001-agent-package-format.md.
 */
export const MOCK_AGENTS: AgentDetail[] = [
  {
    name: 'customer-support-agent',
    description: 'Triages incoming support tickets and drafts responses.',
    version: '1.0.0',
    body: 'Routes a ticket through classification, reflection, and a knowledge-base lookup before drafting a response.',
    soul: 'You are a calm, empathetic support specialist. Read every ticket twice before responding.',
    workflow: {
      steps: [
        {
          id: 'classify',
          type: 'skill',
          skill: 'ticket-classifier',
          inputs: { ticket: '$.input.ticket' },
        },
        {
          id: 'reflect',
          type: 'think',
          prompt: 'Given the classification, decide if escalation is needed.',
          inputs: { classification: '$.steps.classify.output' },
        },
        {
          id: 'search',
          type: 'skill',
          skill: 'knowledge-base-search',
          inputs: { category: '$.steps.classify.output.category' },
        },
      ],
      output: '$.steps.search.output',
    },
    frontmatter: {
      skills: [{ name: 'ticket-classifier' }, { name: 'knowledge-base-search' }],
    },
  },
  {
    name: 'release-notes-writer',
    description: 'Drafts release notes from a list of merged pull requests.',
    version: '0.2.0',
    soul: 'You write crisp, factual release notes. No marketing language.',
    workflow: {
      steps: [
        {
          id: 'summarize',
          type: 'think',
          prompt: 'Summarize each PR in one sentence focused on user impact.',
        },
      ],
    },
  },
  {
    name: 'minimal-agent',
    description: 'A stub agent with no workflow yet.',
  },
]

export function findMockAgent(name: string): AgentDetail | undefined {
  return MOCK_AGENTS.find((agent) => agent.name === name)
}
