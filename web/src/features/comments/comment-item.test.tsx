// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import { CommentItem } from './comment-item'
import type { VersionComment } from './types'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

afterEach(() => cleanup())

const baseComment: VersionComment = {
  id: 1,
  skillVersionId: 99,
  author: { userId: 'u1', displayName: 'Alice', avatarUrl: null },
  body: 'Hello world',
  pinned: false,
  createdAt: '2026-04-26T00:00:00Z',
  lastEditedAt: null,
  deleted: false,
  permissions: { canEdit: false, canDelete: false, canPin: false },
}

const noopHandlers = {
  onEdit: () => undefined,
  onDelete: () => undefined,
  onTogglePin: () => undefined,
}

describe('CommentItem', () => {
  it('renders body and author', () => {
    render(<CommentItem comment={baseComment} {...noopHandlers} />)
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('Hello world')).toBeTruthy()
  })

  it('shows pinned badge when pinned', () => {
    render(<CommentItem comment={{ ...baseComment, pinned: true }} {...noopHandlers} />)
    expect(screen.getByText('comments.badge.pinned')).toBeTruthy()
  })

  it('shows edited badge when lastEditedAt is set', () => {
    render(
      <CommentItem
        comment={{ ...baseComment, lastEditedAt: '2026-04-26T01:00:00Z' }}
        {...noopHandlers}
      />,
    )
    expect(screen.getByText('comments.badge.edited')).toBeTruthy()
  })

  it('hides action menu when no permissions are granted', () => {
    render(<CommentItem comment={baseComment} {...noopHandlers} />)
    expect(screen.queryByRole('button', { name: /actions/i })).toBeNull()
  })

  it('renders the actions trigger when canEdit is true', () => {
    render(
      <CommentItem
        comment={{
          ...baseComment,
          permissions: { canEdit: true, canDelete: true, canPin: false },
        }}
        {...noopHandlers}
      />,
    )
    expect(screen.getByRole('button', { name: /actions/i })).toBeTruthy()
  })

  it('renders the actions trigger when only canPin is true', () => {
    render(
      <CommentItem
        comment={{
          ...baseComment,
          permissions: { canEdit: false, canDelete: false, canPin: true },
        }}
        {...noopHandlers}
      />,
    )
    expect(screen.getByRole('button', { name: /actions/i })).toBeTruthy()
  })

  it('does not render an avatar img when avatarUrl is null', () => {
    const { container } = render(<CommentItem comment={baseComment} {...noopHandlers} />)
    expect(container.querySelector('img')).toBeNull()
  })

  it('renders the avatar img when avatarUrl is set', () => {
    const { container } = render(
      <CommentItem
        comment={{
          ...baseComment,
          author: { ...baseComment.author, avatarUrl: 'https://e/x.png' },
        }}
        {...noopHandlers}
      />,
    )
    expect(container.querySelector('img')?.getAttribute('src')).toBe('https://e/x.png')
  })

  it('clicking the trigger does not throw', () => {
    render(
      <CommentItem
        comment={{
          ...baseComment,
          permissions: { canEdit: true, canDelete: true, canPin: true },
        }}
        {...noopHandlers}
      />,
    )
    expect(() =>
      fireEvent.click(screen.getByRole('button', { name: /actions/i })),
    ).not.toThrow()
  })
})
