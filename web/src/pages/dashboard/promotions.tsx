import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useApprovePromotion, usePromotionList, useRejectPromotion } from '@/features/promotion/use-promotion-list'
import type { PromotionTask } from '@/api/types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'

// Skills-blue / Agents-purple chip styling, kept in one place so the colour
// pair stays in sync if the theme migrates to design tokens later.
const sourceTypeBadgeStyles: Record<PromotionTask['sourceType'], string> = {
  SKILL: 'bg-blue-100 text-blue-800',
  AGENT: 'bg-purple-100 text-purple-800',
}

function SourceTypeBadge({ type }: { type: PromotionTask['sourceType'] }) {
  const { t } = useTranslation()
  const label = type === 'AGENT' ? t('promotions.sourceType.agent') : t('promotions.sourceType.skill')
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${sourceTypeBadgeStyles[type]}`}>
      {label}
    </span>
  )
}

/**
 * Returns the human-readable source slug + version for a row, branching by
 * sourceType so the UI works for both kinds of promotion.
 */
function describeSource(item: PromotionTask): { slug: string; version: string } {
  if (item.sourceType === 'AGENT') {
    return { slug: item.sourceAgentSlug ?? '?', version: item.sourceAgentVersion ?? '?' }
  }
  return { slug: item.sourceSkillSlug ?? '?', version: item.sourceVersion ?? '?' }
}

/**
 * Renders one promotion queue lane. Pending items expose moderation actions,
 * while historical lanes stay read-only and surface the review comment only.
 */
function PromotionSection({ status }: { status: 'PENDING' | 'APPROVED' | 'REJECTED' }) {
  const { t, i18n } = useTranslation()
  const { data: items, isLoading } = usePromotionList(status)
  const approveMutation = useApprovePromotion()
  const rejectMutation = useRejectPromotion()
  const [commentById, setCommentById] = useState<Record<number, string>>({})

  if (isLoading) {
    return <div className="h-32 animate-shimmer rounded-xl" />
  }

  if (!items || items.length === 0) {
    return <Card className="p-10 text-center text-muted-foreground">{t('promotions.empty')}</Card>
  }

  return (
    <div className="space-y-4">
      {items.map((item) => {
        const source = describeSource(item)
        return (
        <Card key={item.id} className="p-5 space-y-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 font-semibold font-heading">
                <SourceTypeBadge type={item.sourceType} />
                <span>{item.sourceNamespace}/{source.slug}</span>
              </div>
              <div className="text-sm text-muted-foreground">
                {source.version} {'->'} @{item.targetNamespace}
              </div>
            </div>
            <div className="text-sm text-muted-foreground">{formatLocalDateTime(item.submittedAt, i18n.language)}</div>
          </div>
          {status === 'PENDING' ? (
            <>
              <Input
                placeholder={t('promotions.commentPlaceholder')}
                value={commentById[item.id] ?? ''}
                onChange={(event) => setCommentById((prev) => ({ ...prev, [item.id]: event.target.value }))}
              />
              <div className="flex gap-3">
                <Button
                  onClick={() => approveMutation.mutate({ id: item.id, comment: commentById[item.id] })}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  {t('promotions.approve')}
                </Button>
                <Button
                  variant="destructive"
                  onClick={() => rejectMutation.mutate({ id: item.id, comment: commentById[item.id] })}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  {t('promotions.reject')}
                </Button>
              </div>
            </>
          ) : item.reviewComment ? (
            <p className="text-sm text-muted-foreground">{item.reviewComment}</p>
          ) : null}
        </Card>
        )
      })}
    </div>
  )
}

/**
 * Dashboard page for namespace promotion requests.
 */
export function PromotionsPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('promotions.title')} subtitle={t('promotions.subtitle')} />
      <Tabs defaultValue="PENDING">
        <TabsList>
          <TabsTrigger value="PENDING">{t('promotions.tabPending')}</TabsTrigger>
          <TabsTrigger value="APPROVED">{t('promotions.tabApproved')}</TabsTrigger>
          <TabsTrigger value="REJECTED">{t('promotions.tabRejected')}</TabsTrigger>
        </TabsList>
        <TabsContent value="PENDING" className="mt-6"><PromotionSection status="PENDING" /></TabsContent>
        <TabsContent value="APPROVED" className="mt-6"><PromotionSection status="APPROVED" /></TabsContent>
        <TabsContent value="REJECTED" className="mt-6"><PromotionSection status="REJECTED" /></TabsContent>
      </Tabs>
    </div>
  )
}
