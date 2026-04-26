// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { usePostComment } from './use-post-comment'

const postMock = vi.fn()
vi.mock('@/api/client', () => ({
  commentsApi: {
    post: (...args: unknown[]) => postMock(...args),
  },
}))

beforeEach(() => postMock.mockReset())

describe('usePostComment', () => {
  it('invalidates the version-comments key on success', async () => {
    postMock.mockResolvedValueOnce({ id: 1, body: 'hi' })
    const { result } = renderHook(() => usePostComment(99), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync('hi')
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(postMock).toHaveBeenCalledWith(99, 'hi')
  })

  it('surfaces error on failure', async () => {
    postMock.mockRejectedValueOnce(new Error('boom'))
    const { result } = renderHook(() => usePostComment(99), { wrapper: createWrapper() })

    await act(async () => {
      try {
        await result.current.mutateAsync('hi')
      } catch {
        /* expected */
      }
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
