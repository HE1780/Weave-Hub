import { Link } from '@tanstack/react-router'
import type { AgentSummary } from '@/api/agent-types'
import { Card } from '@/shared/ui/card'

interface AgentCardProps {
  agent: AgentSummary
}

/**
 * Visual list-card for one agent. Mirrors the role of skill-card.tsx
 * but with the simpler AgentSummary fields available today.
 *
 * Wraps the card in a TanStack {@link Link}, so cmd-click / middle-click
 * / right-click "open in new tab" all work like a real link, and screen
 * readers / keyboard users get the framework's focus & Enter handling.
 */
export function AgentCard({ agent }: AgentCardProps) {
  return (
    <Link
      to="/agents/$namespace/$slug"
      params={{ namespace: agent.namespace ?? 'global', slug: agent.name }}
      className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2 rounded-xl"
    >
      <Card glass className="h-full p-5 group relative overflow-hidden">
        <div className="flex h-full flex-col">
          <div className="flex items-start justify-between mb-3">
            <h3
              className="font-semibold text-lg group-hover:text-primary transition-colors"
              style={{ color: 'hsl(var(--foreground))' }}
            >
              {agent.name}
            </h3>
            {agent.version && (
              <span className="px-2.5 py-1 rounded-full bg-secondary/60 font-mono text-xs">
                v{agent.version}
              </span>
            )}
          </div>
          <p className="text-sm text-muted-foreground line-clamp-2 leading-relaxed">
            {agent.description}
          </p>
        </div>
      </Card>
    </Link>
  )
}
