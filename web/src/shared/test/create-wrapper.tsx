import { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

function freshClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })
}

/**
 * Test helper: wraps renderHook children in a fresh QueryClient.
 * - No retries so rejected queries surface immediately.
 * - No cache reuse between calls.
 */
export function createWrapper() {
  const client = freshClient()
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
}

/**
 * Like {@link createWrapper}, but exposes the underlying client so tests can
 * spy on cache operations (e.g. `invalidateQueries`). Use this only when the
 * test must inspect the QueryClient; otherwise prefer {@link createWrapper}.
 */
export function createWrapperWithClient() {
  const client = freshClient()
  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
  return { Wrapper, client }
}
