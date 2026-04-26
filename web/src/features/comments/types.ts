export interface CommentAuthor {
  userId: string
  displayName: string
  avatarUrl: string | null
}

export interface CommentPermissions {
  canEdit: boolean
  canDelete: boolean
  canPin: boolean
}

export interface VersionComment {
  id: number
  skillVersionId: number
  author: CommentAuthor
  body: string
  pinned: boolean
  createdAt: string
  lastEditedAt: string | null
  deleted: boolean
  permissions: CommentPermissions
}

export interface VersionCommentsPage {
  page: number
  size: number
  totalElements: number
  hasNext: boolean
  content: VersionComment[]
}
