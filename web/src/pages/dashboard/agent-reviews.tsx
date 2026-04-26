import { useMemo, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { EmptyState } from '@/shared/components/empty-state'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import type { AgentReviewTaskDtoStatus } from '@/api/client'
import { useAgentReviews } from '@/features/agent/use-agent-reviews'

const PAGE_SIZE = 20

/**
 * Reviewer inbox for agent submissions. The agent review API requires a
 * namespace scope (the controller rejects calls without one), so the page is
 * structured around a namespace selector first, then status tabs second —
 * inverted from the skill review page where global SKILL_ADMIN can see all.
 */
export function AgentReviewsPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { data: myNamespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()

  const adminNamespaces = useMemo(
    () =>
      (myNamespaces ?? []).filter(
        (ns) => ns.currentUserRole === 'OWNER' || ns.currentUserRole === 'ADMIN',
      ),
    [myNamespaces],
  )

  const [selectedNamespaceId, setSelectedNamespaceId] = useState<number | undefined>(undefined)
  const [activeStatus, setActiveStatus] = useState<AgentReviewTaskDtoStatus>('PENDING')
  const [pages, setPages] = useState<Record<AgentReviewTaskDtoStatus, number>>({
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
  })

  const effectiveNamespaceId =
    selectedNamespaceId ?? (adminNamespaces.length === 1 ? adminNamespaces[0].id : undefined)

  const pendingQuery = useAgentReviews(
    effectiveNamespaceId,
    'PENDING',
    pages.PENDING,
    PAGE_SIZE,
    activeStatus === 'PENDING',
  )
  const approvedQuery = useAgentReviews(
    effectiveNamespaceId,
    'APPROVED',
    pages.APPROVED,
    PAGE_SIZE,
    activeStatus === 'APPROVED',
  )
  const rejectedQuery = useAgentReviews(
    effectiveNamespaceId,
    'REJECTED',
    pages.REJECTED,
    PAGE_SIZE,
    activeStatus === 'REJECTED',
  )

  const formatDate = (value: string | null | undefined) =>
    value ? formatLocalDateTime(value, i18n.language) : '—'

  const handleRowClick = (taskId: number) => {
    navigate({ to: `/dashboard/agent-reviews/${taskId}` })
  }

  const changePage = (status: AgentReviewTaskDtoStatus, nextPage: number) => {
    setPages((current) => ({ ...current, [status]: nextPage }))
  }

  const renderTable = (
    query: typeof pendingQuery,
    status: AgentReviewTaskDtoStatus,
  ) => {
    if (!effectiveNamespaceId) {
      return <EmptyState title={t('agentReviews.selectNamespace')} />
    }
    if (query.isLoading) {
      return (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-14 animate-shimmer rounded-xl" />
          ))}
        </div>
      )
    }
    if (query.isError) {
      return <p className="text-destructive">{t('agentReviews.loadError')}</p>
    }
    const items = query.data?.items
    if (!items || items.length === 0) {
      return <EmptyState title={t('agentReviews.empty')} />
    }
    return (
      <div className="overflow-hidden rounded-xl border border-border/60">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/35">
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                {t('agentReviews.colTaskId')}
              </TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                {t('agentReviews.colAgent')}
              </TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                {t('agentReviews.colVersionId')}
              </TableHead>
              <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                {t('agentReviews.colSubmitter')}
              </TableHead>
              {status === 'PENDING' ? (
                <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {t('agentReviews.colSubmittedAt')}
                </TableHead>
              ) : (
                <>
                  <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                    {t('agentReviews.colReviewer')}
                  </TableHead>
                  <TableHead className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                    {t('agentReviews.colReviewedAt')}
                  </TableHead>
                </>
              )}
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((task) => (
              <TableRow
                key={task.id}
                className="cursor-pointer transition-colors hover:bg-muted/30"
                onClick={() => handleRowClick(task.id)}
              >
                <TableCell className="font-mono">#{task.id}</TableCell>
                <TableCell>
                  {task.agentSlug ? (
                    <div className="flex flex-col">
                      <span className="font-medium">{task.agentDisplayName ?? task.agentSlug}</span>
                      <span className="text-xs text-muted-foreground font-mono">
                        {task.agentNamespace ?? '—'}/{task.agentSlug}
                      </span>
                    </div>
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )}
                </TableCell>
                <TableCell className="font-mono">
                  {task.agentVersion ?? `#${task.agentVersionId}`}
                </TableCell>
                <TableCell>{task.submittedBy}</TableCell>
                {status === 'PENDING' ? (
                  <TableCell>{formatDate(task.submittedAt)}</TableCell>
                ) : (
                  <>
                    <TableCell>{task.reviewedBy ?? '—'}</TableCell>
                    <TableCell>{formatDate(task.reviewedAt)}</TableCell>
                  </>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {query.data && (
          <div className="flex flex-col gap-3 border-t border-border/60 px-6 py-4 text-sm text-muted-foreground md:flex-row md:items-center md:justify-between">
            <p>
              {t('agentReviews.pageSummary', {
                total: query.data.totalElements,
                page: pages[status] + 1,
              })}
            </p>
            <Pagination
              page={pages[status]}
              totalPages={Math.max(query.data.totalPages, 1)}
              onPageChange={(nextPage) => changePage(status, nextPage)}
            />
          </div>
        )}
      </div>
    )
  }

  if (isLoadingNamespaces) {
    return (
      <div className="space-y-8 animate-fade-up">
        <DashboardPageHeader title={t('agentReviews.title')} subtitle={t('agentReviews.subtitle')} />
        <Card className="p-8 text-center text-muted-foreground">{t('agentReviews.loading')}</Card>
      </div>
    )
  }

  if (adminNamespaces.length === 0) {
    return (
      <div className="space-y-8 animate-fade-up">
        <DashboardPageHeader title={t('agentReviews.title')} subtitle={t('agentReviews.subtitle')} />
        <Card className="p-8 text-center text-muted-foreground">
          {t('agentReviews.noAdminNamespace')}
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('agentReviews.title')} subtitle={t('agentReviews.subtitle')} />

      <Card className="overflow-hidden border-border/60 shadow-sm">
        <CardHeader className="pb-4">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <CardTitle>{t('agentReviews.queueTitle')}</CardTitle>
            <div className="w-full max-w-xs">
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                {t('agentReviews.namespaceLabel')}
              </p>
              <Select
                value={effectiveNamespaceId ? String(effectiveNamespaceId) : ''}
                onValueChange={(value) => {
                  setSelectedNamespaceId(value ? Number(value) : undefined)
                  setPages({ PENDING: 0, APPROVED: 0, REJECTED: 0 })
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t('agentReviews.namespacePlaceholder')} />
                </SelectTrigger>
                <SelectContent>
                  {adminNamespaces.map((ns) => (
                    <SelectItem key={ns.id} value={String(ns.id)}>
                      {ns.displayName} ({ns.slug})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          <Tabs value={activeStatus} onValueChange={(v) => setActiveStatus(v as AgentReviewTaskDtoStatus)}>
            <TabsList className="gap-4 rounded-xl border-b-0 bg-muted/70 p-1 shadow-none">
              <TabsTrigger
                value="PENDING"
                className="mb-0 rounded-lg border-b-0 px-4 py-2.5 data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
              >
                {t('agentReviews.tabPending')}
              </TabsTrigger>
              <TabsTrigger
                value="APPROVED"
                className="mb-0 rounded-lg border-b-0 px-4 py-2.5 data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
              >
                {t('agentReviews.tabApproved')}
              </TabsTrigger>
              <TabsTrigger
                value="REJECTED"
                className="mb-0 rounded-lg border-b-0 px-4 py-2.5 data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=inactive]:text-muted-foreground"
              >
                {t('agentReviews.tabRejected')}
              </TabsTrigger>
            </TabsList>
            <TabsContent value="PENDING" className="mt-6">
              {renderTable(pendingQuery, 'PENDING')}
            </TabsContent>
            <TabsContent value="APPROVED" className="mt-6">
              {renderTable(approvedQuery, 'APPROVED')}
            </TabsContent>
            <TabsContent value="REJECTED" className="mt-6">
              {renderTable(rejectedQuery, 'REJECTED')}
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  )
}
