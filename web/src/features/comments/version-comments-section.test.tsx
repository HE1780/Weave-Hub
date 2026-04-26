// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { VersionCommentsSection } from './version-comments-section'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

vi.mock('./use-version-comments', () => ({
  useVersionComments: () => ({
    data: { pages: [{ content: [], hasNext: false, page: 0, size: 20, totalElements: 0 }] },
    isSuccess: true,
    isLoading: false,
    isError: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  }),
}))

vi.mock('./use-post-comment', () => ({
  usePostComment: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('./use-edit-comment', () => ({
  useEditComment: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('./use-delete-comment', () => ({
  useDeleteComment: () => ({ mutate: vi.fn(), isPending: false }),
}))

vi.mock('./use-toggle-pin-comment', () => ({
  useTogglePinComment: () => ({ mutate: vi.fn(), isPending: false }),
}))

afterEach(() => cleanup())

describe('VersionCommentsSection', () => {
  it('renders empty state when there are no comments', () => {
    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <VersionCommentsSection versionId={99} canPost={true} />
      </Wrapper>,
    )
    expect(screen.getByText('comments.empty')).toBeTruthy()
  })

  it('hides composer when canPost is false', () => {
    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <VersionCommentsSection versionId={99} canPost={false} />
      </Wrapper>,
    )
    // The composer's submit button uses comments.composer.submit; absent when no composer.
    expect(screen.queryByText('comments.composer.submit')).toBeNull()
  })

  it('shows composer when canPost is true', () => {
    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <VersionCommentsSection versionId={99} canPost={true} />
      </Wrapper>,
    )
    expect(screen.getByText('comments.composer.submit')).toBeTruthy()
  })
})
