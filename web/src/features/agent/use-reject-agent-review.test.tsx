// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useRejectAgentReview } from './use-reject-agent-review'

const rejectMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentReviewsApi: {
    reject: (...args: unknown[]) => rejectMock(...args),
  },
}))

beforeEach(() => rejectMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  return { Wrapper, invalidateSpy }
}

describe('useRejectAgentReview', () => {
  it('calls api.reject with comment and invalidates inbox', async () => {
    rejectMock.mockResolvedValueOnce({ id: 3, status: 'REJECTED' })
    const onSuccess = vi.fn()
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useRejectAgentReview({ onSuccess }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ taskId: 3, comment: 'workflow.yaml malformed' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(rejectMock).toHaveBeenCalledWith(3, 'workflow.yaml malformed')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agentReviews'] })
  })

  it('forwards errors to onError', async () => {
    const error = new Error('nope')
    rejectMock.mockRejectedValueOnce(error)
    const onError = vi.fn()
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useRejectAgentReview({ onError }), {
      wrapper: Wrapper,
    })

    result.current.mutate({ taskId: 4, comment: 'x' })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalled()
    expect(onError.mock.calls[0]?.[0]).toBe(error)
  })
})
