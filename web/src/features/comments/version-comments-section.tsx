import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CommentComposer } from './comment-composer'
import { CommentItem } from './comment-item'
import { useVersionComments } from './use-version-comments'
import { usePostComment } from './use-post-comment'
import { useEditComment } from './use-edit-comment'
import { useDeleteComment } from './use-delete-comment'
import { useTogglePinComment } from './use-toggle-pin-comment'
import type { VersionComment } from './types'

interface Props {
  versionId: number
  canPost: boolean
}

export function VersionCommentsSection({ versionId, canPost }: Props) {
  const { t } = useTranslation()
  const query = useVersionComments(versionId)
  const post = usePostComment(versionId)
  const [editing, setEditing] = useState<VersionComment | null>(null)

  const all = query.data?.pages.flatMap((p) => p.content) ?? []

  if (query.isError) {
    return <p className="text-sm text-destructive">{t('comments.error.loadFailed')}</p>
  }

  return (
    <section className="space-y-4">
      {canPost && (
        <CommentComposer
          onSubmit={(body) => post.mutateAsync(body)}
          isSubmitting={post.isPending}
        />
      )}
      {all.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('comments.empty')}</p>
      ) : (
        <CommentListWithMutations
          versionId={versionId}
          comments={all}
          hasNext={!!query.hasNextPage}
          isLoadingMore={query.isFetchingNextPage}
          onLoadMore={() => query.fetchNextPage()}
          onStartEdit={setEditing}
        />
      )}
      {editing && (
        <EditDialog
          versionId={versionId}
          comment={editing}
          onClose={() => setEditing(null)}
        />
      )}
    </section>
  )
}

function CommentListWithMutations({
  versionId,
  comments,
  hasNext,
  isLoadingMore,
  onLoadMore,
  onStartEdit,
}: {
  versionId: number
  comments: VersionComment[]
  hasNext: boolean
  isLoadingMore: boolean
  onLoadMore: () => void
  onStartEdit: (c: VersionComment) => void
}) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      {comments.map((c) => (
        <CommentItemWithMutations
          key={c.id}
          versionId={versionId}
          comment={c}
          onStartEdit={() => onStartEdit(c)}
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

function CommentItemWithMutations({
  versionId,
  comment,
  onStartEdit,
}: {
  versionId: number
  comment: VersionComment
  onStartEdit: () => void
}) {
  const { t } = useTranslation()
  const del = useDeleteComment(versionId, comment.id)
  const pin = useTogglePinComment(versionId, comment.id)
  return (
    <CommentItem
      comment={comment}
      onEdit={onStartEdit}
      onDelete={() => {
        if (typeof window !== 'undefined' && window.confirm(t('comments.confirm.delete'))) {
          del.mutate()
        }
      }}
      onTogglePin={() => pin.mutate(!comment.pinned)}
    />
  )
}

function EditDialog({
  versionId,
  comment,
  onClose,
}: {
  versionId: number
  comment: VersionComment
  onClose: () => void
}) {
  const edit = useEditComment(versionId, comment.id)
  return (
    <div className="rounded border bg-card p-4">
      <CommentComposer
        initialValue={comment.body}
        isSubmitting={edit.isPending}
        onSubmit={async (body) => {
          await edit.mutateAsync(body)
          onClose()
        }}
      />
      <button
        type="button"
        onClick={onClose}
        className="mt-2 text-xs text-muted-foreground hover:text-foreground"
      >
        cancel
      </button>
    </div>
  )
}
