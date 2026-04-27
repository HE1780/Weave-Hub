// @vitest-environment jsdom
import { afterEach, describe, it, expect, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import type { ReactNode } from 'react'
import { AgentDetailPage } from './agent-detail'

const useAgentDetailMock = vi.fn()
vi.mock('@/features/agent/use-agent-detail', () => ({
  useAgentDetail: (...args: unknown[]) => useAgentDetailMock(...args),
}))

const useAuthMock = vi.fn()
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => useAuthMock(),
}))

vi.mock('@/features/agent/use-archive-agent', () => ({
  useArchiveAgent: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))
vi.mock('@/features/agent/use-unarchive-agent', () => ({
  useUnarchiveAgent: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/features/agent/social/agent-star-button', () => ({
  AgentStarButton: () => null,
}))
vi.mock('@/features/agent/social/agent-rating-input', () => ({
  AgentRatingInput: () => null,
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

i18n.use(initReactI18next).init({
  lng: 'en',
  resources: {
    en: {
      translation: {
        agents: {
          loading: 'Loading agents…',
          loadError: 'Failed to load agents.',
          detail: {
            soulHeading: 'Soul',
            workflowHeading: 'Workflow',
            skillsHeading: 'Skills used',
            noSoul: '(no soul provided)',
            noWorkflow: '(no workflow defined)',
            stepLabel: 'Step',
            typeLabel: 'Type',
            skillLabel: 'Skill',
            promptLabel: 'Prompt',
            inputsLabel: 'Inputs',
          },
          lifecycle: {
            statusActive: 'Active',
            statusArchived: 'Archived',
            archive: 'Archive',
            unarchive: 'Unarchive',
            manageHeading: 'Manage agent',
            archiveDescription: 'Hide it.',
            unarchiveDescription: 'Restore it.',
            processing: 'Processing…',
            archiveConfirmTitle: 'Archive?',
            archiveConfirmDescription: 'Archive {{agent}}?',
            unarchiveConfirmTitle: 'Unarchive?',
            unarchiveConfirmDescription: 'Restore {{agent}}?',
          },
        },
      },
    },
  },
  interpolation: { escapeValue: false },
})

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </I18nextProvider>
  )
}

afterEach(() => {
  cleanup()
  useAgentDetailMock.mockReset()
  useAuthMock.mockReset()
})

describe('AgentDetailPage', () => {
  it('renders the agent name, description, soul, and workflow when found', async () => {
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'customer-support-agent',
        description: 'Triages tickets',
        soul: 'You are helpful.',
        workflow: { steps: [{ id: 'greet', type: 'llm', prompt: 'hi' }] },
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: null })

    render(<AgentDetailPage namespace="global" slug="customer-support-agent" />, { wrapper })

    await waitFor(() =>
      expect(screen.getByText('customer-support-agent')).toBeInTheDocument(),
    )
    expect(screen.getByText('Soul')).toBeInTheDocument()
    expect(screen.getByText('Workflow')).toBeInTheDocument()
  })

  it('renders the load-error message when the agent is unknown', async () => {
    useAgentDetailMock.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    })
    useAuthMock.mockReturnValue({ user: null })

    render(<AgentDetailPage namespace="global" slug="does-not-exist" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Failed to load agents.')).toBeInTheDocument())
  })

  it('shows the manage section with Archive button when the viewer owns the agent', async () => {
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'planner',
        description: 'plans things',
        agentId: 1,
        slug: 'planner',
        displayName: 'Planner',
        ownerId: 'u-1',
        status: 'ACTIVE',
        namespace: 'global',
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'u-1' } })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Manage agent')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Archive' })).toBeInTheDocument()
  })

  it('hides the manage section for non-owners', async () => {
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'planner',
        description: 'plans things',
        agentId: 1,
        slug: 'planner',
        displayName: 'Planner',
        ownerId: 'u-1',
        status: 'ACTIVE',
        namespace: 'global',
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'u-2' } })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    await waitFor(() => expect(screen.getByText('planner')).toBeInTheDocument())
    expect(screen.queryByText('Manage agent')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Archive' })).toBeNull()
  })

  it('shows Unarchive button + Archived badge when the agent is archived and viewer owns it', async () => {
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'planner',
        description: 'plans things',
        agentId: 1,
        slug: 'planner',
        displayName: 'Planner',
        ownerId: 'u-1',
        status: 'ARCHIVED',
        namespace: 'global',
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'u-1' } })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Manage agent')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Unarchive' })).toBeInTheDocument()
    expect(screen.getAllByText('Archived').length).toBeGreaterThan(0)
  })
})
