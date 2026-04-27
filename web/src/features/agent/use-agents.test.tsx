// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { useAgents } from './use-agents'

const listMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentsApi: {
    list: (...args: unknown[]) => listMock(...args),
  },
}))

beforeEach(() => listMock.mockReset())

describe('useAgents', () => {
  it('maps backend AgentDto rows into AgentSummary', async () => {
    listMock.mockResolvedValueOnce({
      items: [
        {
          id: 7,
          namespace: 'global',
          slug: 'agent-a',
          displayName: 'Agent A',
          description: 'description-a',
          visibility: 'PUBLIC',
          ownerId: 'owner-1',
          status: 'ACTIVE',
          createdAt: '2026-04-26T00:00:00Z',
          updatedAt: '2026-04-26T00:00:00Z',
        },
      ],
      total: 1,
      page: 0,
      size: 50,
    })

    const { result } = renderHook(() => useAgents(), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data).toHaveLength(1)
    expect(result.current.data?.[0]).toEqual({
      name: 'agent-a',
      description: 'description-a',
      namespace: 'global',
    })
  })

  it('returns empty list when backend has no agents', async () => {
    listMock.mockResolvedValueOnce({ items: [], total: 0, page: 0, size: 50 })

    const { result } = renderHook(() => useAgents(), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toEqual([])
  })

  it('forwards q/namespace/visibility to agentsApi.list', async () => {
    listMock.mockResolvedValueOnce({ items: [], total: 0, page: 0, size: 50 })

    const { result } = renderHook(
      () => useAgents({ q: 'hello', namespace: 'team-x', visibility: 'PRIVATE' }),
      { wrapper: createWrapper() },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(listMock).toHaveBeenCalledWith({
      page: 0,
      size: 50,
      q: 'hello',
      namespace: 'team-x',
      visibility: 'PRIVATE',
    })
  })

  it('uses different cache keys for different params', async () => {
    listMock.mockResolvedValue({ items: [], total: 0, page: 0, size: 50 })
    const wrapper = createWrapper()

    const { result: first } = renderHook(() => useAgents({ q: 'foo' }), { wrapper })
    await waitFor(() => expect(first.current.isSuccess).toBe(true))
    const { result: second } = renderHook(() => useAgents({ q: 'bar' }), { wrapper })
    await waitFor(() => expect(second.current.isSuccess).toBe(true))

    // Two distinct fetches because the queryKey hashes the params object.
    expect(listMock).toHaveBeenCalledTimes(2)
    expect(listMock.mock.calls[0]?.[0]).toMatchObject({ q: 'foo' })
    expect(listMock.mock.calls[1]?.[0]).toMatchObject({ q: 'bar' })
  })
})
