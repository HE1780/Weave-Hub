import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { Upload } from 'lucide-react'
import type { AgentVisibilityFilter } from '@/api/client'
import { useAgents } from '@/features/agent/use-agents'
import { AgentCard } from '@/features/agent/agent-card'
import { useAuth } from '@/features/auth/use-auth'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { useDebounce } from '@/shared/hooks/use-debounce'
import { EmptyState } from '@/shared/components/empty-state'
import { Input } from '@/shared/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { buttonVariants } from '@/shared/ui/button'

const ALL = '__all__'

/**
 * Agents list page. Reads from useAgents() — real backend.
 * Search input has 300ms debounce; namespace + visibility selectors apply immediately.
 */
export function AgentsPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const { data: myNamespaces } = useMyNamespaces()

  const [rawQ, setRawQ] = useState('')
  const debouncedQ = useDebounce(rawQ, 300)
  const [namespace, setNamespace] = useState<string | undefined>(undefined)
  const [visibility, setVisibility] = useState<AgentVisibilityFilter | undefined>(undefined)

  const { data: agents, isLoading, isError } = useAgents({
    q: debouncedQ.trim() || undefined,
    namespace,
    visibility,
  })

  const hasActiveFilter = !!(debouncedQ.trim() || namespace || visibility)

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
            {user && (
              <div className="mt-6 flex justify-center">
                <Link to="/dashboard/publish/agent" className={buttonVariants()}>
                  <Upload className="mr-2 h-4 w-4" />
                  {t('agents.publish.title')}
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="mb-6 flex flex-col gap-3 md:flex-row md:items-center">
          <Input
            placeholder={t('agents.search.placeholder')}
            value={rawQ}
            onChange={(e) => setRawQ(e.target.value)}
            className="md:flex-1"
            aria-label={t('agents.search.placeholder')}
          />
          <Select
            value={namespace ?? ALL}
            onValueChange={(v) => setNamespace(v === ALL ? undefined : v)}
          >
            <SelectTrigger className="md:w-48" aria-label={t('agents.search.allNamespaces')}>
              <SelectValue placeholder={t('agents.search.allNamespaces')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t('agents.search.allNamespaces')}</SelectItem>
              {(myNamespaces ?? []).map((ns) => (
                <SelectItem key={ns.id} value={ns.slug}>
                  {ns.displayName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {user && (
            <Select
              value={visibility ?? ALL}
              onValueChange={(v) =>
                setVisibility(v === ALL ? undefined : (v as AgentVisibilityFilter))
              }
            >
              <SelectTrigger className="md:w-40" aria-label={t('agents.search.allVisibility')}>
                <SelectValue placeholder={t('agents.search.allVisibility')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>{t('agents.search.allVisibility')}</SelectItem>
                <SelectItem value="PUBLIC">{t('agents.search.visibilityPublic')}</SelectItem>
                <SelectItem value="NAMESPACE_ONLY">
                  {t('agents.search.visibilityNamespace')}
                </SelectItem>
                <SelectItem value="PRIVATE">{t('agents.search.visibilityPrivate')}</SelectItem>
              </SelectContent>
            </Select>
          )}
        </div>

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
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {agents.map((agent) => (
              <AgentCard
                key={`${agent.namespace ?? 'global'}/${agent.name}`}
                agent={agent}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
