import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { Upload } from 'lucide-react'
import { useAgents } from '@/features/agent/use-agents'
import { AgentCard } from '@/features/agent/agent-card'
import { useAuth } from '@/features/auth/use-auth'
import { SearchBar } from '@/features/search/search-bar'
import { useDebounce } from '@/shared/hooks/use-debounce'
import { EmptyState } from '@/shared/components/empty-state'
import { buttonVariants } from '@/shared/ui/button'

/**
 * Agents list page. Reads from useAgents() — real backend.
 * Search input has 300ms debounce; namespace + visibility selectors apply immediately.
 */
export function AgentsPage() {
  const { t } = useTranslation()
  const { user } = useAuth()

  const [rawQ, setRawQ] = useState('')
  const debouncedQ = useDebounce(rawQ, 300)

  const { data: agents, isLoading, isError } = useAgents({
    q: debouncedQ.trim() || undefined,
  })

  const hasActiveFilter = !!debouncedQ.trim()

  return (
    <div className="space-y-20">
      <div className="text-center space-y-8 py-16 animate-fade-up">
        <div className="space-y-4">
          <h1 className="text-6xl md:text-7xl lg:text-8xl font-bold text-brand-gradient leading-tight">
            {t('agents.title')}
          </h1>
          <p className="text-xl md:text-2xl max-w-2xl mx-auto" style={{ color: 'hsl(var(--text-secondary))' }}>
            {t('agents.subtitle')}
          </p>
        </div>

        <div className="max-w-2xl mx-auto animate-fade-up delay-1">
          <SearchBar
            value={rawQ}
            placeholder={t('agents.search.placeholder')}
            isSearching={isLoading}
            onChange={setRawQ}
            onSearch={setRawQ}
          />
        </div>

        {user && (
          <div className="flex items-center justify-center gap-4 animate-fade-up delay-2">
            <Link to="/dashboard/publish/agent" className={buttonVariants()}>
              <Upload className="mr-2 h-4 w-4" />
              {t('agents.publish.title')}
            </Link>
          </div>
        )}
      </div>

      <section className="space-y-6 animate-fade-up">
        {isLoading && (
          <p className="text-center text-muted-foreground">{t('agents.loading')}</p>
        )}
        {isError && (
          <p className="text-center text-destructive">{t('agents.loadError')}</p>
        )}
        {!isLoading && !isError && agents && agents.length === 0 && (
          <EmptyState
            title={hasActiveFilter ? t('agents.search.noResults') : t('agents.emptyTitle')}
            description={hasActiveFilter ? '' : t('agents.emptyDescription')}
          />
        )}
        {!isLoading && !isError && agents && agents.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {agents.map((agent) => (
              <AgentCard
                key={`${agent.namespace ?? 'global'}/${agent.name}`}
                agent={agent}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
