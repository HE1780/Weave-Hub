// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useArchiveAgent } from './use-archive-agent'

const archiveMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentLifecycleApi: {
    archiveAgent: (...args: unknown[]) => archiveMock(...args),
  },
}))

beforeEach(() => archiveMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  return { Wrapper, invalidateSpy }
}

describe('useArchiveAgent', () => {
  it('calls api.archiveAgent and invalidates the agent caches on success', async () => {
    archiveMock.mockResolvedValueOnce({ agentId: 1, action: 'ARCHIVE', status: 'ARCHIVED' })
    const onSuccess = vi.fn()
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useArchiveAgent({ onSuccess }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ namespace: 'global', slug: 'planner', reason: 'stale workflow' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(archiveMock).toHaveBeenCalledWith('global', 'planner', 'stale workflow')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'my'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'global', 'planner'] })
  })

  it('forwards errors to onError', async () => {
    const error = new Error('boom')
    archiveMock.mockRejectedValueOnce(error)
    const onError = vi.fn()
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useArchiveAgent({ onError }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ namespace: 'global', slug: 'planner' })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalled()
    expect(onError.mock.calls[0]?.[0]).toBe(error)
  })
})
