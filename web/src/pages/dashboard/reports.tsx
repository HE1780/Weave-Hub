import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { Button } from '@/shared/ui/button'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { useDismissSkillReport, useResolveSkillReport, useSkillReports } from '@/features/report/use-skill-reports'
import { useAgentReports, useDismissAgentReport, useResolveAgentReport } from '@/features/report/use-agent-reports'
import { REPORT_TEXT_WRAP_CLASS_NAME } from '@/features/report/report-text'
import { toast } from '@/shared/lib/toast'
import type { ReportDisposition, AgentReportDisposition } from '@/api/types'

type Translator = (key: string, options?: Record<string, unknown>) => string

/**
 * Moderation page for skill and agent reports. The outer Tabs split the two
 * report families because they have different "open" navigation targets and
 * historically had different disposition options. Both now support the full
 * RESOLVE_ONLY / RESOLVE_AND_HIDE / RESOLVE_AND_ARCHIVE set.
 */
export function ReportsPage() {
  const { t } = useTranslation()

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('reports.title')} subtitle={t('reports.subtitle')} />

      <Tabs defaultValue="SKILL">
        <TabsList>
          <TabsTrigger value="SKILL">{t('reports.tabSkillReports')}</TabsTrigger>
          <TabsTrigger value="AGENT">{t('reports.tabAgentReports')}</TabsTrigger>
        </TabsList>
        <TabsContent value="SKILL" className="mt-6">
          <SkillReportsPanel />
        </TabsContent>
        <TabsContent value="AGENT" className="mt-6">
          <AgentReportsPanel />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function SkillReportsPanel() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [pendingAction, setPendingAction] = useState<{
    id: number
    action: 'resolve' | 'dismiss'
    disposition?: ReportDisposition
    skillLabel: string
  } | null>(null)
  const { data: pendingReports, isLoading: isPendingLoading } = useSkillReports('PENDING')
  const { data: resolvedReports, isLoading: isResolvedLoading } = useSkillReports('RESOLVED')
  const { data: dismissedReports, isLoading: isDismissedLoading } = useSkillReports('DISMISSED')
  const resolveMutation = useResolveSkillReport()
  const dismissMutation = useDismissSkillReport()

  const formatDate = (dateString: string) => formatLocalDateTime(dateString, i18n.language)

  const handleOpenSkill = (namespace?: string, skillSlug?: string) => {
    if (!namespace || !skillSlug) {
      return
    }
    navigate({ to: `/space/${namespace}/${skillSlug}` })
  }

  const handleConfirm = async () => {
    if (!pendingAction) {
      return
    }
    try {
      if (pendingAction.action === 'resolve') {
        await resolveMutation.mutateAsync({ id: pendingAction.id, disposition: pendingAction.disposition })
        toast.success(resolveSuccessTitle(pendingAction.disposition, t), resolveSuccessDescription(pendingAction.disposition, t, pendingAction.skillLabel))
      } else {
        await dismissMutation.mutateAsync({ id: pendingAction.id })
        toast.success(t('reports.dismissSuccessTitle'), t('reports.dismissSuccessDescription', { skill: pendingAction.skillLabel }))
      }
      setPendingAction(null)
    } catch (error) {
      toast.error(
        pendingAction.action === 'resolve' ? resolveErrorTitle(pendingAction.disposition, t) : t('reports.dismissErrorTitle'),
        error instanceof Error ? error.message : '',
      )
    }
  }

  const renderList = (reports: typeof pendingReports, isLoading: boolean, status: 'PENDING' | 'RESOLVED' | 'DISMISSED') => {
    if (isLoading) {
      return (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <div key={index} className="h-24 animate-shimmer rounded-lg" />
          ))}
        </div>
      )
    }

    if (!reports || reports.length === 0) {
      return <Card className="p-12 text-center text-muted-foreground">{t('reports.empty')}</Card>
    }

    return (
      <div className="space-y-4">
        {reports.map((report) => {
          const skillLabel = report.skillDisplayName || report.skillSlug || `#${report.skillId}`
          return (
            <Card key={report.id} className="p-5 space-y-4">
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-2 min-w-0">
                  <button
                    type="button"
                    className={`text-left font-semibold font-heading text-foreground transition-colors hover:text-primary ${REPORT_TEXT_WRAP_CLASS_NAME}`}
                    onClick={() => handleOpenSkill(report.namespace, report.skillSlug)}
                  >
                    {report.namespace && report.skillSlug ? `${report.namespace}/${report.skillSlug}` : skillLabel}
                  </button>
                  <div className={`text-sm text-muted-foreground ${REPORT_TEXT_WRAP_CLASS_NAME}`}>{skillLabel}</div>
                  <div className={`text-sm text-foreground ${REPORT_TEXT_WRAP_CLASS_NAME}`}>{report.reason}</div>
                  {report.details ? <div className={`text-sm text-muted-foreground ${REPORT_TEXT_WRAP_CLASS_NAME}`}>{report.details}</div> : null}
                </div>
                <div className="text-right text-xs text-muted-foreground space-y-1 shrink-0">
                  <div>{t('reports.reporter')}: {report.reporterId}</div>
                  <div>{formatDate(report.createdAt)}</div>
                  {report.handledAt ? <div>{formatDate(report.handledAt)}</div> : null}
                </div>
              </div>

              {status === 'PENDING' ? (
                <div className="flex items-center justify-end gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'dismiss', skillLabel })}
                  >
                    {t('reports.dismiss')}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'resolve', disposition: 'RESOLVE_ONLY', skillLabel })}
                  >
                    {t('reports.resolve')}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'resolve', disposition: 'RESOLVE_AND_HIDE', skillLabel })}
                  >
                    {t('reports.resolveAndHide')}
                  </Button>
                  <Button
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'resolve', disposition: 'RESOLVE_AND_ARCHIVE', skillLabel })}
                  >
                    {t('reports.resolveAndArchive')}
                  </Button>
                </div>
              ) : (
                <div className="text-sm text-muted-foreground">
                  {t('reports.handledBy')}: {report.handledBy || '—'}
                </div>
              )}
            </Card>
          )
        })}
      </div>
    )
  }

  return (
    <>
      <Tabs defaultValue="PENDING">
        <TabsList>
          <TabsTrigger value="PENDING">{t('reports.tabPending')}</TabsTrigger>
          <TabsTrigger value="RESOLVED">{t('reports.tabResolved')}</TabsTrigger>
          <TabsTrigger value="DISMISSED">{t('reports.tabDismissed')}</TabsTrigger>
        </TabsList>

        <TabsContent value="PENDING" className="mt-6">
          {renderList(pendingReports, isPendingLoading, 'PENDING')}
        </TabsContent>
        <TabsContent value="RESOLVED" className="mt-6">
          {renderList(resolvedReports, isResolvedLoading, 'RESOLVED')}
        </TabsContent>
        <TabsContent value="DISMISSED" className="mt-6">
          {renderList(dismissedReports, isDismissedLoading, 'DISMISSED')}
        </TabsContent>
      </Tabs>

      <ConfirmDialog
        open={pendingAction !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingAction(null)
          }
        }}
        title={pendingAction?.action === 'resolve' ? t('reports.resolveConfirmTitle') : t('reports.dismissConfirmTitle')}
        description={pendingAction?.action === 'resolve'
          ? resolveConfirmDescription(pendingAction?.disposition, t, pendingAction?.skillLabel ?? '')
          : t('reports.dismissConfirmDescription', { skill: pendingAction?.skillLabel ?? '' })}
        confirmText={pendingAction?.action === 'resolve' ? resolveConfirmText(pendingAction?.disposition, t) : t('reports.dismiss')}
        onConfirm={handleConfirm}
      />
    </>
  )
}

