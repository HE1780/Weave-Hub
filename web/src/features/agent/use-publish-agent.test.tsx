// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { usePublishAgent } from './use-publish-agent'

const publishMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentsApi: {
    publish: (...args: unknown[]) => publishMock(...args),
  },
}))

beforeEach(() => publishMock.mockReset())

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

describe('usePublishAgent', () => {
  it('forwards namespace, file, and visibility to agentsApi.publish', async () => {
    publishMock.mockResolvedValueOnce({
      agentId: 7,
      agentVersionId: 70,
      namespace: 'global',
      slug: 'agent-a',
      version: '1.0.0',
      status: 'PUBLISHED',
      packageSizeBytes: 1024,
    })
    const file = new File([new Uint8Array([1, 2, 3])], 'a.zip', { type: 'application/zip' })

    const { result } = renderHook(() => usePublishAgent(), { wrapper })
    await act(async () => {
      await result.current.mutateAsync({ namespace: 'global', file, visibility: 'PRIVATE' })
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(publishMock).toHaveBeenCalledWith('global', file, 'PRIVATE')
    expect(result.current.data?.slug).toBe('agent-a')
  })

  it('surfaces the API error', async () => {
    publishMock.mockRejectedValueOnce(new Error('Forbidden'))
    const file = new File([new Uint8Array([1])], 'b.zip')

    const { result } = renderHook(() => usePublishAgent(), { wrapper })
    await act(async () => {
      try {
        await result.current.mutateAsync({ namespace: 'global', file, visibility: 'PUBLIC' })
      } catch {
        /* expected */
      }
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
