// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { CommentList } from './comment-list'
import type { VersionComment } from './types'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

afterEach(() => cleanup())

const make = (id: number, pinned: boolean, body: string): VersionComment => ({
  id,
  skillVersionId: 99,
  author: { userId: 'u', displayName: 'U', avatarUrl: null },
  body,
  pinned,
  createdAt: '2026-04-26T00:00:00Z',
  lastEditedAt: null,
  deleted: false,
  permissions: { canEdit: false, canDelete: false, canPin: false },
})

const noopHandlers = {
  onEdit: vi.fn(),
  onDelete: vi.fn(),
  onTogglePin: vi.fn(),
  onLoadMore: vi.fn(),
}

describe('CommentList', () => {
  it('renders comments in given order (parent enforces ordering)', () => {
    render(
      <CommentList
        comments={[make(1, true, 'pinned-one'), make(2, false, 'plain-one')]}
        hasNext={false}
        isLoadingMore={false}
        {...noopHandlers}
      />,
    )
    const articles = screen.getAllByRole('article')
    expect(articles[0].textContent).toContain('pinned-one')
    expect(articles[1].textContent).toContain('plain-one')
  })

  it('shows Load More button when hasNext', () => {
    render(
      <CommentList
        comments={[make(1, false, 'a')]}
        hasNext={true}
        isLoadingMore={false}
        {...noopHandlers}
      />,
    )
    expect(screen.getByText('comments.loadMore')).toBeTruthy()
  })

  it('hides Load More when no next page', () => {
    render(
      <CommentList
        comments={[make(1, false, 'a')]}
        hasNext={false}
        isLoadingMore={false}
        {...noopHandlers}
      />,
    )
    expect(screen.queryByText('comments.loadMore')).toBeNull()
  })

  it('disables Load More while isLoadingMore is true', () => {
    render(
      <CommentList
        comments={[make(1, false, 'a')]}
        hasNext={true}
        isLoadingMore={true}
        {...noopHandlers}
      />,
    )
    const button = screen.getByText('comments.loadMore') as HTMLButtonElement
    expect(button.disabled).toBe(true)
  })
})