/**
 * Agent moderation panel. Mirrors {@link SkillReportsPanel} but against the
 * agent report family. Differences:
 *   - opens {@code /agents/:ns/:slug} instead of {@code /space/:ns/:slug}
 *   - no {@code RESOLVE_AND_HIDE} action (agent has no hide path yet)
 *   - reads {@code agentSlug} / {@code agentDisplayName} instead of skill ones
 */
function AgentReportsPanel() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [pendingAction, setPendingAction] = useState<{
    id: number
    action: 'resolve' | 'dismiss'
    disposition?: AgentReportDisposition
    agentLabel: string
  } | null>(null)
  const { data: pendingReports, isLoading: isPendingLoading } = useAgentReports('PENDING')
  const { data: resolvedReports, isLoading: isResolvedLoading } = useAgentReports('RESOLVED')
  const { data: dismissedReports, isLoading: isDismissedLoading } = useAgentReports('DISMISSED')
  const resolveMutation = useResolveAgentReport()
  const dismissMutation = useDismissAgentReport()

  const formatDate = (dateString: string) => formatLocalDateTime(dateString, i18n.language)

  const handleOpenAgent = (namespace?: string, agentSlug?: string) => {
    if (!namespace || !agentSlug) {
      return
    }
    navigate({ to: `/agents/${namespace}/${agentSlug}` })
  }

  const handleConfirm = async () => {
    if (!pendingAction) {
      return
    }
    try {
      if (pendingAction.action === 'resolve') {
        await resolveMutation.mutateAsync({ id: pendingAction.id, disposition: pendingAction.disposition })
        toast.success(
          agentResolveSuccessTitle(pendingAction.disposition, t),
          agentResolveSuccessDescription(pendingAction.disposition, t, pendingAction.agentLabel),
        )
      } else {
        await dismissMutation.mutateAsync({ id: pendingAction.id })
        toast.success(t('reports.dismissSuccessTitle'), t('reports.dismissSuccessDescription', { skill: pendingAction.agentLabel }))
      }
      setPendingAction(null)
    } catch (error) {
      toast.error(
        pendingAction.action === 'resolve' ? agentResolveErrorTitle(pendingAction.disposition, t) : t('reports.dismissErrorTitle'),
        error instanceof Error ? error.message : '',
      )
    }
  }

  const renderList = (reports: typeof pendingReports, isLoading: boolean, status: 'PENDING' | 'RESOLVED' | 'DISMISSED') => {
    if (isLoading) {
      return (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <div key={index} className="h-24 animate-shimmer rounded-lg" />
          ))}
        </div>
      )
    }

    if (!reports || reports.length === 0) {
      return <Card className="p-12 text-center text-muted-foreground">{t('reports.empty')}</Card>
    }

    return (
      <div className="space-y-4">
        {reports.map((report) => {
          const agentLabel = report.agentDisplayName || report.agentSlug || `#${report.agentId}`
          return (
            <Card key={report.id} className="p-5 space-y-4">
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-2 min-w-0">
                  <button
                    type="button"
                    className={`text-left font-semibold font-heading text-foreground transition-colors hover:text-primary ${REPORT_TEXT_WRAP_CLASS_NAME}`}
                    onClick={() => handleOpenAgent(report.namespace, report.agentSlug)}
                  >
                    {report.namespace && report.agentSlug ? `${report.namespace}/${report.agentSlug}` : agentLabel}
                  </button>
                  <div className={`text-sm text-muted-foreground ${REPORT_TEXT_WRAP_CLASS_NAME}`}>{agentLabel}</div>
                  <div className={`text-sm text-foreground ${REPORT_TEXT_WRAP_CLASS_NAME}`}>{report.reason}</div>
                  {report.details ? <div className={`text-sm text-muted-foreground ${REPORT_TEXT_WRAP_CLASS_NAME}`}>{report.details}</div> : null}
                </div>
                <div className="text-right text-xs text-muted-foreground space-y-1 shrink-0">
                  <div>{t('reports.reporter')}: {report.reporterId}</div>
                  <div>{formatDate(report.createdAt)}</div>
                  {report.handledAt ? <div>{formatDate(report.handledAt)}</div> : null}
                </div>
              </div>

              {status === 'PENDING' ? (
                <div className="flex items-center justify-end gap-2">
                  {report.namespace && report.agentSlug ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleOpenAgent(report.namespace, report.agentSlug)}
                    >
                      {t('reports.openAgent')}
                    </Button>
                  ) : null}
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'dismiss', agentLabel })}
                  >
                    {t('reports.dismiss')}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'resolve', disposition: 'RESOLVE_ONLY', agentLabel })}
                  >
                    {t('reports.resolve')}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'resolve', disposition: 'RESOLVE_AND_HIDE', agentLabel })}
                  >
                    {t('reports.resolveAndHide')}
                  </Button>
                  <Button
                    size="sm"
                    disabled={resolveMutation.isPending || dismissMutation.isPending}
                    onClick={() => setPendingAction({ id: report.id, action: 'resolve', disposition: 'RESOLVE_AND_ARCHIVE', agentLabel })}
                  >
                    {t('reports.resolveAndArchive')}
                  </Button>
                </div>
              ) : (
                <div className="text-sm text-muted-foreground">
                  {t('reports.handledBy')}: {report.handledBy || '—'}
                </div>
              )}
            </Card>
          )
        })}
      </div>
    )
  }

  return (
    <>
      <Tabs defaultValue="PENDING">
        <TabsList>
          <TabsTrigger value="PENDING">{t('reports.tabPending')}</TabsTrigger>
          <TabsTrigger value="RESOLVED">{t('reports.tabResolved')}</TabsTrigger>
          <TabsTrigger value="DISMISSED">{t('reports.tabDismissed')}</TabsTrigger>
        </TabsList>

        <TabsContent value="PENDING" className="mt-6">
          {renderList(pendingReports, isPendingLoading, 'PENDING')}
        </TabsContent>
        <TabsContent value="RESOLVED" className="mt-6">
          {renderList(resolvedReports, isResolvedLoading, 'RESOLVED')}
        </TabsContent>
        <TabsContent value="DISMISSED" className="mt-6">
          {renderList(dismissedReports, isDismissedLoading, 'DISMISSED')}
        </TabsContent>
      </Tabs>

      <ConfirmDialog
        open={pendingAction !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingAction(null)
          }
        }}
        title={pendingAction?.action === 'resolve' ? t('reports.resolveConfirmTitle') : t('reports.dismissConfirmTitle')}
        description={pendingAction?.action === 'resolve'
          ? agentResolveConfirmDescription(pendingAction?.disposition, t, pendingAction?.agentLabel ?? '')
          : t('reports.dismissConfirmDescription', { skill: pendingAction?.agentLabel ?? '' })}
        confirmText={pendingAction?.action === 'resolve' ? agentResolveConfirmText(pendingAction?.disposition, t) : t('reports.dismiss')}
        onConfirm={handleConfirm}
      />
    </>
  )
}

