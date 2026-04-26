// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useApproveAgentReview } from './use-approve-agent-review'

const approveMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentReviewsApi: {
    approve: (...args: unknown[]) => approveMock(...args),
  },
}))

beforeEach(() => approveMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  return { Wrapper, invalidateSpy }
}

describe('useApproveAgentReview', () => {
  it('calls api.approve and invalidates the inbox cache on success', async () => {
    approveMock.mockResolvedValueOnce({ id: 1, status: 'APPROVED' })
    const onSuccess = vi.fn()
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useApproveAgentReview({ onSuccess }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ taskId: 1, comment: 'looks good' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(approveMock).toHaveBeenCalledWith(1, 'looks good')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agentReviews'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
  })

  it('forwards errors to onError', async () => {
    const error = new Error('boom')
    approveMock.mockRejectedValueOnce(error)
    const onError = vi.fn()
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useApproveAgentReview({ onError }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ taskId: 2 })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalled()
    expect(onError.mock.calls[0]?.[0]).toBe(error)
  })
})
