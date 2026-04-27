// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useUnarchiveAgent } from './use-unarchive-agent'

const unarchiveMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentLifecycleApi: {
    unarchiveAgent: (...args: unknown[]) => unarchiveMock(...args),
  },
}))

beforeEach(() => unarchiveMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  return { Wrapper, invalidateSpy }
}

describe('useUnarchiveAgent', () => {
  it('calls api.unarchiveAgent and invalidates the agent caches on success', async () => {
    unarchiveMock.mockResolvedValueOnce({ agentId: 1, action: 'UNARCHIVE', status: 'ACTIVE' })
    const onSuccess = vi.fn()
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useUnarchiveAgent({ onSuccess }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ namespace: 'global', slug: 'planner' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(unarchiveMock).toHaveBeenCalledWith('global', 'planner')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'my'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'global', 'planner'] })
  })

  it('forwards errors to onError', async () => {
    const error = new Error('forbidden')
    unarchiveMock.mockRejectedValueOnce(error)
    const onError = vi.fn()
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useUnarchiveAgent({ onError }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ namespace: 'global', slug: 'planner' })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalled()
    expect(onError.mock.calls[0]?.[0]).toBe(error)
  })
})
