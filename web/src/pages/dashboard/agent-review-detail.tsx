import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { useAgentReviewDetail } from '@/features/agent/use-agent-review-detail'
import { useApproveAgentReview } from '@/features/agent/use-approve-agent-review'
import { useRejectAgentReview } from '@/features/agent/use-reject-agent-review'

interface AgentReviewDetailPageProps {
  taskId: number
}

/**
 * Reviewer detail page for one agent submission. Shows the agent metadata,
 * the inline soul.md and workflow.yaml, and approve/reject controls. Soul and
 * workflow are rendered as <pre> blocks rather than markdown — workflow is YAML
 * (not markdown) and the reviewer needs to see soul exactly as it will be
 * served to the runtime.
 */
export function AgentReviewDetailPage({ taskId }: AgentReviewDetailPageProps) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()

  const { data, isLoading, isError } = useAgentReviewDetail(taskId)

  const [comment, setComment] = useState('')
  const [showRejectForm, setShowRejectForm] = useState(false)
  const [approveDialogOpen, setApproveDialogOpen] = useState(false)
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)

  const approveMutation = useApproveAgentReview({
    onSuccess: () => {
      toast.success(t('agentReviews.detail.approveSuccess'))
      void navigate({ to: '/dashboard/agent-reviews' })
    },
    onError: (error) => {
      toast.error(t('agentReviews.detail.approveFailed'), error.message)
    },
  })
  const rejectMutation = useRejectAgentReview({
    onSuccess: () => {
      toast.success(t('agentReviews.detail.rejectSuccess'))
      void navigate({ to: '/dashboard/agent-reviews' })
    },
    onError: (error) => {
      toast.error(t('agentReviews.detail.rejectFailed'), error.message)
    },
  })

  const formatDate = (value: string | null | undefined) =>
    value ? formatLocalDateTime(value, i18n.language) : '—'

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <p className="text-muted-foreground">{t('agentReviews.detail.loading')}</p>
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-4">
        <p className="text-destructive">{t('agentReviews.detail.loadError')}</p>
        <Button variant="outline" onClick={() => navigate({ to: '/dashboard/agent-reviews' })}>
          {t('agentReviews.detail.back')}
        </Button>
      </div>
    )
  }

  const { task, agent, version } = data
  const isPending = task.status === 'PENDING'

  const handleApprove = () => {
    approveMutation.mutate({ taskId, comment: comment || undefined })
  }
  const handleReject = () => {
    if (!comment.trim()) {
      toast.error(t('agentReviews.detail.rejectReasonRequired'))
      return
    }
    rejectMutation.mutate({ taskId, comment })
  }

  const statusBadge = (() => {
    if (task.status === 'PENDING') {
      return (
        <span className="px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-500 text-sm">
          {t('agentReviews.detail.statusPending')}
        </span>
      )
    }
    if (task.status === 'APPROVED') {
      return (
        <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-500 text-sm">
          {t('agentReviews.detail.statusApproved')}
        </span>
      )
    }
    return (
      <span className="px-2.5 py-0.5 rounded-full bg-red-500/10 text-red-500 text-sm">
        {t('agentReviews.detail.statusRejected')}
      </span>
    )
  })()

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-8 animate-fade-up">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">
          {t('agentReviews.detail.title')}
        </h1>
        <Button variant="outline" onClick={() => navigate({ to: '/dashboard/agent-reviews' })}>
          {t('agentReviews.detail.back')}
        </Button>
      </div>

      <Card className="p-6 space-y-5">
        <h2 className="text-xl font-semibold">{t('agentReviews.detail.agentSection')}</h2>
        <div className="grid grid-cols-2 gap-4">
          <Meta label={t('agentReviews.detail.metaTaskId')} value={`#${task.id}`} mono />
          <Meta label={t('agentReviews.detail.metaStatus')} valueNode={statusBadge} />
          <Meta
            label="Agent"
            value={`${agent.namespace}/${agent.slug}`}
            mono
          />
          <Meta label={t('agentReviews.detail.metaVersion')} value={version.version} mono />
          <Meta label={t('agentReviews.detail.metaSubmitter')} value={task.submittedBy} />
          <Meta label={t('agentReviews.detail.metaSubmittedAt')} value={formatDate(task.submittedAt)} />
          {task.reviewedBy && (
            <>
              <Meta label={t('agentReviews.detail.metaReviewer')} value={task.reviewedBy} />
              <Meta label={t('agentReviews.detail.metaReviewedAt')} value={formatDate(task.reviewedAt)} />
            </>
          )}
        </div>
        {agent.displayName && (
          <p className="text-base font-semibold">{agent.displayName}</p>
        )}
        {agent.description && (
          <p className="text-sm text-muted-foreground">{agent.description}</p>
        )}
        {task.reviewComment && (
          <div className="space-y-1.5">
            <Label className="text-xs uppercase tracking-wider text-muted-foreground">
              {t('agentReviews.detail.commentLabel')}
            </Label>
            <p className="p-4 bg-secondary/50 rounded-xl text-sm leading-relaxed">
              {task.reviewComment}
            </p>
          </div>
        )}
      </Card>

      <Card className="p-6 space-y-3">
        <h2 className="text-xl font-semibold">{t('agentReviews.detail.soulSection')}</h2>
        {version.soulMd ? (
          <pre className="whitespace-pre-wrap text-sm bg-muted/40 rounded-lg p-4 leading-relaxed">
            {version.soulMd}
          </pre>
        ) : (
          <p className="text-sm text-muted-foreground italic">
            {t('agentReviews.detail.noSoul')}
          </p>
        )}
      </Card>

      <Card className="p-6 space-y-3">
        <h2 className="text-xl font-semibold">{t('agentReviews.detail.workflowSection')}</h2>
        {version.workflowYaml ? (
          <pre className="whitespace-pre-wrap text-xs font-mono bg-muted/40 rounded-lg p-4 overflow-x-auto leading-relaxed">
            {version.workflowYaml}
          </pre>
        ) : (
          <p className="text-sm text-muted-foreground italic">
            {t('agentReviews.detail.noWorkflow')}
          </p>
        )}
      </Card>

      {isPending && (
        <Card className="p-6 space-y-5">
          <h2 className="text-xl font-semibold">{t('agentReviews.detail.actionsTitle')}</h2>

          <div className="space-y-2">
            <Label htmlFor="agent-review-comment" className="text-sm font-semibold">
              {t('agentReviews.detail.commentLabel')}
            </Label>
            <Textarea
              id="agent-review-comment"
              placeholder={t('agentReviews.detail.commentPlaceholder')}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={4}
            />
          </div>

          <div className="flex gap-3">
            <Button
              onClick={() => setApproveDialogOpen(true)}
              disabled={approveMutation.isPending || rejectMutation.isPending}
            >
              {t('agentReviews.detail.approve')}
            </Button>
            {!showRejectForm ? (
              <Button
                variant="destructive"
                onClick={() => setShowRejectForm(true)}
                disabled={approveMutation.isPending || rejectMutation.isPending}
              >
                {t('agentReviews.detail.reject')}
              </Button>
            ) : (
              <>
                <Button
                  variant="destructive"
                  onClick={() => {
                    if (!comment.trim()) {
                      toast.error(t('agentReviews.detail.rejectReasonRequired'))
                      return
                    }
                    setRejectDialogOpen(true)
                  }}
                  disabled={approveMutation.isPending || rejectMutation.isPending || !comment.trim()}
                >
                  {t('agentReviews.detail.confirmReject')}
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setShowRejectForm(false)}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  {t('agentReviews.detail.cancelReject')}
                </Button>
              </>
            )}
          </div>

          {showRejectForm && !comment.trim() && (
            <p className="text-sm text-destructive">
              {t('agentReviews.detail.rejectReasonRequired')}
            </p>
          )}
        </Card>
      )}

      <ConfirmDialog
        open={approveDialogOpen}
        onOpenChange={setApproveDialogOpen}
        title={t('agentReviews.detail.approve')}
        confirmText={t('agentReviews.detail.approve')}
        onConfirm={handleApprove}
      />
      <ConfirmDialog
        open={rejectDialogOpen}
        onOpenChange={setRejectDialogOpen}
        title={t('agentReviews.detail.confirmReject')}
        confirmText={t('agentReviews.detail.confirmReject')}
        variant="destructive"
        onConfirm={handleReject}
      />
    </div>
  )
}

function Meta({
  label,
  value,
  valueNode,
  mono = false,
}: {
  label: string
  value?: string
  valueNode?: React.ReactNode
  mono?: boolean
}) {
  return (
    <div className="space-y-1">
      <Label className="text-xs text-muted-foreground uppercase tracking-wider">{label}</Label>
      {valueNode ?? <p className={mono ? 'font-mono font-semibold' : 'font-semibold'}>{value ?? '—'}</p>}
    </div>
  )
}
