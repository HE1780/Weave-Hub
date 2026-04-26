// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'

const useAgentReviewDetailMock = vi.fn()
const approveMock = vi.fn()
const rejectMock = vi.fn()

vi.mock('@/features/agent/use-agent-review-detail', () => ({
  useAgentReviewDetail: (...args: unknown[]) => useAgentReviewDetailMock(...args),
}))

vi.mock('@/features/agent/use-approve-agent-review', () => ({
  useApproveAgentReview: (callbacks?: unknown) => ({
    mutate: (vars: unknown) => approveMock(vars, callbacks),
    isPending: false,
  }),
}))

vi.mock('@/features/agent/use-reject-agent-review', () => ({
  useRejectAgentReview: (callbacks?: unknown) => ({
    mutate: (vars: unknown) => rejectMock(vars, callbacks),
    isPending: false,
  }),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'en' } }),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

afterEach(() => {
  cleanup()
  useAgentReviewDetailMock.mockReset()
  approveMock.mockReset()
  rejectMock.mockReset()
})

import { AgentReviewDetailPage } from './agent-review-detail'

describe('AgentReviewDetailPage', () => {
  it('renders the loading state', () => {
    useAgentReviewDetailMock.mockReturnValue({ isLoading: true })
    render(<AgentReviewDetailPage taskId={1} />)
    expect(screen.getByText('agentReviews.detail.loading')).toBeTruthy()
  })

  it('renders the error state', () => {
    useAgentReviewDetailMock.mockReturnValue({ isLoading: false, isError: true, data: undefined })
    render(<AgentReviewDetailPage taskId={1} />)
    expect(screen.getByText('agentReviews.detail.loadError')).toBeTruthy()
  })

  it('renders the agent metadata, soul, and workflow when loaded', () => {
    useAgentReviewDetailMock.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        task: {
          id: 42,
          agentVersionId: 5,
          namespaceId: 1,
          status: 'PENDING',
          submittedBy: 'alice',
          reviewedBy: null,
          reviewComment: null,
          submittedAt: '2026-04-27T00:00:00Z',
          reviewedAt: null,
        },
        agent: {
          id: 7,
          namespace: 'global',
          slug: 'demo',
          displayName: 'Demo Agent',
          description: 'A demo',
          visibility: 'PUBLIC',
          ownerId: 'alice',
          status: 'ACTIVE',
          createdAt: '2026-04-27T00:00:00Z',
          updatedAt: '2026-04-27T00:00:00Z',
        },
        version: {
          id: 5,
          agentId: 7,
          version: '1.0.0',
          status: 'PENDING_REVIEW',
          submittedBy: 'alice',
          submittedAt: '2026-04-27T00:00:00Z',
          publishedAt: null,
          packageSizeBytes: 1024,
          manifestYaml: 'name: demo',
          soulMd: 'You are calm.',
          workflowYaml: 'steps: []',
        },
      },
    })

    render(<AgentReviewDetailPage taskId={42} />)
    expect(screen.getByText('global/demo')).toBeTruthy()
    expect(screen.getByText('Demo Agent')).toBeTruthy()
    expect(screen.getByText('You are calm.')).toBeTruthy()
    expect(screen.getByText('steps: []')).toBeTruthy()
    expect(screen.getByText('agentReviews.detail.actionsTitle')).toBeTruthy()
  })

  it('hides the action panel when the task is not pending', () => {
    useAgentReviewDetailMock.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
        task: {
          id: 42,
          agentVersionId: 5,
          namespaceId: 1,
          status: 'APPROVED',
          submittedBy: 'alice',
          reviewedBy: 'admin-1',
          reviewComment: 'ok',
          submittedAt: '2026-04-27T00:00:00Z',
          reviewedAt: '2026-04-27T01:00:00Z',
        },
        agent: {
          id: 7,
          namespace: 'global',
          slug: 'demo',
          displayName: 'Demo',
          description: '',
          visibility: 'PUBLIC',
          ownerId: 'alice',
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
        version: {
          id: 5,
          agentId: 7,
          version: '1.0.0',
          status: 'PUBLISHED',
          submittedBy: 'alice',
          submittedAt: '',
          publishedAt: null,
          packageSizeBytes: 0,
          manifestYaml: null,
          soulMd: null,
          workflowYaml: null,
        },
      },
    })

    render(<AgentReviewDetailPage taskId={42} />)
    expect(screen.queryByText('agentReviews.detail.actionsTitle')).toBeNull()
    expect(screen.getByText('agentReviews.detail.statusApproved')).toBeTruthy()
  })
})
