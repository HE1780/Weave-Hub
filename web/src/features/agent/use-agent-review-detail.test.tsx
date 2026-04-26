// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAgentReviewDetail } from './use-agent-review-detail'

const getDetailMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentReviewsApi: {
    getDetail: (...args: unknown[]) => getDetailMock(...args),
  },
}))

beforeEach(() => getDetailMock.mockReset())

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

describe('useAgentReviewDetail', () => {
  it('fetches the full review payload for a valid task id', async () => {
    getDetailMock.mockResolvedValueOnce({
      task: { id: 1, agentVersionId: 5, status: 'PENDING' },
      agent: { id: 7, slug: 'demo', namespace: 'global' },
      version: { id: 5, version: '1.0.0', soulMd: 'soul', workflowYaml: 'steps: []' },
    })

    const { result } = renderHook(() => useAgentReviewDetail(1), { wrapper })
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(getDetailMock).toHaveBeenCalledWith(1)
    expect(result.current.data?.agent.slug).toBe('demo')
    expect(result.current.data?.version.version).toBe('1.0.0')
  })

  it('does not fetch when taskId is 0', () => {
    renderHook(() => useAgentReviewDetail(0), { wrapper })
    expect(getDetailMock).not.toHaveBeenCalled()
  })
})
