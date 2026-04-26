// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { useEditComment } from './use-edit-comment'

const editMock = vi.fn()
vi.mock('@/api/client', () => ({
  commentsApi: {
    edit: (...args: unknown[]) => editMock(...args),
  },
}))

beforeEach(() => editMock.mockReset())

describe('useEditComment', () => {
  it('invalidates the version-comments key on success', async () => {
    editMock.mockResolvedValueOnce({ id: 1, body: 'new body' })
    const { result } = renderHook(() => useEditComment(99, 1), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync('new body')
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(editMock).toHaveBeenCalledWith(1, 'new body')
  })

  it('surfaces error on failure', async () => {
    editMock.mockRejectedValueOnce(new Error('boom'))
    const { result } = renderHook(() => useEditComment(99, 1), { wrapper: createWrapper() })

    await act(async () => {
      try {
        await result.current.mutateAsync('x')
      } catch {
        /* expected */
      }
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
