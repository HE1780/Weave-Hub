// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAgentDetail } from './use-agent-detail'

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

describe('useAgentDetail', () => {
  it('returns the matching agent for a known name', async () => {
    const { result } = renderHook(() => useAgentDetail('customer-support-agent'), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data?.name).toBe('customer-support-agent')
    expect(result.current.data?.workflow?.steps.length).toBeGreaterThan(0)
  })

  it('errors for an unknown name', async () => {
    const { result } = renderHook(() => useAgentDetail('does-not-exist'), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(Error)
  })
})
