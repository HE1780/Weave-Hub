/**
 * Agent-version comment shapes. Mirror of features/comments/types but with
 * agentVersionId in place of skillVersionId. The author + permissions sub-records
 * intentionally have the same shape so consumers can reuse rendering primitives
 * across both comment types.
 */
import type { CommentAuthor, CommentPermissions } from '@/features/comments/types'

export type { CommentAuthor, CommentPermissions }

export interface AgentVersionComment {
  id: number
  agentVersionId: number
  author: CommentAuthor
  body: string
  pinned: boolean
  createdAt: string
  lastEditedAt: string | null
  deleted: boolean
  permissions: CommentPermissions
}

export interface AgentVersionCommentsPage {
  page: number
  size: number
  totalElements: number
  hasNext: boolean
  content: AgentVersionComment[]
}
