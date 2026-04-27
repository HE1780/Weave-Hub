import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAgentDetail } from '@/features/agent/use-agent-detail'
import { useArchiveAgent } from '@/features/agent/use-archive-agent'
import { useUnarchiveAgent } from '@/features/agent/use-unarchive-agent'
import { useAuth } from '@/features/auth/use-auth'
import { WorkflowSteps } from '@/features/agent/workflow-steps'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { toast } from '@/shared/lib/toast'

interface AgentDetailPageProps {
  /**
   * Namespace and slug come from the route params (or are passed directly in tests).
   */
  namespace?: string
  slug?: string
}

/**
 * Detail page for a single agent. Renders metadata + soul + workflow.
 */
export function AgentDetailPage({ namespace, slug }: AgentDetailPageProps) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const { data: agent, isLoading, isError, error } = useAgentDetail(namespace ?? '', slug ?? '')
  const archiveMutation = useArchiveAgent()
  const unarchiveMutation = useUnarchiveAgent()
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false)
  const [unarchiveConfirmOpen, setUnarchiveConfirmOpen] = useState(false)

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <p className="text-muted-foreground">{t('agents.loading')}</p>
      </div>
    )
  }

  if (isError || !agent) {
    const errorMessage = error instanceof Error ? error.message : ''
    const isNoPublishedVersion = errorMessage.includes('no published version')
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <p className="text-destructive">
          {isNoPublishedVersion ? t('agents.noPublishedVersion') : t('agents.loadError')}
        </p>
      </div>
    )
  }

  const canManageLifecycle = Boolean(
    agent.ownerId && user?.userId && agent.ownerId === user.userId,
  )
  const targetNamespace = agent.namespace ?? namespace ?? ''
  const targetSlug = agent.slug ?? slug ?? ''
  const targetName = agent.displayName ?? agent.name

  const handleArchive = async () => {
    try {
      await archiveMutation.mutateAsync({ namespace: targetNamespace, slug: targetSlug })
      toast.success(
        t('agents.lifecycle.archiveSuccessTitle'),
        t('agents.lifecycle.archiveSuccessDescription', { agent: targetName }),
      )
      setArchiveConfirmOpen(false)
    } catch (err) {
      toast.error(
        t('agents.lifecycle.archiveErrorTitle'),
        err instanceof Error ? err.message : '',
      )
      throw err
    }
  }

  const handleUnarchive = async () => {
    try {
      await unarchiveMutation.mutateAsync({ namespace: targetNamespace, slug: targetSlug })
      toast.success(
        t('agents.lifecycle.unarchiveSuccessTitle'),
        t('agents.lifecycle.unarchiveSuccessDescription', { agent: targetName }),
      )
      setUnarchiveConfirmOpen(false)
    } catch (err) {
      toast.error(
        t('agents.lifecycle.unarchiveErrorTitle'),
        err instanceof Error ? err.message : '',
      )
      throw err
    }
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-8">
      <header>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">{agent.name}</h1>
            {agent.version && (
              <p className="text-sm font-mono text-muted-foreground mt-1">v{agent.version}</p>
            )}
          </div>
          {agent.status === 'ARCHIVED' && (
            <span className="text-xs px-2 py-0.5 rounded border bg-muted text-muted-foreground">
              {t('agents.lifecycle.statusArchived')}
            </span>
          )}
        </div>
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

      {canManageLifecycle && (
        <Card className="p-5 space-y-3">
          <h2 className="text-sm font-semibold text-foreground">
            {t('agents.lifecycle.manageHeading')}
          </h2>
          <p className="text-sm text-muted-foreground">
            {agent.status === 'ARCHIVED'
              ? t('agents.lifecycle.unarchiveDescription')
              : t('agents.lifecycle.archiveDescription')}
          </p>
          <div>
            {agent.status === 'ARCHIVED' ? (
              <Button
                variant="outline"
                onClick={() => setUnarchiveConfirmOpen(true)}
                disabled={unarchiveMutation.isPending}
              >
                {unarchiveMutation.isPending
                  ? t('agents.lifecycle.processing')
                  : t('agents.lifecycle.unarchive')}
              </Button>
            ) : (
              <Button
                variant="outline"
                onClick={() => setArchiveConfirmOpen(true)}
                disabled={archiveMutation.isPending}
              >
                {archiveMutation.isPending
                  ? t('agents.lifecycle.processing')
                  : t('agents.lifecycle.archive')}
              </Button>
            )}
          </div>
        </Card>
      )}

      <ConfirmDialog
        open={archiveConfirmOpen}
        onOpenChange={setArchiveConfirmOpen}
        title={t('agents.lifecycle.archiveConfirmTitle')}
        description={t('agents.lifecycle.archiveConfirmDescription', { agent: targetName })}
        confirmText={t('agents.lifecycle.archive')}
        onConfirm={handleArchive}
      />

      <ConfirmDialog
        open={unarchiveConfirmOpen}
        onOpenChange={setUnarchiveConfirmOpen}
        title={t('agents.lifecycle.unarchiveConfirmTitle')}
        description={t('agents.lifecycle.unarchiveConfirmDescription', { agent: targetName })}
        confirmText={t('agents.lifecycle.unarchive')}
        onConfirm={handleUnarchive}
      />
    </div>
  )
}