/**
 * Resolves the action label used by the confirmation dialog from the selected
 * moderation disposition.
 */
function resolveConfirmText(disposition: ReportDisposition | undefined, t: Translator) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHide')
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchive')
  return t('reports.resolve')
}

/**
 * Chooses the confirmation body copy for the pending moderation action.
 */
function resolveConfirmDescription(disposition: ReportDisposition | undefined, t: Translator, skillLabel: string) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHideConfirmDescription', { skill: skillLabel })
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchiveConfirmDescription', { skill: skillLabel })
  return t('reports.resolveConfirmDescription', { skill: skillLabel })
}

/**
 * Chooses the toast title shown after a successful resolution flow.
 */
function resolveSuccessTitle(disposition: ReportDisposition | undefined, t: Translator) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHideSuccessTitle')
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchiveSuccessTitle')
  return t('reports.resolveSuccessTitle')
}

/**
 * Chooses the toast body shown after a successful resolution flow.
 */
function resolveSuccessDescription(disposition: ReportDisposition | undefined, t: Translator, skillLabel: string) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHideSuccessDescription', { skill: skillLabel })
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchiveSuccessDescription', { skill: skillLabel })
  return t('reports.resolveSuccessDescription', { skill: skillLabel })
}

/**
 * Chooses the toast title shown when the resolution flow fails.
 */
