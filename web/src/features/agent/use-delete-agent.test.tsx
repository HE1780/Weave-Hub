// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useDeleteAgent } from './use-delete-agent'

const deleteMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentLifecycleApi: {
    deleteAgent: (...args: unknown[]) => deleteMock(...args),
  },
}))

beforeEach(() => deleteMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  const removeSpy = vi.spyOn(client, 'removeQueries')
  return { Wrapper, invalidateSpy, removeSpy }
}

describe('useDeleteAgent', () => {
  it('calls api.deleteAgent and clears + invalidates the agent caches on success', async () => {
    deleteMock.mockResolvedValueOnce({ agentId: 7, namespace: 'global', slug: 'planner', deleted: true })
    const onSuccess = vi.fn()
    const { Wrapper, invalidateSpy, removeSpy } = makeWrapper()

    const { result } = renderHook(() => useDeleteAgent({ onSuccess }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ namespace: 'global', slug: 'planner' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(deleteMock).toHaveBeenCalledWith('global', 'planner')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(removeSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'global', 'planner'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'my'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
  })

  it('forwards errors to onError', async () => {
    const error = new Error('forbidden')
    deleteMock.mockRejectedValueOnce(error)
    const onError = vi.fn()
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useDeleteAgent({ onError }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ namespace: 'global', slug: 'planner' })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalled()
    expect(onError.mock.calls[0]?.[0]).toBe(error)
  })
})
