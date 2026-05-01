import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'

type AgentVersionStatus =
  | 'DRAFT'
  | 'SCANNING'
  | 'SCAN_FAILED'
  | 'UPLOADED'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'ARCHIVED'
  | 'YANKED'

const statusStyles: Record<AgentVersionStatus, string> = {
  PUBLISHED:
    'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400',
  UPLOADED:
    'border-blue-500/30 bg-blue-500/10 text-blue-700 dark:text-blue-400',
  PENDING_REVIEW:
    'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400',
  REJECTED:
    'border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-400',
  SCANNING:
    'border-purple-500/30 bg-purple-500/10 text-purple-700 dark:text-purple-400',
  SCAN_FAILED:
    'border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-400',
  YANKED:
    'border-border/60 bg-secondary/40 text-muted-foreground line-through',
  ARCHIVED:
    'border-border/60 bg-secondary/40 text-muted-foreground',
  DRAFT:
    'border-border/60 bg-secondary/40 text-muted-foreground',
}

const i18nKeys: Record<AgentVersionStatus, string> = {
  DRAFT: 'agents.versionStatus.draft',
  SCANNING: 'agents.versionStatus.scanning',
  SCAN_FAILED: 'agents.versionStatus.scanFailed',
  UPLOADED: 'agents.versionStatus.uploaded',
  PENDING_REVIEW: 'agents.versionStatus.pendingReview',
  PUBLISHED: 'agents.versionStatus.published',
  REJECTED: 'agents.versionStatus.rejected',
  ARCHIVED: 'agents.versionStatus.archived',
  YANKED: 'agents.versionStatus.yanked',
}

export function AgentVersionStatusBadge({
  status,
  className,
}: {
  status?: string
  className?: string
}) {
  const { t } = useTranslation()
  if (!status) return null

  const style = statusStyles[status as AgentVersionStatus] ?? statusStyles.DRAFT
  const label = i18nKeys[status as AgentVersionStatus]
    ? t(i18nKeys[status as AgentVersionStatus])
    : status

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium',
        style,
        className,
      )}
    >
      {label}
    </span>
  )
}
