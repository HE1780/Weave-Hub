// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { useAgentReviews } from './use-agent-reviews'

const listMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentReviewsApi: {
    list: (...args: unknown[]) => listMock(...args),
  },
}))

beforeEach(() => listMock.mockReset())

describe('useAgentReviews', () => {
  it('returns paginated review tasks for the namespace', async () => {
    listMock.mockResolvedValueOnce({
      items: [
        {
          id: 1,
          agentVersionId: 5,
          namespaceId: 10,
          status: 'PENDING',
          submittedBy: 'alice',
          reviewedBy: null,
          reviewComment: null,
          submittedAt: '2026-04-27T00:00:00Z',
          reviewedAt: null,
        },
      ],
      total: 1,
      page: 0,
      size: 20,
    })

    const { result } = renderHook(() => useAgentReviews(10, 'PENDING'), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(listMock).toHaveBeenCalledWith({
      namespaceId: 10,
      status: 'PENDING',
      page: 0,
      size: 20,
    })
    expect(result.current.data?.items).toHaveLength(1)
    expect(result.current.data?.totalElements).toBe(1)
    expect(result.current.data?.totalPages).toBe(1)
  })

  it('does not fire the request when namespaceId is missing', () => {
    renderHook(() => useAgentReviews(undefined), { wrapper: createWrapper() })
    expect(listMock).not.toHaveBeenCalled()
  })

  it('does not fire when caller passes enabled=false', () => {
    renderHook(() => useAgentReviews(10, 'PENDING', 0, 20, false), { wrapper: createWrapper() })
    expect(listMock).not.toHaveBeenCalled()
  })
})