function resolveErrorTitle(disposition: ReportDisposition | undefined, t: Translator) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHideErrorTitle')
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchiveErrorTitle')
  return t('reports.resolveErrorTitle')
}

/**
 * Agent-side confirmation labels — mirror skill's options. RESOLVE_AND_HIDE
 * is now supported on the backend (AgentGovernanceService.hideAgent), but
 * AdminAgentReportController still allows SKILL_ADMIN to invoke it; the
 * skill counterpart restricts hide to SUPER_ADMIN. Tracked for follow-up.
 */
function agentResolveConfirmText(disposition: AgentReportDisposition | undefined, t: Translator) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHide')
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchive')
  return t('reports.resolve')
}

function agentResolveConfirmDescription(disposition: AgentReportDisposition | undefined, t: Translator, agentLabel: string) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHideConfirmDescription', { skill: agentLabel })
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchiveConfirmDescription', { skill: agentLabel })
  return t('reports.resolveConfirmDescription', { skill: agentLabel })
}

function agentResolveSuccessTitle(disposition: AgentReportDisposition | undefined, t: Translator) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHideSuccessTitle')
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchiveSuccessTitle')
  return t('reports.resolveSuccessTitle')
}

function agentResolveSuccessDescription(disposition: AgentReportDisposition | undefined, t: Translator, agentLabel: string) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHideSuccessDescription', { skill: agentLabel })
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchiveSuccessDescription', { skill: agentLabel })
  return t('reports.resolveSuccessDescription', { skill: agentLabel })
}

function agentResolveErrorTitle(disposition: AgentReportDisposition | undefined, t: Translator) {
  if (disposition === 'RESOLVE_AND_HIDE') return t('reports.resolveAndHideErrorTitle')
  if (disposition === 'RESOLVE_AND_ARCHIVE') return t('reports.resolveAndArchiveErrorTitle')
  return t('reports.resolveErrorTitle')
}
