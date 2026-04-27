import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/use-auth'
import { useMySkills } from '@/shared/hooks/use-user-queries'
import { agentsApi, type AgentDto } from '@/api/client'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'

/**
 * "My Weave" aggregate page — surfaces the user's own skills and agents in one place.
 *
 * Reuses existing dashboard hooks; this is a discovery shell, not a governance page.
 * Governance actions (archive, withdraw, promote) stay on /dashboard/skills.
 */
export function MyWeavePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user } = useAuth()

  const { data: skillsPage, isLoading: skillsLoading } = useMySkills({
    page: 0,
    size: 10,
  })

  const { data: agentsList, isLoading: agentsLoading } = useQuery({
    queryKey: ['agents', 'my', user?.userId],
    queryFn: async () => {
      const page = await agentsApi.list({ page: 0, size: 200 })
      return page.items
    },
    enabled: !!user?.userId,
  })

  const myAgents = (agentsList ?? []).filter((a: AgentDto) => a.ownerId === user?.userId)
  const skills = skillsPage?.items ?? []

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-12">
      <DashboardPageHeader
        title={t('myWeave.title')}
        subtitle={t('myWeave.subtitle')}
      />

      <section>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold tracking-tight">
            {t('myWeave.skillsHeading')}
          </h2>
          <Button variant="outline" size="sm" onClick={() => navigate({ to: '/dashboard/skills' })}>
            {t('myWeave.viewAll')}
          </Button>
        </div>
        {skillsLoading ? (
          <p className="text-muted-foreground">{t('agents.loading')}</p>
        ) : skills.length === 0 ? (
          <EmptyState title={t('myWeave.skillsEmpty')} />
        ) : (
          <div className="space-y-3">
            {skills.slice(0, 5).map((skill) => (
              <Card
                key={skill.id}
                className="p-4 cursor-pointer hover:bg-muted/30 transition-colors"
                onClick={() => navigate({ to: `/space/${skill.namespace}/${skill.slug}` })}
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="font-semibold">{skill.displayName}</div>
                    {skill.summary && (
                      <div className="mt-1 text-sm text-muted-foreground">{skill.summary}</div>
                    )}
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded border bg-secondary/40 text-muted-foreground">
                    @{skill.namespace}
                  </span>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      <section>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold tracking-tight">
            {t('myWeave.agentsHeading')}
          </h2>
          <Button variant="outline" size="sm" onClick={() => navigate({ to: '/dashboard/my-agents' })}>
            {t('myWeave.viewAll')}
          </Button>
        </div>
        {agentsLoading ? (
          <p className="text-muted-foreground">{t('agents.loading')}</p>
        ) : myAgents.length === 0 ? (
          <EmptyState title={t('myWeave.agentsEmpty')} />
        ) : (
          <div className="space-y-3">
            {myAgents.slice(0, 5).map((agent) => (
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
                      <div className="mt-1 text-sm text-muted-foreground">{agent.description}</div>
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
      </section>
    </div>
  )
}
