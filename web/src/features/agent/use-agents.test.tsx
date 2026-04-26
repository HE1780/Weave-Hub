// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAgents } from './use-agents'

const listMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentsApi: {
    list: (...args: unknown[]) => listMock(...args),
  },
}))

beforeEach(() => listMock.mockReset())

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

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

    const { result } = renderHook(() => useAgents(), { wrapper })
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data).toHaveLength(1)
    expect(result.current.data?.[0]).toEqual({ name: 'agent-a', description: 'description-a' })
  })

  it('returns empty list when backend has no agents', async () => {
    listMock.mockResolvedValueOnce({ items: [], total: 0, page: 0, size: 50 })

    const { result } = renderHook(() => useAgents(), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toEqual([])
  })
})
