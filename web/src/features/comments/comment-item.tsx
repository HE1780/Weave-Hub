import { useTranslation } from 'react-i18next'
import { MoreHorizontal } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from '@/shared/ui/dropdown-menu'
import { CommentMarkdownRenderer } from './comment-markdown-renderer'
import type { VersionComment } from './types'

interface Props {
  comment: VersionComment
  onEdit: () => void
  onDelete: () => void
  onTogglePin: () => void
}

export function CommentItem({ comment, onEdit, onDelete, onTogglePin }: Props) {
  const { t } = useTranslation()
  const { permissions: p } = comment
  const showMenu = p.canEdit || p.canDelete || p.canPin

  return (
    <article className="rounded-lg border bg-card p-4">
      <header className="flex items-start justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          {comment.author.avatarUrl ? (
            <img
              src={comment.author.avatarUrl}
              alt=""
              className="h-6 w-6 rounded-full object-cover"
            />
          ) : (
            <div className="h-6 w-6 rounded-full bg-muted" />
          )}
          <span className="text-sm font-medium">{comment.author.displayName}</span>
          <time className="text-xs text-muted-foreground" dateTime={comment.createdAt}>
            {new Date(comment.createdAt).toLocaleString()}
          </time>
          {comment.pinned && (
            <span className="rounded bg-primary/10 px-2 py-0.5 text-xs text-primary">
              {t('comments.badge.pinned')}
            </span>
          )}
          {comment.lastEditedAt && (
            <span className="text-xs text-muted-foreground">
              {t('comments.badge.edited')}
            </span>
          )}
        </div>
        {showMenu && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                aria-label="actions"
                className="rounded p-1 text-muted-foreground hover:bg-muted"
              >
                <MoreHorizontal className="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {p.canEdit && (
                <DropdownMenuItem onSelect={onEdit}>
                  {t('comments.action.edit')}
                </DropdownMenuItem>
              )}
              {p.canDelete && (
                <DropdownMenuItem onSelect={onDelete}>
                  {t('comments.action.delete')}
                </DropdownMenuItem>
              )}
              {p.canPin && (
                <DropdownMenuItem onSelect={onTogglePin}>
                  {comment.pinned ? t('comments.action.unpin') : t('comments.action.pin')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </header>
      <div className="mt-3">
        <CommentMarkdownRenderer content={comment.body} />
      </div>
    </article>
  )
}
