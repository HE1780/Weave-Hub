// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'

const myNamespacesMock = vi.fn()
vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => myNamespacesMock(),
}))

const useAgentReviewsMock = vi.fn()
vi.mock('@/features/agent/use-agent-reviews', () => ({
  useAgentReviews: (...args: unknown[]) => useAgentReviewsMock(...args),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'en' } }),
}))

afterEach(() => {
  cleanup()
  myNamespacesMock.mockReset()
  useAgentReviewsMock.mockReset()
})

import { AgentReviewsPage } from './agent-reviews'

describe('AgentReviewsPage', () => {
  it('shows the loading state while namespaces are loading', () => {
    myNamespacesMock.mockReturnValue({ data: undefined, isLoading: true })
    useAgentReviewsMock.mockReturnValue({ isLoading: true, data: undefined })

    render(<AgentReviewsPage />)
    expect(screen.getByText('agentReviews.loading')).toBeTruthy()
  })

  it('shows the no-admin-namespace state when the user has no admin role', () => {
    myNamespacesMock.mockReturnValue({
      data: [{ id: 1, slug: 'team-a', displayName: 'Team A', currentUserRole: 'MEMBER' }],
      isLoading: false,
    })
    useAgentReviewsMock.mockReturnValue({ isLoading: false, data: { items: [], totalElements: 0, totalPages: 0 } })

    render(<AgentReviewsPage />)
    expect(screen.getByText('agentReviews.noAdminNamespace')).toBeTruthy()
  })

  it('renders the queue card when the user owns or admins a namespace', () => {
    myNamespacesMock.mockReturnValue({
      data: [{ id: 7, slug: 'team-x', displayName: 'Team X', currentUserRole: 'ADMIN' }],
      isLoading: false,
    })
    useAgentReviewsMock.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { items: [], totalElements: 0, totalPages: 0 },
    })

    render(<AgentReviewsPage />)
    expect(screen.getByText('agentReviews.queueTitle')).toBeTruthy()
    expect(useAgentReviewsMock).toHaveBeenCalledWith(7, 'PENDING', 0, 20, true)
  })

  it('renders enriched agent metadata in the row when the backend hydrated it', () => {
    myNamespacesMock.mockReturnValue({
      data: [{ id: 7, slug: 'team-x', displayName: 'Team X', currentUserRole: 'ADMIN' }],
      isLoading: false,
    })
    useAgentReviewsMock.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        items: [
          {
            id: 100,
            agentVersionId: 70,
            namespaceId: 7,
            status: 'PENDING',
            submittedBy: 'alice',
            reviewedBy: null,
            reviewComment: null,
            submittedAt: '2026-04-27T00:00:00Z',
            reviewedAt: null,
            agentSlug: 'agent-a',
            agentDisplayName: 'Agent A',
            agentNamespace: 'team-x',
            agentVersion: '1.2.0',
          },
        ],
        totalElements: 1,
        totalPages: 1,
      },
    })

    render(<AgentReviewsPage />)
    expect(screen.getByText('Agent A')).toBeTruthy()
    expect(screen.getByText('team-x/agent-a')).toBeTruthy()
    expect(screen.getByText('1.2.0')).toBeTruthy()
  })

  it('falls back to dashes and version-id when backend hydration is missing', () => {
    myNamespacesMock.mockReturnValue({
      data: [{ id: 7, slug: 'team-x', displayName: 'Team X', currentUserRole: 'ADMIN' }],
      isLoading: false,
    })
    useAgentReviewsMock.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        items: [
          {
            id: 101,
            agentVersionId: 71,
            namespaceId: 7,
            status: 'PENDING',
            submittedBy: 'bob',
            reviewedBy: null,
            reviewComment: null,
            submittedAt: '2026-04-27T00:00:00Z',
            reviewedAt: null,
          },
        ],
        totalElements: 1,
        totalPages: 1,
      },
    })

    render(<AgentReviewsPage />)
    expect(screen.getByText('#71')).toBeTruthy()
  })
})
