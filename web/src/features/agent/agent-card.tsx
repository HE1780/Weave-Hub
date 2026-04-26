import type { AgentSummary } from '@/api/agent-types'
import { Card } from '@/shared/ui/card'

interface AgentCardProps {
  agent: AgentSummary
  onClick?: () => void
}

/**
 * Visual list-card for one agent. Mirrors the role of skill-card.tsx
 * but with the simpler AgentSummary fields available today.
 */
export function AgentCard({ agent, onClick }: AgentCardProps) {
  const isInteractive = typeof onClick === 'function'

  return (
    <Card
      className="h-full p-5 cursor-pointer group relative overflow-hidden bg-white border shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
      onClick={onClick}
      onKeyDown={(event) => {
        if (!isInteractive) return
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onClick()
        }
      }}
      role={isInteractive ? 'link' : undefined}
      tabIndex={isInteractive ? 0 : undefined}
    >
      <div className="flex h-full flex-col">
        <div className="flex items-start justify-between mb-3">
          <h3 className="font-semibold text-lg group-hover:text-primary transition-colors" style={{ color: 'hsl(var(--foreground))' }}>
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
  )
}
