import { Link } from '@tanstack/react-router'
import type { AgentSummary } from '@/api/agent-types'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { formatCompactCount } from '@/shared/lib/number-format'
import { Bookmark } from 'lucide-react'

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
  const namespace = agent.namespace ?? 'global'
  const displayName = agent.displayName ?? agent.name

  return (
    <Link
      to="/agents/$namespace/$slug"
      params={{ namespace, slug: agent.name }}
      className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2 rounded-xl"
    >
      <Card glass className="h-full min-h-[220px] p-5 group relative overflow-hidden">
        <div className="flex h-full flex-col">
          <div className="flex items-start justify-between mb-3">
            <div className="space-y-2">
              <h3
                className="font-semibold text-lg group-hover:text-primary transition-colors"
                style={{ color: 'hsl(var(--foreground))' }}
              >
                {displayName}
              </h3>
            </div>
            <div className="flex items-center gap-2">
              <NamespaceBadge type={namespace === 'global' ? 'GLOBAL' : 'TEAM'} name={`@${namespace}`} />
            </div>
          </div>
          <p className="text-sm text-muted-foreground mb-4 line-clamp-2 leading-relaxed">
            {agent.description}
          </p>
          <div className="mt-auto flex items-center gap-4 text-xs text-muted-foreground">
            <span className="px-2.5 py-1 rounded-full bg-secondary/60 font-mono">
              {agent.version ? `v${agent.version}` : '—'}
            </span>
            <span className="flex items-center gap-1">
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
              </svg>
              {formatCompactCount(agent.downloadCount ?? 0)}
            </span>
            <span className="flex items-center gap-1">
              <Bookmark className="w-3.5 h-3.5" />
              {agent.starCount ?? 0}
            </span>
          </div>
        </div>
      </Card>
    </Link>
  )
}
