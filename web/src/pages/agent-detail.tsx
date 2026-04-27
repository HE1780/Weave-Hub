import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Clock, Globe, Lock, Star, Users } from 'lucide-react'
import { useAgentDetail } from '@/features/agent/use-agent-detail'
import { useArchiveAgent } from '@/features/agent/use-archive-agent'
import { useUnarchiveAgent } from '@/features/agent/use-unarchive-agent'
import { useDeleteAgent } from '@/features/agent/use-delete-agent'
import { useWithdrawAgentReview } from '@/features/agent/use-withdraw-agent-review'
import { useRereleaseAgentVersion } from '@/features/agent/use-rerelease-agent-version'
import { useDeleteAgentVersion } from '@/features/agent/use-delete-agent-version'
import { AgentStarButton } from '@/features/agent/social/agent-star-button'
import { AgentRatingInput } from '@/features/agent/social/agent-rating-input'
import { WorkflowSteps } from '@/features/agent/workflow-steps'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { toast } from '@/shared/lib/toast'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { cn } from '@/shared/lib/utils'

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
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { data: agent, isLoading, isError, error } = useAgentDetail(namespace ?? '', slug ?? '')
  const archiveMutation = useArchiveAgent()
  const unarchiveMutation = useUnarchiveAgent()
  const deleteMutation = useDeleteAgent()
  const withdrawMutation = useWithdrawAgentReview()
  const rereleaseMutation = useRereleaseAgentVersion()
  const deleteVersionMutation = useDeleteAgentVersion()
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false)
  const [unarchiveConfirmOpen, setUnarchiveConfirmOpen] = useState(false)
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [deleteInputOpen, setDeleteInputOpen] = useState(false)
  const [deleteSlugInput, setDeleteSlugInput] = useState('')
  const [withdrawTarget, setWithdrawTarget] = useState<string | null>(null)
  const [rereleaseTarget, setRereleaseTarget] = useState<string | null>(null)
  const [rereleaseInput, setRereleaseInput] = useState('')
  const [deleteVersionTarget, setDeleteVersionTarget] = useState<string | null>(null)
  const [activeDoc, setActiveDoc] = useState<'agent' | 'soul'>('agent')
  const handleRequireLogin = () => {
    navigate({
      to: '/login',
      search: { returnTo: `${window.location.pathname}${window.location.search}` },
    })
  }
  const handleBack = () => {
    navigate({ to: '/agents' })
  }

  if (isLoading) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-10 w-64 animate-shimmer rounded-lg" />
        <div className="h-5 w-96 animate-shimmer rounded-md" />
        <div className="h-64 animate-shimmer rounded-xl" />
      </div>
    )
  }

  if (isError || !agent) {
    const errorMessage = error instanceof Error ? error.message : ''
    const isNoPublishedVersion = errorMessage.includes('no published version')
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">
          {isNoPublishedVersion ? t('agents.noPublishedVersion') : t('agents.loadError')}
        </h2>
      </div>
    )
  }

  // Backend computes canManageLifecycle as (owner OR namespace ADMIN/OWNER).
  // Anonymous viewers see false. Falling back to false on missing field keeps
  // older API responses safe.
  const canManageLifecycle = agent.canManageLifecycle ?? false
  const targetNamespace = agent.namespace ?? namespace ?? ''
  const targetSlug = agent.slug ?? slug ?? ''
  const targetName = agent.displayName ?? agent.name

  /**
   * Maps backend status into translated labels used by the versions tab.
   */
  const resolveVersionStatusLabel = (status?: string) => {
    if (!status) return ''
    const statusMap: Record<string, string> = {
      DRAFT: t('skillDetail.versionStatusDraft'),
      PENDING_REVIEW: t('skillDetail.versionStatusPendingReview'),
      PUBLISHED: t('skillDetail.versionStatusPublished'),
      REJECTED: t('skillDetail.versionStatusRejected'),
      ARCHIVED: t('skillDetail.statusArchived'),
    }
    return statusMap[status] ?? status
  }

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

  const handleOpenDeleteInput = () => {
    setDeleteConfirmOpen(false)
    setDeleteSlugInput('')
    setDeleteInputOpen(true)
  }

  const handleDeleteAgent = async () => {
    if (deleteSlugInput !== targetSlug) return
    try {
      await deleteMutation.mutateAsync({ namespace: targetNamespace, slug: targetSlug })
      toast.success(
        t('agents.lifecycle.deleteSuccessTitle'),
        t('agents.lifecycle.deleteSuccessDescription', { agent: targetName }),
      )
      setDeleteInputOpen(false)
      navigate({ to: '/agents' })
    } catch (err) {
      toast.error(
        t('agents.lifecycle.deleteErrorTitle'),
        err instanceof Error ? err.message : '',
      )
    }
  }

  const handleWithdraw = async () => {
    if (!withdrawTarget) return
    try {
      await withdrawMutation.mutateAsync({
        namespace: targetNamespace,
        slug: targetSlug,
        version: withdrawTarget,
      })
      toast.success(
        t('agents.lifecycle.withdrawSuccessTitle'),
        t('agents.lifecycle.withdrawSuccessDescription', { version: withdrawTarget }),
      )
      setWithdrawTarget(null)
    } catch (err) {
      toast.error(
        t('agents.lifecycle.withdrawErrorTitle'),
        err instanceof Error ? err.message : '',
      )
    }
  }

  const handleOpenRerelease = (version: string) => {
    setRereleaseTarget(version)
    setRereleaseInput('')
  }

  const handleRerelease = async () => {
    if (!rereleaseTarget || !rereleaseInput.trim()) return
    try {
      await rereleaseMutation.mutateAsync({
        namespace: targetNamespace,
        slug: targetSlug,
        version: rereleaseTarget,
        targetVersion: rereleaseInput.trim(),
      })
      toast.success(
        t('agents.lifecycle.rereleaseSuccessTitle'),
        t('agents.lifecycle.rereleaseSuccessDescription', {
          source: rereleaseTarget,
          target: rereleaseInput.trim(),
        }),
      )
      setRereleaseTarget(null)
      setRereleaseInput('')
    } catch (err) {
      toast.error(
        t('agents.lifecycle.rereleaseErrorTitle'),
        err instanceof Error ? err.message : '',
      )
    }
  }

  const handleDeleteVersion = async () => {
    if (!deleteVersionTarget) return
    try {
      await deleteVersionMutation.mutateAsync({
        namespace: targetNamespace,
        slug: targetSlug,
        version: deleteVersionTarget,
      })
      toast.success(
        t('agents.lifecycle.deleteVersionSuccessTitle'),
        t('agents.lifecycle.deleteVersionSuccessDescription', { version: deleteVersionTarget }),
      )
      setDeleteVersionTarget(null)
    } catch (err) {
      toast.error(
        t('agents.lifecycle.deleteVersionErrorTitle'),
        err instanceof Error ? err.message : '',
      )
    }
  }

  return (
    <div className="max-w-6xl mx-auto flex flex-col lg:flex-row gap-8 animate-fade-up">
      <div className="flex-1 min-w-0 space-y-8">
        <header className="space-y-4">
          <Button
            variant="ghost"
            size="sm"
            className="gap-2 px-0 text-muted-foreground hover:text-foreground"
            onClick={handleBack}
          >
            <ArrowLeft className="h-4 w-4" />
            {t('skillDetail.back')}
          </Button>
          <div className="flex items-center gap-3 mb-1">
            <NamespaceBadge type="GLOBAL" name={targetNamespace || 'global'} />
            {agent.status && (
              <span className={cn(
                'badge-soft',
                agent.status === 'ACTIVE' && 'badge-soft-green',
                agent.status === 'ARCHIVED' && 'bg-secondary text-muted-foreground',
              )}>
                {agent.status === 'ARCHIVED' ? t('agents.lifecycle.statusArchived') : t('agents.lifecycle.statusActive')}
              </span>
            )}
            {agent.visibility && (
              <span className={cn(
                'badge-soft inline-flex items-center gap-1',
                agent.visibility === 'PUBLIC' && 'badge-soft-green',
                agent.visibility === 'PRIVATE' && 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
                agent.visibility === 'NAMESPACE_ONLY' && 'badge-soft-blue',
              )}>
                {agent.visibility === 'PUBLIC' && <Globe className="h-3 w-3" />}
                {agent.visibility === 'PRIVATE' && <Lock className="h-3 w-3" />}
                {agent.visibility === 'NAMESPACE_ONLY' && <Users className="h-3 w-3" />}
                {agent.visibility === 'PUBLIC' && t('publish.visibilityOptions.public')}
                {agent.visibility === 'PRIVATE' && t('publish.visibilityOptions.private')}
                {agent.visibility === 'NAMESPACE_ONLY' && t('publish.visibilityOptions.namespaceOnly')}
              </span>
            )}
          </div>
          <h1 className="text-balance text-4xl font-bold font-heading text-foreground">{agent.name}</h1>
          <p className="text-lg text-muted-foreground leading-relaxed">{agent.description}</p>
        </header>

        <Tabs defaultValue="overview">
          <TabsList>
            <TabsTrigger value="overview">{t('agents.detail.tabOverview')}</TabsTrigger>
            <TabsTrigger value="workflow">{t('agents.detail.tabWorkflow')}</TabsTrigger>
            <TabsTrigger value="versions">{t('agents.detail.tabVersions')}</TabsTrigger>
          </TabsList>

          <TabsContent value="overview" className="mt-6">
            <Card className="p-8 space-y-4">
              <div className="inline-flex items-center rounded-full border border-border/60 bg-secondary/20 p-1">
                <button
                  type="button"
                  onClick={() => setActiveDoc('agent')}
                  className={cn(
                    'rounded-full px-3 py-1.5 text-xs font-semibold transition-colors',
                    activeDoc === 'agent'
                      ? 'bg-primary text-primary-foreground'
                      : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {t('agents.detail.docTagAgent')}
                </button>
                <button
                  type="button"
                  onClick={() => setActiveDoc('soul')}
                  className={cn(
                    'rounded-full px-3 py-1.5 text-xs font-semibold transition-colors',
                    activeDoc === 'soul'
                      ? 'bg-primary text-primary-foreground'
                      : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {t('agents.detail.docTagSoul')}
                </button>
              </div>
              {(activeDoc === 'agent' ? agent.body : agent.soul) ? (
                <MarkdownRenderer content={activeDoc === 'agent' ? (agent.body ?? '') : (agent.soul ?? '')} />
              ) : (
                <p className="text-sm text-muted-foreground italic">{t('agents.detail.docEmpty')}</p>
              )}
            </Card>
          </TabsContent>

          <TabsContent value="workflow" className="mt-6">
            <Card className="p-6 space-y-4">
              <div className="text-sm font-semibold text-foreground">{t('agents.detail.workflowHeading')}</div>
              <WorkflowSteps workflow={agent.workflow} />
            </Card>
          </TabsContent>

          <TabsContent value="versions" className="mt-6">
            <Card className="p-6">
              {agent.versions && agent.versions.length > 0 ? (
                <div className="space-y-0 divide-y divide-border/40">
                  {agent.versions.map((version) => (
                    <div key={version.id} className="py-5 first:pt-0 last:pb-0">
                      <div className="flex items-start justify-between gap-4 mb-2">
                        <span className="font-semibold font-heading text-foreground flex items-center gap-2 flex-wrap min-w-0">
                          <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-sm font-mono">
                            v{version.version}
                          </span>
                          <span className="rounded-full border border-border/60 bg-secondary/40 px-2.5 py-0.5 text-xs text-muted-foreground">
                            {resolveVersionStatusLabel(version.status)}
                          </span>
                        </span>
                        <div className="flex items-center gap-3 flex-shrink-0 text-sm text-muted-foreground">
                          <Clock className="h-4 w-4" />
                          <span>{formatLocalDateTime(version.publishedAt ?? version.submittedAt, i18n.language)}</span>
                        </div>
                      </div>
                      <div className="flex items-center justify-between gap-3 mt-2 flex-wrap">
                        <div className="text-xs text-muted-foreground">
                          {(version.packageSizeBytes / 1024).toFixed(1)} KB
                        </div>
                        {canManageLifecycle && (
                          <div className="flex items-center gap-2">
                            {version.status === 'PENDING_REVIEW' && (
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => setWithdrawTarget(version.version)}
                                disabled={withdrawMutation.isPending}
                              >
                                {t('agents.lifecycle.withdraw')}
                              </Button>
                            )}
                            {version.status === 'PUBLISHED' && (
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => handleOpenRerelease(version.version)}
                                disabled={rereleaseMutation.isPending}
                              >
                                {t('agents.lifecycle.rerelease')}
                              </Button>
                            )}
                            {(version.status === 'DRAFT' ||
                              version.status === 'REJECTED' ||
                              version.status === 'ARCHIVED') && (
                              <Button
                                size="sm"
                                variant="destructive"
                                onClick={() => setDeleteVersionTarget(version.version)}
                                disabled={deleteVersionMutation.isPending}
                              >
                                {t('agents.lifecycle.deleteVersion')}
                              </Button>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-8 text-muted-foreground text-center">{t('skillDetail.noVersions')}</div>
              )}
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      <aside className="w-full lg:w-80 flex-shrink-0 space-y-5">
        <Card className="p-5 space-y-5">
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.version')}</div>
            <div className="max-w-[11rem] break-all text-right font-mono font-semibold leading-snug text-foreground">
              {agent.version ? `v${agent.version}` : '—'}
            </div>
          </div>
          <div className="h-px bg-border/40" />
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.downloads')}</div>
            <div className="font-semibold text-foreground">—</div>
          </div>
          <div className="h-px bg-border/40" />
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.rating')}</div>
            <div className="inline-flex items-center gap-1 font-semibold text-foreground">
              <Star className="w-4 h-4 fill-yellow-400 text-yellow-400" />
              {typeof agent.ratingAvg === 'number' && (agent.ratingCount ?? 0) > 0
                ? `${agent.ratingAvg.toFixed(1)} / 5`
                : t('skillDetail.ratingNone')}
            </div>
          </div>
          <div className="h-px bg-border/40" />
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.namespaceLabel')}</div>
            <NamespaceBadge type="GLOBAL" name={targetNamespace || 'global'} />
          </div>
          <div className="h-px bg-border/40" />
          <div className="text-sm text-muted-foreground">
            {t('agents.detail.starCount', { count: agent.starCount ?? 0 })}
          </div>
        </Card>

        {agent.agentId && targetNamespace && targetSlug && (
          <Card className="p-5 space-y-3">
            <AgentStarButton
              namespace={targetNamespace}
              slug={targetSlug}
              starCount={agent.starCount ?? 0}
              onRequireLogin={handleRequireLogin}
            />
            <AgentRatingInput
              namespace={targetNamespace}
              slug={targetSlug}
              onRequireLogin={handleRequireLogin}
            />
          </Card>
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
            <div className="flex flex-col gap-3">
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
              <Button
                variant="destructive"
                onClick={() => setDeleteConfirmOpen(true)}
                disabled={deleteMutation.isPending}
              >
                {deleteMutation.isPending
                  ? t('agents.lifecycle.processing')
                  : t('agents.lifecycle.delete')}
              </Button>
            </div>
          </Card>
        )}
      </aside>

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

      <ConfirmDialog
        open={deleteConfirmOpen}
        onOpenChange={setDeleteConfirmOpen}
        title={t('agents.lifecycle.deleteConfirmTitle')}
        description={t('agents.lifecycle.deleteConfirmDescription', { agent: targetName })}
        confirmText={t('agents.lifecycle.deleteContinue')}
        variant="destructive"
        onConfirm={handleOpenDeleteInput}
      />

      <Dialog
        open={deleteInputOpen}
        onOpenChange={(open) => {
          setDeleteInputOpen(open)
          if (!open) setDeleteSlugInput('')
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('agents.lifecycle.deleteInputTitle')}</DialogTitle>
            <DialogDescription>
              {t('agents.lifecycle.deleteInputDescription', { slug: targetSlug })}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-sm text-muted-foreground">
              {t('agents.lifecycle.deleteWarning')}
            </div>
            <Input
              value={deleteSlugInput}
              onChange={(event) => setDeleteSlugInput(event.target.value)}
              placeholder={t('agents.lifecycle.deleteInputPlaceholder')}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteInputOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteAgent}
              disabled={deleteSlugInput !== targetSlug || deleteMutation.isPending}
            >
              {deleteMutation.isPending
                ? t('agents.lifecycle.processing')
                : t('agents.lifecycle.deleteFinal')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!withdrawTarget}
        onOpenChange={(open) => {
          if (!open) setWithdrawTarget(null)
        }}
        title={t('agents.lifecycle.withdrawConfirmTitle')}
        description={
          withdrawTarget
            ? t('agents.lifecycle.withdrawConfirmDescription', { version: withdrawTarget })
            : ''
        }
        confirmText={t('agents.lifecycle.withdraw')}
        onConfirm={handleWithdraw}
      />

      <ConfirmDialog
        open={!!deleteVersionTarget}
        onOpenChange={(open) => {
          if (!open) setDeleteVersionTarget(null)
        }}
        title={t('agents.lifecycle.deleteVersionConfirmTitle')}
        description={
          deleteVersionTarget
            ? t('agents.lifecycle.deleteVersionConfirmDescription', { version: deleteVersionTarget })
            : ''
        }
        confirmText={t('agents.lifecycle.deleteVersion')}
        variant="destructive"
        onConfirm={handleDeleteVersion}
      />

      <Dialog
        open={!!rereleaseTarget}
        onOpenChange={(open) => {
          if (!open) {
            setRereleaseTarget(null)
            setRereleaseInput('')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('agents.lifecycle.rereleaseTitle')}</DialogTitle>
            <DialogDescription>
              {rereleaseTarget
                ? t('agents.lifecycle.rereleaseDescription', { source: rereleaseTarget })
                : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <Input
              value={rereleaseInput}
              onChange={(event) => setRereleaseInput(event.target.value)}
              placeholder={t('agents.lifecycle.rereleasePlaceholder')}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setRereleaseTarget(null)
                setRereleaseInput('')
              }}
            >
              {t('dialog.cancel')}
            </Button>
            <Button
              onClick={handleRerelease}
              disabled={
                !rereleaseInput.trim() ||
                rereleaseInput.trim() === rereleaseTarget ||
                rereleaseMutation.isPending
              }
            >
              {rereleaseMutation.isPending
                ? t('agents.lifecycle.processing')
                : t('agents.lifecycle.rerelease')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
