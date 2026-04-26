import { useTranslation } from 'react-i18next'
import { useNavigate } from '@tanstack/react-router'
import { useAgents } from '@/features/agent/use-agents'
import { AgentCard } from '@/features/agent/agent-card'
import { EmptyState } from '@/shared/components/empty-state'

/**
 * Agents list page. Reads from useAgents() — backed by mocks today, real API later.
 */
export function AgentsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: agents, isLoading, isError } = useAgents()

  return (
    <div className="min-h-screen bg-gradient-to-br from-background to-muted/20">
      <div className="relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-r from-purple-500/10 to-blue-500/10" />
        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16 md:py-24">
          <div className="text-center">
            <h1 className="text-4xl md:text-5xl font-bold tracking-tight mb-4">
              <span className="bg-gradient-to-r from-purple-600 to-blue-600 bg-clip-text text-transparent">
                {t('agents.title')}
              </span>
            </h1>
            <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
              {t('agents.subtitle')}
            </p>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        {isLoading && (
          <p className="text-center text-muted-foreground">{t('agents.loading')}</p>
        )}
        {isError && (
          <p className="text-center text-destructive">{t('agents.loadError')}</p>
        )}
        {!isLoading && !isError && agents && agents.length === 0 && (
          <EmptyState
            title={t('agents.emptyTitle')}
            description={t('agents.emptyDescription')}
          />
        )}
        {!isLoading && !isError && agents && agents.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {agents.map((agent) => (
              <AgentCard
                key={agent.name}
                agent={agent}
                onClick={() => navigate({ to: '/agents/$name', params: { name: agent.name } })}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
