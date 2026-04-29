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
  const displayName = agent.displayName ?? agent.slug

  return (
    <Link
      to="/agents/$namespace/$slug"
      params={{ namespace, slug: agent.slug }}
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
            {agent.ratingAvg !== undefined && (agent.ratingCount ?? 0) > 0 && (
              <span className="flex items-center gap-1">
                <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                </svg>
                {agent.ratingAvg.toFixed(1)}
              </span>
            )}
          </div>
        </div>
      </Card>
    </Link>
  )
}
