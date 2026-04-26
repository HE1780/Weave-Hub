// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { useTogglePinComment } from './use-toggle-pin-comment'

const togglePinMock = vi.fn()
vi.mock('@/api/client', () => ({
  commentsApi: {
    togglePin: (...args: unknown[]) => togglePinMock(...args),
  },
}))

beforeEach(() => togglePinMock.mockReset())

describe('useTogglePinComment', () => {
  it('invalidates the version-comments key on success', async () => {
    togglePinMock.mockResolvedValueOnce({ id: 1, pinned: true })
    const { result } = renderHook(() => useTogglePinComment(99, 1), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync(true)
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(togglePinMock).toHaveBeenCalledWith(1, true)
  })

  it('surfaces error on failure', async () => {
    togglePinMock.mockRejectedValueOnce(new Error('boom'))
    const { result } = renderHook(() => useTogglePinComment(99, 1), { wrapper: createWrapper() })

    await act(async () => {
      try {
        await result.current.mutateAsync(false)
      } catch {
        /* expected */
      }
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
