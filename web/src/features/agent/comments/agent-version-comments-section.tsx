import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CommentComposer } from '@/features/comments/comment-composer'
import { CommentItem } from '@/features/comments/comment-item'
import type { VersionComment } from '@/features/comments/types'
import { useAgentVersionComments } from './use-agent-version-comments'
import { usePostAgentComment } from './use-post-agent-comment'
import { useEditAgentComment } from './use-edit-agent-comment'
import { useDeleteAgentComment } from './use-delete-agent-comment'
import { useTogglePinAgentComment } from './use-toggle-pin-agent-comment'
import type { AgentVersionComment } from './types'

interface Props {
  versionId: number
  /** Whether the viewer can post a new comment. Anonymous viewers should pass false. */
  canPost: boolean
}

/**
 * Direct mirror of {@link import('@/features/comments/version-comments-section').VersionCommentsSection}.
 * Reuses the comment UI primitives (CommentItem / CommentComposer /
 * CommentMarkdownRenderer) since they only depend on the structural shape of a
 * comment row — author / body / pinned / lastEditedAt / permissions — and that
 * shape is identical across skill and agent comments.
 */
export function AgentVersionCommentsSection({ versionId, canPost }: Props) {
  const { t } = useTranslation()
  const query = useAgentVersionComments(versionId)
  const post = usePostAgentComment(versionId)
  const [editing, setEditing] = useState<AgentVersionComment | null>(null)

  const all = query.data?.pages.flatMap((p) => p.content) ?? []

  if (query.isError) {
    return <p className="text-sm text-destructive">{t('comments.error.loadFailed')}</p>
  }

  return (
    <section className="space-y-4">
      {canPost && (
        <CommentComposer
          onSubmit={async (body) => {
            await post.mutateAsync(body)
          }}
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

/**
 * Adapts an {@link AgentVersionComment} to the shape {@link CommentItem}
 * expects. The two records overlap on every field except the version-id key
 * name (skillVersionId vs agentVersionId), and the UI doesn't read that field —
 * mapping it through a dummy keeps the type system happy without forking the
 * UI component for a meaningless variation.
 */
function toCommentItemShape(c: AgentVersionComment): VersionComment {
  return {
    id: c.id,
    skillVersionId: c.agentVersionId,
    author: c.author,
    body: c.body,
    pinned: c.pinned,
    createdAt: c.createdAt,
    lastEditedAt: c.lastEditedAt,
    deleted: c.deleted,
    permissions: c.permissions,
  }
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
  comments: AgentVersionComment[]
  hasNext: boolean
  isLoadingMore: boolean
  onLoadMore: () => void
  onStartEdit: (c: AgentVersionComment) => void
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
  comment: AgentVersionComment
  onStartEdit: () => void
}) {
  const { t } = useTranslation()
  const del = useDeleteAgentComment(versionId, comment.id)
  const pin = useTogglePinAgentComment(versionId, comment.id)
  return (
    <CommentItem
      comment={toCommentItemShape(comment)}
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
  comment: AgentVersionComment
  onClose: () => void
}) {
  const edit = useEditAgentComment(versionId, comment.id)
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
