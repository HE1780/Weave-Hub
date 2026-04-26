import { useTranslation } from 'react-i18next'
import { CommentItem } from './comment-item'
import type { VersionComment } from './types'

interface Props {
  comments: VersionComment[]
  hasNext: boolean
  isLoadingMore: boolean
  onLoadMore: () => void
  onEdit: (c: VersionComment) => void
  onDelete: (c: VersionComment) => void
  onTogglePin: (c: VersionComment) => void
}

export function CommentList({
  comments,
  hasNext,
  isLoadingMore,
  onLoadMore,
  onEdit,
  onDelete,
  onTogglePin,
}: Props) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      {comments.map((c) => (
        <CommentItem
          key={c.id}
          comment={c}
          onEdit={() => onEdit(c)}
          onDelete={() => onDelete(c)}
          onTogglePin={() => onTogglePin(c)}
        />
      ))}
      {hasNext && (
        <button
          type="button"
          onClick={onLoadMore}
          disabled={isLoadingMore}
          className="w-full rounded border py-2 text-sm hover:bg-muted disabled:opacity-50"
        >
          {t('comments.loadMore')}
        </button>
      )}
    </div>
  )
}
