import { useNavigate } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Upload } from 'lucide-react'
import { agentsApi, type AgentDto } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'

/**
 * Lists agents owned by the current user.
 *
 * v1 limitation: backend list endpoint does not yet accept an ownerId filter,
 * so we fetch the public list and filter client-side. Acceptable for the early
 * deployment where the agent count is small. Tracked for Phase E follow-up.
 */
export function MyAgentsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user } = useAuth()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['agents', 'my', user?.userId],
    queryFn: async () => {
      const page = await agentsApi.list({ page: 0, size: 200 })
      return page.items
    },
    enabled: !!user?.userId,
  })

  const myAgents = (data ?? []).filter((a: AgentDto) => a.ownerId === user?.userId)

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <DashboardPageHeader
        title={t('agents.myAgents.title')}
        actions={
          <Button onClick={() => navigate({ to: '/dashboard/publish/agent' })}>
            <Upload className="mr-2 h-4 w-4" />
            {t('agents.myAgents.publish')}
          </Button>
        }
      />

      <div className="mt-6">
        {isLoading && <p className="text-muted-foreground">{t('agents.loading')}</p>}
        {isError && <p className="text-destructive">{t('agents.loadError')}</p>}
        {!isLoading && !isError && myAgents.length === 0 && (
          <EmptyState title={t('agents.myAgents.empty')} />
        )}
        {!isLoading && !isError && myAgents.length > 0 && (
          <div className="space-y-3">
            {myAgents.map((agent) => (
              <Card
                key={agent.id}
                className="p-4 cursor-pointer hover:bg-muted/30 transition-colors"
                onClick={() =>
                  navigate({
                    to: '/agents/$namespace/$slug',
                    params: { namespace: agent.namespace, slug: agent.slug },
                  })
                }
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="font-semibold">{agent.displayName}</div>
                    {agent.description && (
                      <div className="mt-1 text-sm text-muted-foreground">
                        {agent.description}
                      </div>
                    )}
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded border bg-secondary/40 text-muted-foreground">
                    {agent.visibility}
                  </span>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
