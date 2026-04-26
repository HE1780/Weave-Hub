import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { AgentCard } from '@/features/agent/agent-card'
import { useAgents } from '@/features/agent/use-agents'
import { Button } from '@/shared/ui/button'
import { SkeletonList } from '@/shared/components/skeleton-loader'

const TOP_N = 3

export function PopularAgents() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: agents, isLoading } = useAgents()

  const top = (agents ?? []).slice(0, TOP_N)

  return (
    <section
      className="relative z-10 w-full py-20 md:py-24 px-6"
      style={{ background: 'var(--bg-page, hsl(var(--background)))' }}
    >
      <div className="max-w-6xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2
              className="text-3xl font-bold tracking-tight mb-2"
              style={{ color: 'hsl(var(--foreground))' }}
            >
              {t('landing.popularAgents.title')}
            </h2>
            <p style={{ color: 'hsl(var(--text-secondary))' }}>
              {t('landing.popularAgents.description')}
            </p>
          </div>
          <Button variant="ghost" onClick={() => navigate({ to: '/agents' })}>
            {t('landing.popularAgents.viewAll')}
          </Button>
        </div>
        {isLoading ? (
          <SkeletonList count={TOP_N} />
        ) : top.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {top.map((agent, idx) => (
              <div key={agent.name} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                <AgentCard
                  agent={agent}
                  onClick={() => navigate({ to: `/agents/${encodeURIComponent(agent.name)}` })}
                />
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </section>
  )
}
