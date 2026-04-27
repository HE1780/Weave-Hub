// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapperWithClient } from '@/shared/test/create-wrapper'
import { useAgentStar, useToggleAgentStar } from './use-agent-star'

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

describe('useAgentStar (read)', () => {
  it('returns starred=true when the API responds with true', async () => {
    fetchJsonMock.mockResolvedValueOnce(true)
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useAgentStar('global', 'planner'), { wrapper: Wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual({ starred: true })
    expect(fetchJsonMock).toHaveBeenCalledWith('/api/web/agents/global/planner/star')
  })

  it('falls back to starred=false on 401 instead of surfacing the error', async () => {
    const { ApiError } = await import('@/shared/lib/api-error')
    fetchJsonMock.mockRejectedValueOnce(new ApiError('unauthenticated', 401))
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useAgentStar('global', 'planner'), { wrapper: Wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual({ starred: false })
    expect(result.current.isError).toBe(false)
  })

  it('surfaces non-401 errors instead of swallowing them', async () => {
    const { ApiError } = await import('@/shared/lib/api-error')
    fetchJsonMock.mockRejectedValueOnce(new ApiError('boom', 500))
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useAgentStar('global', 'planner'), { wrapper: Wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
  })

  it('strips a leading @ from the namespace before building the URL', async () => {
    fetchJsonMock.mockResolvedValueOnce(false)
    const { Wrapper } = makeWrapper()

    renderHook(() => useAgentStar('@global', 'planner'), { wrapper: Wrapper })

    await waitFor(() => expect(fetchJsonMock).toHaveBeenCalled())
    expect(fetchJsonMock).toHaveBeenCalledWith('/api/web/agents/global/planner/star')
  })

  it('skips the request when enabled=false', async () => {
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useAgentStar('global', 'planner', false), {
      wrapper: Wrapper,
    })

    // Give react-query a tick to confirm it didn't fire.
    await new Promise((resolve) => setTimeout(resolve, 10))
    expect(fetchJsonMock).not.toHaveBeenCalled()
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('useToggleAgentStar (mutation)', () => {
  it('PUTs to add a star and invalidates both the per-agent and the agent-list queries', async () => {
    fetchJsonMock.mockResolvedValueOnce(undefined)
    const { Wrapper, invalidateSpy } = makeWrapper()

    const { result } = renderHook(() => useToggleAgentStar('global', 'planner'), {
      wrapper: Wrapper,
    })

    result.current.mutate(false /* not starred yet → PUT */)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(fetchJsonMock).toHaveBeenCalledWith(
      '/api/web/agents/global/planner/star',
      expect.objectContaining({ method: 'PUT' }),
    )
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['agents', 'global', 'planner', 'star'],
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['agents'] })
  })

  it('DELETEs to remove a star when already starred', async () => {
    fetchJsonMock.mockResolvedValueOnce(undefined)
    const { Wrapper } = makeWrapper()

    const { result } = renderHook(() => useToggleAgentStar('global', 'planner'), {
      wrapper: Wrapper,
    })

    result.current.mutate(true /* currently starred → DELETE */)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(fetchJsonMock).toHaveBeenCalledWith(
      '/api/web/agents/global/planner/star',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })
})
