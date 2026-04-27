// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useRereleaseAgentVersion } from './use-rerelease-agent-version'

const rereleaseMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentLifecycleApi: {
    rereleaseAgentVersion: (...args: unknown[]) => rereleaseMock(...args),
  },
}))

beforeEach(() => rereleaseMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  return { Wrapper, invalidateSpy }
}

describe('useRereleaseAgentVersion', () => {
  it('calls api.rereleaseAgentVersion with the target version and invalidates caches', async () => {
    rereleaseMock.mockResolvedValueOnce({
      agentId: 7, versionId: 21, version: '1.1.0', action: 'RERELEASE', status: 'PENDING_REVIEW',
    })
    const onSuccess = vi.fn()
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useRereleaseAgentVersion({ onSuccess }), { wrapper: Wrapper })
    result.current.mutate({
      namespace: 'global',
      slug: 'planner',
      version: '1.0.0',
      targetVersion: '1.1.0',
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(rereleaseMock).toHaveBeenCalledWith('global', 'planner', '1.0.0', '1.1.0')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'global', 'planner'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agentReviews'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
  })

  it('forwards errors to onError', async () => {
    const error = new Error('target exists')
    rereleaseMock.mockRejectedValueOnce(error)
    const onError = vi.fn()
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useRereleaseAgentVersion({ onError }), { wrapper: Wrapper })
    result.current.mutate({
      namespace: 'global',
      slug: 'planner',
      version: '1.0.0',
      targetVersion: '1.0.0',
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalled()
    expect(onError.mock.calls[0]?.[0]).toBe(error)
  })
})
