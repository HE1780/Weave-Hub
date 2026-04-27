import { useTranslation } from 'react-i18next'
import { Bookmark } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { useAuth } from '@/features/auth/use-auth'
import { useAgentStar, useToggleAgentStar } from './use-agent-star'

interface AgentStarButtonProps {
  namespace: string
  slug: string
  starCount: number
  onRequireLogin?: () => void
}

/**
 * Toggles the current user's star state for an agent. Mirrors
 * features/social/star-button — same disabled/loading semantics, same i18n
 * fallback to starButton.* keys (shared vocabulary across skill + agent).
 */
export function AgentStarButton({
  namespace,
  slug,
  starCount,
  onRequireLogin,
}: AgentStarButtonProps) {
  const { t } = useTranslation()
  const { data: starStatus, isLoading } = useAgentStar(namespace, slug)
  const toggleMutation = useToggleAgentStar(namespace, slug)
  const { isAuthenticated } = useAuth()

  const handleToggle = () => {
    if (!isAuthenticated) {
      onRequireLogin?.()
      return
    }
    if (starStatus) {
      toggleMutation.mutate(starStatus.starred)
    }
  }

  if (isLoading || !starStatus) {
    return null
  }

  return (
    <Button
      variant={starStatus.starred ? 'default' : 'outline'}
      size="sm"
      className="justify-between"
      onClick={handleToggle}
      disabled={toggleMutation.isPending}
    >
      <Bookmark className={`w-4 h-4 mr-2 ${starStatus.starred ? 'fill-current' : ''}`} />
      {starStatus.starred ? t('starButton.starred') : t('starButton.star')} ({starCount})
    </Button>
  )
}
