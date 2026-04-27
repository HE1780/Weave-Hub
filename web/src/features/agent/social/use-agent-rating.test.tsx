// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useAgentUserRating, useRateAgent } from './use-agent-rating'

const fetchJsonMock = vi.fn()

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return {
    ...actual,
    fetchJson: (...args: unknown[]) => fetchJsonMock(...args),
  }
})

beforeEach(() => fetchJsonMock.mockReset())

function makeWrapper() {
  const { Wrapper, client } = createWrapperWithClient()
  const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
  return { Wrapper, invalidateSpy }
}

describe('useAgentUserRating (read)', () => {
  it('returns the API rating payload on success', async () => {
    fetchJsonMock.mockResolvedValueOnce({ score: 4, rated: true })
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useAgentUserRating('global', 'planner'), {
      wrapper: Wrapper,
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual({ score: 4, rated: true })
    expect(fetchJsonMock).toHaveBeenCalledWith('/api/web/agents/global/planner/rating')
  })

  it('falls back to {score:0, rated:false} on 401 instead of erroring', async () => {
    const { ApiError } = await import('@/shared/lib/api-error')
    fetchJsonMock.mockRejectedValueOnce(new ApiError('unauthenticated', 401))
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useAgentUserRating('global', 'planner'), {
      wrapper: Wrapper,
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual({ score: 0, rated: false })
    expect(result.current.isError).toBe(false)
  })

  it('surfaces non-401 errors instead of swallowing them', async () => {
    const { ApiError } = await import('@/shared/lib/api-error')
    fetchJsonMock.mockRejectedValueOnce(new ApiError('boom', 500))
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useAgentUserRating('global', 'planner'), {
      wrapper: Wrapper,
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('strips a leading @ from the namespace before building the URL', async () => {
    fetchJsonMock.mockResolvedValueOnce({ score: 0, rated: false })
    const { Wrapper } = makeWrapper()

    renderHook(() => useAgentUserRating('@global', 'planner'), { wrapper: Wrapper })

    await waitFor(() => expect(fetchJsonMock).toHaveBeenCalled())
    expect(fetchJsonMock).toHaveBeenCalledWith('/api/web/agents/global/planner/rating')
  })
})

describe('useRateAgent (mutation)', () => {
  it('PUTs the score in JSON body and invalidates both the rating and agent-list queries', async () => {
    fetchJsonMock.mockResolvedValueOnce(undefined)
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useRateAgent('global', 'planner'), { wrapper: Wrapper })

    result.current.mutate(4)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const [url, init] = fetchJsonMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/web/agents/global/planner/rating')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify({ score: 4 }))

    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['agents', 'global', 'planner', 'rating'],
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
  })
})
