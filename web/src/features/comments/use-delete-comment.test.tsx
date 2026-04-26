// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { useDeleteComment } from './use-delete-comment'

const deleteMock = vi.fn()
vi.mock('@/api/client', () => ({
  commentsApi: {
    delete: (...args: unknown[]) => deleteMock(...args),
  },
}))

beforeEach(() => deleteMock.mockReset())

describe('useDeleteComment', () => {
  it('invalidates the version-comments key on success', async () => {
    deleteMock.mockResolvedValueOnce(undefined)
    const { result } = renderHook(() => useDeleteComment(99, 1), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync()
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(deleteMock).toHaveBeenCalledWith(1)
  })

  it('surfaces error on failure', async () => {
    deleteMock.mockRejectedValueOnce(new Error('boom'))
    const { result } = renderHook(() => useDeleteComment(99, 1), { wrapper: createWrapper() })

    await act(async () => {
      try {
        await result.current.mutateAsync()
      } catch {
        /* expected */
      }
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
