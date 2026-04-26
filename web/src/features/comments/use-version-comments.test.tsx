// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { useVersionComments } from './use-version-comments'
import type { VersionCommentsPage } from './types'

const listMock = vi.fn()
vi.mock('@/api/client', () => ({
  commentsApi: {
    list: (...args: unknown[]) => listMock(...args),
  },
}))

beforeEach(() => {
  listMock.mockReset()
})

describe('useVersionComments', () => {
  it('fetches the first page on mount', async () => {
    const page: VersionCommentsPage = {
      page: 0,
      size: 20,
      totalElements: 1,
      hasNext: false,
      content: [
        {
          id: 1,
          skillVersionId: 99,
          author: { userId: 'alice', displayName: 'Alice', avatarUrl: null },
          body: 'hi',
          pinned: false,
          createdAt: '2026-04-26T00:00:00Z',
          lastEditedAt: null,
          deleted: false,
          permissions: { canEdit: true, canDelete: true, canPin: false },
        },
      ],
    }
    listMock.mockResolvedValueOnce(page)

    const { result } = renderHook(() => useVersionComments(99), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.pages[0].content[0].body).toBe('hi')
  })

  it('exposes hasNextPage from API hasNext flag', async () => {
    listMock.mockResolvedValueOnce({
      page: 0,
      size: 20,
      totalElements: 100,
      hasNext: true,
      content: [],
    })
    const { result } = renderHook(() => useVersionComments(99), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(true)
  })

  it('surfaces error on fetch failure', async () => {
    listMock.mockRejectedValueOnce(new Error('network'))
    const { result } = renderHook(() => useVersionComments(99), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
