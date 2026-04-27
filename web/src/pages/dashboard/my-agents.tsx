import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Upload } from 'lucide-react'
import { agentsApi, type AgentDto } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { useArchiveAgent } from '@/features/agent/use-archive-agent'
import { useUnarchiveAgent } from '@/features/agent/use-unarchive-agent'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'
import { toast } from '@/shared/lib/toast'

interface LifecycleTarget {
  namespace: string
  slug: string
  name: string
}

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
  const [archiveTarget, setArchiveTarget] = useState<LifecycleTarget | null>(null)
  const [unarchiveTarget, setUnarchiveTarget] = useState<LifecycleTarget | null>(null)

  const archiveMutation = useArchiveAgent()
  const unarchiveMutation = useUnarchiveAgent()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['agents', 'my', user?.userId],
    queryFn: async () => {
      const page = await agentsApi.list({ page: 0, size: 200 })
      return page.items
    },
    enabled: !!user?.userId,
  })

  const myAgents = (data ?? []).filter((a: AgentDto) => a.ownerId === user?.userId)

  const handleArchive = async () => {
    if (!archiveTarget) return
    try {
      await archiveMutation.mutateAsync({
        namespace: archiveTarget.namespace,
        slug: archiveTarget.slug,
      })
      toast.success(
        t('agents.lifecycle.archiveSuccessTitle'),
        t('agents.lifecycle.archiveSuccessDescription', { agent: archiveTarget.name }),
      )
      setArchiveTarget(null)
    } catch (error) {
      toast.error(
        t('agents.lifecycle.archiveErrorTitle'),
        error instanceof Error ? error.message : '',
      )
      throw error
    }
  }

  const handleUnarchive = async () => {
    if (!unarchiveTarget) return
    try {
      await unarchiveMutation.mutateAsync({
        namespace: unarchiveTarget.namespace,
        slug: unarchiveTarget.slug,
      })
      toast.success(
        t('agents.lifecycle.unarchiveSuccessTitle'),
        t('agents.lifecycle.unarchiveSuccessDescription', { agent: unarchiveTarget.name }),
      )
      setUnarchiveTarget(null)
    } catch (error) {
      toast.error(
        t('agents.lifecycle.unarchiveErrorTitle'),
        error instanceof Error ? error.message : '',
      )
      throw error
    }
  }

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
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="text-xs px-2 py-0.5 rounded border bg-secondary/40 text-muted-foreground">
                      {agent.visibility}
                    </span>
                    <span
                      className={
                        agent.status === 'ARCHIVED'
                          ? 'text-xs px-2 py-0.5 rounded border bg-muted text-muted-foreground'
                          : 'text-xs px-2 py-0.5 rounded border border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                      }
                    >
                      {agent.status === 'ARCHIVED'
                        ? t('agents.lifecycle.statusArchived')
                        : t('agents.lifecycle.statusActive')}
                    </span>
                    {agent.status === 'ARCHIVED' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={(event) => {
                          event.stopPropagation()
                          setUnarchiveTarget({
                            namespace: agent.namespace,
                            slug: agent.slug,
                            name: agent.displayName,
                          })
                        }}
                      >
                        {t('agents.lifecycle.unarchive')}
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={(event) => {
                          event.stopPropagation()
                          setArchiveTarget({
                            namespace: agent.namespace,
                            slug: agent.slug,
                            name: agent.displayName,
                          })
                        }}
                      >
                        {t('agents.lifecycle.archive')}
                      </Button>
                    )}
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={!!archiveTarget}
        onOpenChange={(open) => {
          if (!open) setArchiveTarget(null)
        }}
        title={t('agents.lifecycle.archiveConfirmTitle')}
        description={
          archiveTarget
            ? t('agents.lifecycle.archiveConfirmDescription', { agent: archiveTarget.name })
            : ''
        }
        confirmText={t('agents.lifecycle.archive')}
        onConfirm={handleArchive}
      />

      <ConfirmDialog
        open={!!unarchiveTarget}
        onOpenChange={(open) => {
          if (!open) setUnarchiveTarget(null)
        }}
        title={t('agents.lifecycle.unarchiveConfirmTitle')}
        description={
          unarchiveTarget
            ? t('agents.lifecycle.unarchiveConfirmDescription', { agent: unarchiveTarget.name })
            : ''
        }
        confirmText={t('agents.lifecycle.unarchive')}
        onConfirm={handleUnarchive}
      />
    </div>
  )
}
