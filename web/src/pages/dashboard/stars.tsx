import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SkillCard } from '@/features/skill/skill-card'
import { AgentCard } from '@/features/agent/agent-card'
import { Pagination } from '@/shared/components/pagination'
import { useMyStarsPage, useMyAgentStarsPage } from '@/shared/hooks/use-user-queries'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'

const PAGE_SIZE = 12

export function MyStarsPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('stars.title')} subtitle={t('stars.subtitle')} />
      <Tabs defaultValue="SKILLS">
        <TabsList>
          <TabsTrigger value="SKILLS">{t('stars.tabSkills')}</TabsTrigger>
          <TabsTrigger value="AGENTS">{t('stars.tabAgents')}</TabsTrigger>
        </TabsList>
        <TabsContent value="SKILLS" className="mt-6">
          <SkillsStarsPanel />
        </TabsContent>
        <TabsContent value="AGENTS" className="mt-6">
          <AgentsStarsPanel />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function SkillsStarsPanel() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMyStarsPage({ page, size: PAGE_SIZE })
  const skills = data?.items ?? []
  const totalPages = data ? Math.max(Math.ceil(data.total / data.size), 1) : 1

  if (isLoading) {
    return (
      <div className="space-y-4 animate-fade-up">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-32 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  if (!skills || skills.length === 0) {
    return <Card className="p-12 text-center text-muted-foreground">{t('stars.empty')}</Card>
  }

  return (
    <>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {skills.map((skill) => (
          <SkillCard
            key={skill.id}
            skill={skill}
            onClick={() => navigate({ to: `/space/${skill.namespace}/${encodeURIComponent(skill.slug)}` })}
          />
        ))}
      </div>
      {data && data.total > PAGE_SIZE ? (
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      ) : null}
    </>
  )
}

function AgentsStarsPanel() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMyAgentStarsPage({ page, size: PAGE_SIZE })
  const agents = data?.items ?? []
  const totalPages = data ? Math.max(Math.ceil(data.total / data.size), 1) : 1

  if (isLoading) {
    return (
      <div className="space-y-4 animate-fade-up">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-32 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  if (!agents || agents.length === 0) {
    return <Card className="p-12 text-center text-muted-foreground">{t('stars.emptyAgents')}</Card>
  }

  return (
    <>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {agents.map((agent) => (
          <AgentCard key={agent.id ?? agent.name} agent={agent} />
        ))}
      </div>
      {data && data.total > PAGE_SIZE ? (
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      ) : null}
    </>
  )
}
