import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
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
  const [activeTab, setActiveTab] = useState<'skill' | 'agent'>('skill')

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
  const viewAllTo = activeTab === 'skill' ? '/dashboard/skills' : '/dashboard/my-agents'

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-12">
      <DashboardPageHeader
        title={t('myWeave.title')}
        subtitle={t('myWeave.subtitle')}
      />

      <section>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-4">
          <div className="inline-flex w-full sm:w-[380px] items-center justify-between rounded-full border border-brand-200 bg-brand-50 p-1 gap-1 shadow-sm">
            <button
              type="button"
              onClick={() => setActiveTab('skill')}
              className={
                activeTab === 'skill'
                  ? 'flex-1 text-center rounded-full px-5 py-2 text-sm font-extrabold tracking-wide bg-primary text-primary-foreground shadow-sm ring-1 ring-primary/30 transition-colors'
                  : 'flex-1 text-center rounded-full px-5 py-2 text-sm font-extrabold tracking-wide bg-white/90 text-slate-600 hover:bg-white transition-colors'
              }
            >
              SKILL
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('agent')}
              className={
                activeTab === 'agent'
                  ? 'flex-1 text-center rounded-full px-5 py-2 text-sm font-extrabold tracking-wide bg-primary text-primary-foreground shadow-sm ring-1 ring-primary/30 transition-colors'
                  : 'flex-1 text-center rounded-full px-5 py-2 text-sm font-extrabold tracking-wide bg-white/90 text-slate-600 hover:bg-white transition-colors'
              }
            >
              智能体
            </button>
          </div>
          <Button
            variant="outline"
            className="h-11 rounded-full px-5 text-sm font-semibold self-end sm:self-auto"
            onClick={() => navigate({ to: viewAllTo })}
          >
            {t('myWeave.viewAll')}
          </Button>
        </div>

        {activeTab === 'skill' ? (
          skillsLoading ? (
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
            )
        ) : (
          agentsLoading ? (
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
            )
        )}
      </section>
    </div>
  )
}
