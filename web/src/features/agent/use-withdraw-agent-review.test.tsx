// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useWithdrawAgentReview } from './use-withdraw-agent-review'

const withdrawMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentLifecycleApi: {
    withdrawAgentReview: (...args: unknown[]) => withdrawMock(...args),
  },
}))

beforeEach(() => withdrawMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  return { Wrapper, invalidateSpy }
}

describe('useWithdrawAgentReview', () => {
  it('calls api.withdrawAgentReview and invalidates the agent + review caches on success', async () => {
    withdrawMock.mockResolvedValueOnce({
      agentId: 7, versionId: 20, version: '1.0.0', action: 'WITHDRAW_REVIEW', status: 'DRAFT',
    })
    const onSuccess = vi.fn()
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useWithdrawAgentReview({ onSuccess }), { wrapper: Wrapper })
    result.current.mutate({ namespace: 'global', slug: 'planner', version: '1.0.0' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(withdrawMock).toHaveBeenCalledWith('global', 'planner', '1.0.0')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents', 'global', 'planner'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agentReviews'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
  })

  it('forwards errors to onError', async () => {
    const error = new Error('not pending')
    withdrawMock.mockRejectedValueOnce(error)
    const onError = vi.fn()
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useWithdrawAgentReview({ onError }), { wrapper: Wrapper })
    result.current.mutate({ namespace: 'global', slug: 'planner', version: '1.0.0' })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalled()
    expect(onError.mock.calls[0]?.[0]).toBe(error)
  })
})
