import { useMemo, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, ChevronDown, Clock, Download, Folder, Globe, Lock, Star, Terminal, Users } from 'lucide-react'
import { useAgentDetail } from '@/features/agent/use-agent-detail'
import { useArchiveAgent } from '@/features/agent/use-archive-agent'
import { useUnarchiveAgent } from '@/features/agent/use-unarchive-agent'
import { useReportAgent } from '@/features/agent/use-report-agent'
import { useDeleteAgent } from '@/features/agent/use-delete-agent'
import { useWithdrawAgentReview } from '@/features/agent/use-withdraw-agent-review'
import { useRereleaseAgentVersion } from '@/features/agent/use-rerelease-agent-version'
import { useDeleteAgentVersion } from '@/features/agent/use-delete-agent-version'
import { AgentStarButton } from '@/features/agent/social/agent-star-button'
import { AgentRatingInput } from '@/features/agent/social/agent-rating-input'
import { AgentVersionCommentsSection } from '@/features/agent/comments'
import { AgentLabelPanel } from '@/features/agent/agent-label-panel'
import { useAuth } from '@/features/auth/use-auth'
import { WorkflowSteps } from '@/features/agent/workflow-steps'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { InstallCommand, getBaseUrl } from '@/features/skill/install-command'
import { FileTree } from '@/features/skill/file-tree'
import { FilePreviewDialog } from '@/features/skill/file-preview-dialog'
import type { FileTreeNode } from '@/features/skill/file-tree-builder'
import type { SkillFile } from '@/api/types'
import { buildApiUrl, WEB_API_PREFIX } from '@/api/client'
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
import { Textarea } from '@/shared/ui/textarea'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
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
 * Builds a minimal virtual package file list from inline agent version content.
 * This keeps the file-tree UX aligned with skill detail while using real data.
 */
function buildInlineAgentFiles(agent: {
  body?: string
  soul?: string
  workflowYaml?: string
}): SkillFile[] {
  const files: SkillFile[] = []
  const pushFile = (filePath: string, content: string, contentType: string) => {
    files.push({
      id: files.length + 1,
      filePath,
      fileSize: new Blob([content]).size,
      contentType,
      sha256: `inline-${filePath}`,
    })
  }

  if (agent.body) {
    pushFile('agent.md', agent.body, 'text/markdown')
  }
  if (agent.soul) {
    pushFile('soul.md', agent.soul, 'text/markdown')
  }
  if (agent.workflowYaml) {
    pushFile('workflow.yaml', agent.workflowYaml, 'application/x-yaml')
  }
  return files
}

/**
 * Triggers browser download for inline agent file content.
 */
