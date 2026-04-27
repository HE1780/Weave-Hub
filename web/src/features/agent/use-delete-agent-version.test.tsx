// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useDeleteAgentVersion } from './use-delete-agent-version'

const deleteVersionMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentLifecycleApi: {
    deleteAgentVersion: (...args: unknown[]) => deleteVersionMock(...args),
  },
}))

beforeEach(() => deleteVersionMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  return { Wrapper, invalidateSpy }
}

describe('useDeleteAgentVersion', () => {
  it('calls api.deleteAgentVersion and invalidates the agent caches on success', async () => {
    deleteVersionMock.mockResolvedValueOnce({
      agentId: 7, versionId: 20, version: '1.0.0', action: 'DELETE_VERSION', status: 'DRAFT',
    })
    const onSuccess = vi.fn()
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useDeleteAgentVersion({ onSuccess }), { wrapper: Wrapper })
    result.current.mutate({ namespace: 'global', slug: 'planner', version: '1.0.0' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(deleteVersionMock).toHaveBeenCalledWith('global', 'planner', '1.0.0')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'global', 'planner'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
  })

  it('forwards errors to onError', async () => {
    const error = new Error('published not deletable')
    deleteVersionMock.mockRejectedValueOnce(error)
    const onError = vi.fn()
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useDeleteAgentVersion({ onError }), { wrapper: Wrapper })
    result.current.mutate({ namespace: 'global', slug: 'planner', version: '1.0.0' })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalled()
    expect(onError.mock.calls[0]?.[0]).toBe(error)
  })
})
