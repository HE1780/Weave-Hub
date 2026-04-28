import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowUpCircle } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { useAuth } from '@/features/auth/use-auth'
import { useSubmitAgentPromotion } from '@/features/promotion/use-promotion-list'

interface PromoteAgentButtonProps {
  agentId: number
  versionId: number | null | undefined
  /** Agent version status — only PUBLISHED versions can be promoted. */
  versionStatus?: string | null
  /** True when the agent already lives in the global namespace. */
  isInGlobalNamespace: boolean
  /** Resolved global namespace id used as the promotion target. */
  globalNamespaceId: number | null | undefined
}

const ADMIN_ROLES = ['SKILL_ADMIN', 'SUPER_ADMIN']

/**
 * Sidebar entry for promoting an agent version to the global namespace.
 * Hidden when:
 *  - viewer lacks SKILL_ADMIN / SUPER_ADMIN
 *  - the source version is not PUBLISHED
 *  - the agent already lives in global
 *  - we don't have a target namespace id resolved
 *
 * Renders a single confirmation dialog before dispatching the mutation; the
 * mutation invalidates the promotion + governance query keys on success.
 */
export function PromoteAgentButton({
  agentId,
  versionId,
  versionStatus,
  isInGlobalNamespace,
  globalNamespaceId,
}: PromoteAgentButtonProps) {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const [open, setOpen] = useState(false)
  const submit = useSubmitAgentPromotion()

  const canPromote = ADMIN_ROLES.some((r) => hasRole(r))
  if (!canPromote) return null
  if (versionStatus !== 'PUBLISHED') return null
  if (isInGlobalNamespace) return null
  if (!versionId || !globalNamespaceId) return null

  const handleConfirm = async () => {
    await submit.mutateAsync({
      sourceAgentId: agentId,
      sourceAgentVersionId: versionId,
      targetNamespaceId: globalNamespaceId,
    })
  }

  return (
    <Card className="p-5 space-y-3">
      <div className="flex items-center gap-2">
        <ArrowUpCircle className="w-4 h-4 text-muted-foreground" />
        <span className="text-sm font-semibold font-heading text-foreground">
          {t('agents.promote.sectionTitle')}
        </span>
      </div>
      <p className="text-sm text-muted-foreground">{t('agents.promote.sectionDescription')}</p>
      <Button
        variant="outline"
        onClick={() => setOpen(true)}
        disabled={submit.isPending}
      >
        {submit.isPending ? t('agents.promote.submitting') : t('agents.promote.label')}
      </Button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title={t('agents.promote.confirmTitle')}
        description={t('agents.promote.confirmBody')}
        confirmText={t('agents.promote.label')}
        onConfirm={handleConfirm}
      />
    </Card>
  )
}