function triggerBrowserDownload(fileName: string, content: string, contentType: string) {
  const blob = new Blob([content], { type: contentType })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

/**
 * Triggers browser download for a backend URL endpoint.
 */
function triggerBrowserUrlDownload(url: string) {
  const anchor = document.createElement('a')
  anchor.href = url
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
}

/**
 * Detail page for a single agent. Renders metadata + soul + workflow.
 */
export function AgentDetailPage({ namespace, slug }: AgentDetailPageProps) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { user, hasRole } = useAuth()
  const viewerUserId = user?.userId ?? null
  const { data: agent, isLoading, isError, error } = useAgentDetail(namespace ?? '', slug ?? '')
  const archiveMutation = useArchiveAgent()
  const unarchiveMutation = useUnarchiveAgent()
  const reportMutation = useReportAgent(namespace ?? '', slug ?? '')
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
  const [reportDialogOpen, setReportDialogOpen] = useState(false)
  const [reportReason, setReportReason] = useState('')
  const [reportDetails, setReportDetails] = useState('')
  const [hasReported, setHasReported] = useState(false)
  const [activeDoc, setActiveDoc] = useState<'agent' | 'soul'>('agent')
  const [selectedFileNode, setSelectedFileNode] = useState<FileTreeNode | null>(null)
  const [filePreviewOpen, setFilePreviewOpen] = useState(false)
  const [fileBrowserOpen, setFileBrowserOpen] = useState(true)
  const [shareCopied, copyShareText] = useCopyToClipboard()
  const inlineFiles = useMemo(
    () =>
      buildInlineAgentFiles({
        body: agent?.body,
        soul: agent?.soul,
        workflowYaml: agent?.workflowYaml,
      }),
    [agent?.body, agent?.soul, agent?.workflowYaml],
  )
  const inlineContentByPath = useMemo<Record<string, string>>(
    () => ({
      ...(agent?.body ? { 'agent.md': agent.body } : {}),
      ...(agent?.soul ? { 'soul.md': agent.soul } : {}),
      ...(agent?.workflowYaml ? { 'workflow.yaml': agent.workflowYaml } : {}),
    }),
    [agent?.body, agent?.soul, agent?.workflowYaml],
  )
  const selectedFileContent = selectedFileNode?.path
    ? (inlineContentByPath[selectedFileNode.path] ?? null)
    : null
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

  const handleOpenReport = () => {
    if (!viewerUserId) {
      handleRequireLogin()
      return
    }
    setReportReason('')
    setReportDetails('')
    setReportDialogOpen(true)
  }

  const handleSubmitReport = async () => {
    if (!reportReason.trim()) {
      toast.error(t('agents.detail.reportReasonRequired'))
      return
    }
    try {
      await reportMutation.mutateAsync({
        reason: reportReason.trim(),
        details: reportDetails.trim() || undefined,
      })
      setReportDialogOpen(false)
      setReportReason('')
      setReportDetails('')
      setHasReported(true)
      toast.success(t('agents.detail.reportSuccessTitle'), t('agents.detail.reportSuccessDescription'))
    } catch (err) {
      toast.error(
        t('agents.detail.reportErrorTitle'),
        err instanceof Error ? err.message : '',
      )
    }
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

  /**
   * Opens the preview dialog for a clicked inline file node.
   */
  const handleInlineFileClick = (node: FileTreeNode) => {
    setSelectedFileNode(node)
    setFilePreviewOpen(true)
  }

  /**
   * Downloads the currently selected inline file content.
   */
  const handleInlineFileDownload = () => {
    if (!selectedFileNode || !selectedFileContent) return
    triggerBrowserDownload(
      selectedFileNode.name,
      selectedFileContent,
      selectedFileNode.file?.contentType ?? 'text/plain;charset=utf-8',
    )
  }

  /**
   * Downloads the selected agent package via backend endpoint.
   */
  const handleDownloadPackage = () => {
    if (!targetNamespace || !targetSlug || !agent.version) return
    const cleanNamespace = targetNamespace.startsWith('@') ? targetNamespace.slice(1) : targetNamespace
    const url = buildApiUrl(
      `${WEB_API_PREFIX}/agents/${cleanNamespace}/${encodeURIComponent(targetSlug)}/versions/${encodeURIComponent(agent.version)}/download`,
    )
    triggerBrowserUrlDownload(url)
  }

  /**
   * Copies agent share text and detail URL to clipboard.
   */
  const handleShareAgent = async () => {
    if (!targetNamespace || !targetSlug) return
    try {
      const baseUrl = getBaseUrl()
      const shareUrl = `${baseUrl}/agents/${targetNamespace}/${encodeURIComponent(targetSlug)}`
      const displayName = targetNamespace === 'global'
        ? targetSlug
        : `${targetNamespace}/${targetSlug}`
      const shareText = `${displayName}\n${agent.description ?? ''}\n${shareUrl}`
      await copyShareText(shareText)
    } catch (err) {
      toast.error(t('skillDetail.share.failed'), err instanceof Error ? err.message : '')
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
            <TabsTrigger value="files">{t('agents.detail.tabFiles')}</TabsTrigger>
            <TabsTrigger value="versions">{t('agents.detail.tabVersions')}</TabsTrigger>
            <TabsTrigger value="comments">{t('agents.detail.tabComments')}</TabsTrigger>
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

          <TabsContent value="files" className="mt-6">
            <Card className="p-0 overflow-hidden">
              {inlineFiles.length > 0 ? (
                <FileTree files={inlineFiles} onFileClick={handleInlineFileClick} />
              ) : (
                <div className="p-8 text-muted-foreground text-center">{t('skillDetail.noFiles')}</div>
              )}
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

          <TabsContent value="comments" className="mt-6">
            <Card className="p-6">
              {agent.latestPublishedVersionId ? (
                <AgentVersionCommentsSection
                  versionId={agent.latestPublishedVersionId}
                  canPost={!!viewerUserId}
                />
              ) : (
                <p className="text-sm text-muted-foreground">{t('agents.noPublishedVersion')}</p>
              )}
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      <aside className="w-full lg:w-80 flex-shrink-0 space-y-5">
        {inlineFiles.length > 0 && (
          <Card className="p-5 space-y-3">
            <button
              type="button"
              className="flex w-full items-center gap-2 text-left"
              aria-expanded={fileBrowserOpen}
              onClick={() => setFileBrowserOpen((value) => !value)}
            >
              <Folder className="w-4 h-4 text-muted-foreground" />
              <span className="text-sm font-semibold font-heading text-foreground">
                {t('fileTree.title')}
              </span>
              <span className="text-xs text-muted-foreground ml-auto mr-2">
                {inlineFiles.length}
              </span>
              <span
                className={cn(
                  'text-muted-foreground transition-transform duration-200',
                  fileBrowserOpen && 'rotate-180',
                )}
              >
                <ChevronDown className="h-4 w-4" />
              </span>
            </button>
            {fileBrowserOpen && (
              <div className="max-h-[400px] overflow-y-auto -mx-5 px-5">
                <FileTree files={inlineFiles} onFileClick={handleInlineFileClick} bare />
              </div>
            )}
          </Card>
        )}

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

        {targetNamespace && targetSlug && (
          <Card className="p-5">
            <Button
              variant="outline"
              className="w-full"
              onClick={handleOpenReport}
              disabled={hasReported}
            >
              {hasReported ? t('agents.detail.reportAlreadySubmitted') : t('agents.detail.reportButton')}
            </Button>
          </Card>
        )}

        {targetNamespace && targetSlug && agent.version && (
          <Card className="p-5 space-y-4">
            <div className="flex items-center gap-2">
              <Terminal className="w-4 h-4 text-muted-foreground" />
              <span className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.install')}</span>
            </div>
            <InstallCommand namespace={targetNamespace} slug={targetSlug} version={agent.version} />
          </Card>
        )}

        <Button
          className="w-full"
          variant="outline"
          size="lg"
          onClick={handleDownloadPackage}
          disabled={!targetNamespace || !targetSlug || !agent.version}
        >
          <Download className="w-4 h-4 mr-2" />
          {t('skillDetail.download')}
        </Button>

        <Button
          className="w-full"
          variant="outline"
          size="lg"
          onClick={handleShareAgent}
          disabled={!targetNamespace || !targetSlug}
        >
          {shareCopied ? t('skillDetail.share.copied') : t('skillDetail.share.button')}
        </Button>

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

        <AgentLabelPanel
          namespace={targetNamespace}
          slug={targetSlug}
          initialLabels={[]}
          canManage={Boolean(user && (canManageLifecycle || hasRole('SUPER_ADMIN')))}
          isSuperAdmin={hasRole('SUPER_ADMIN')}
        />
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

      <Dialog open={reportDialogOpen} onOpenChange={setReportDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('agents.detail.reportDialogTitle')}</DialogTitle>
            <DialogDescription>{t('agents.detail.reportDialogDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Input
              value={reportReason}
              onChange={(event) => setReportReason(event.target.value)}
              placeholder={t('agents.detail.reportReasonPlaceholder')}
              maxLength={200}
            />
            <Textarea
              value={reportDetails}
              onChange={(event) => setReportDetails(event.target.value)}
              placeholder={t('agents.detail.reportDetailsPlaceholder')}
              rows={5}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReportDialogOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button onClick={handleSubmitReport} disabled={reportMutation.isPending}>
              {reportMutation.isPending
                ? t('agents.lifecycle.processing')
                : t('agents.detail.submitReport')}
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

      <FilePreviewDialog
        open={filePreviewOpen}
        onOpenChange={(open) => {
          setFilePreviewOpen(open)
          if (!open) setSelectedFileNode(null)
        }}
        node={selectedFileNode}
        content={selectedFileContent}
        isLoading={false}
        error={null}
        onDownload={handleInlineFileDownload}
      />
    </div>
  )
}
