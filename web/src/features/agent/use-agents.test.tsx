// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAgents } from './use-agents'
import { MOCK_AGENTS } from './mock-agents'

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

describe('useAgents', () => {
  it('returns the mock agent list as AgentSummary records', async () => {
    const { result } = renderHook(() => useAgents(), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data).toHaveLength(MOCK_AGENTS.length)
    expect(result.current.data?.[0]).toEqual(
      expect.objectContaining({
        name: MOCK_AGENTS[0].name,
        description: MOCK_AGENTS[0].description,
      }),
    )
  })
})
