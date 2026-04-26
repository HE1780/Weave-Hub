import { useTranslation } from 'react-i18next'
import { useAgentDetail } from '@/features/agent/use-agent-detail'
import { WorkflowSteps } from '@/features/agent/workflow-steps'

interface AgentDetailPageProps {
  /**
   * Optional override for tests. In normal use this is read from route params via the
   * route component wrapper in router.tsx.
   */
  name?: string
}

/**
 * Detail page for a single agent. Renders metadata + soul + workflow.
 */
export function AgentDetailPage({ name }: AgentDetailPageProps) {
  const { t } = useTranslation()
  const resolvedName = name ?? ''
  const { data: agent, isLoading, isError } = useAgentDetail(resolvedName)

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <p className="text-muted-foreground">{t('agents.loading')}</p>
      </div>
    )
  }

  if (isError || !agent) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <p className="text-destructive">{t('agents.loadError')}</p>
      </div>
    )
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-8">
      <header>
        <h1 className="text-3xl font-bold tracking-tight">{agent.name}</h1>
        {agent.version && (
          <p className="text-sm font-mono text-muted-foreground mt-1">v{agent.version}</p>
        )}
        <p className="text-lg text-muted-foreground mt-3">{agent.description}</p>
      </header>

      <section>
        <h2 className="text-xl font-semibold mb-3">{t('agents.detail.soulHeading')}</h2>
        {agent.soul ? (
          <pre className="whitespace-pre-wrap text-sm bg-muted/40 rounded-lg p-4 leading-relaxed">
            {agent.soul}
          </pre>
        ) : (
          <p className="text-sm text-muted-foreground italic">{t('agents.detail.noSoul')}</p>
        )}
      </section>

      <section>
        <h2 className="text-xl font-semibold mb-3">{t('agents.detail.workflowHeading')}</h2>
        <WorkflowSteps workflow={agent.workflow} />
      </section>

      {agent.body && (
        <section>
          <pre className="whitespace-pre-wrap text-sm leading-relaxed">{agent.body}</pre>
        </section>
      )}
    </div>
  )
}
