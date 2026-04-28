// @vitest-environment jsdom
import { afterEach, describe, it, expect, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  AgentStarButton: () => <div data-testid="agent-star-widget">star-widget</div>,
}))
vi.mock('@/features/agent/social/agent-rating-input', () => ({
  AgentRatingInput: () => <div data-testid="agent-rating-widget">rating-widget</div>,
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

const formatLocalDateTimeMock = vi.fn((value: string | null | undefined, locale: string) => `FMT(${value ?? 'null'}|${locale})`)
vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (...args: [string | null | undefined, string]) => formatLocalDateTimeMock(...args),
}))

i18n.use(initReactI18next).init({
  lng: 'en',
  resources: {
    en: {
      translation: {
        agents: {
          loading: 'Loading agents…',
          loadError: 'Failed to load agents.',
          noPublishedVersion: 'This agent has no published version yet.',
          detail: {
            tabOverview: 'Overview',
            tabWorkflow: 'Workflow',
            tabFiles: 'Files',
            tabVersions: 'Versions',
            docTagAgent: 'agent.md',
            docTagSoul: 'soul.md',
            docEmpty: '(no document provided)',
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
          promote: {
            sectionTitle: 'Promote to global',
            sectionDescription: 'Submit this version for promotion.',
            label: 'Promote to global',
            submitting: 'Submitting…',
            confirmTitle: 'Promote agent',
            confirmBody: 'This will submit the current published version.',
          },
        },
        skillDetail: {
          install: 'Install',
          download: 'Download',
          share: {
            button: 'Share',
            copied: 'Copied',
            failed: 'Share failed',
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
  formatLocalDateTimeMock.mockClear()
})

describe('AgentDetailPage', () => {
  it('renders the agent name, description, soul, and workflow when found', async () => {
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'customer-support-agent',
        version: '1.0.0',
        description: 'Triages tickets',
        body: '# Agent Doc\n\nSupports triage.',
        soul: 'You are helpful.',
        workflow: { steps: [{ id: 'greet', type: 'llm', prompt: 'hi' }] },
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: null, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="customer-support-agent" />, { wrapper })

    await waitFor(() =>
      expect(screen.getByText('customer-support-agent')).toBeInTheDocument(),
    )
    expect(screen.getByRole('button', { name: 'Overview' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Workflow' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Files' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Versions' })).toBeInTheDocument()
    expect(screen.getByText('Install')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Share' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'agent.md' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'soul.md' })).toBeInTheDocument()
    expect(screen.getByText('Agent Doc')).toBeInTheDocument()
    expect(screen.queryByText('You are helpful.')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'soul.md' }))
    expect(screen.getByText('You are helpful.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Workflow' }))
    expect(screen.getByText('Step 1')).toBeInTheDocument()
  })

  it('renders the load-error message when the agent is unknown', async () => {
    useAgentDetailMock.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    })
    useAuthMock.mockReturnValue({ user: null, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="does-not-exist" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Failed to load agents.')).toBeInTheDocument())
  })

  it('shows the manage section with Archive button when the backend says the viewer can manage', async () => {
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
        canManageLifecycle: true,
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'u-1' }, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Manage agent')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Archive' })).toBeInTheDocument()
  })

  it('hides the manage section when canManageLifecycle is false', async () => {
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
        canManageLifecycle: false,
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'u-2' }, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    await waitFor(() => expect(screen.getByText('planner')).toBeInTheDocument())
    expect(screen.queryByText('Manage agent')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Archive' })).toBeNull()
  })

  it('shows Unarchive button + Archived badge when the agent is archived and viewer can manage', async () => {
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
        canManageLifecycle: true,
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'u-1' }, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Manage agent')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Unarchive' })).toBeInTheDocument()
    expect(screen.getAllByText('Archived').length).toBeGreaterThan(0)
  })

  it('shows Archive button to a namespace ADMIN who is NOT the owner (canManageLifecycle gates by backend permission, not local owner check)', async () => {
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
        canManageLifecycle: true,
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'ns-admin-99' }, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Manage agent')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Archive' })).toBeInTheDocument()
  })

  it('renders PromoteAgentButton for a SKILL_ADMIN viewing a non-global agent with a published version, without any GLOBAL namespace membership plumbing', async () => {
    // Spec: Task 1 of agent-followups bundle — promotion no longer depends on the
    // viewer being a member of GLOBAL. The backend resolves the GLOBAL namespace
    // server-side; the UI gate is just (hasRole SKILL_ADMIN/SUPER_ADMIN) + a
    // published version that is not already in global. This test would fail if
    // the button silently regressed to requiring `globalNamespaceId` plumbing.
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'planner',
        description: 'plans things',
        agentId: 42,
        slug: 'planner',
        displayName: 'Planner',
        ownerId: 'u-1',
        status: 'ACTIVE',
        namespace: 'acme',
        latestPublishedVersionId: 99,
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({
      user: { userId: 'admin-1' },
      hasRole: (role: string) => role === 'SKILL_ADMIN',
    })

    render(<AgentDetailPage namespace="acme" slug="planner" />, { wrapper })

    expect(
      await screen.findByRole('button', { name: 'Promote to global' }),
    ).toBeInTheDocument()
  })

  it('formats version timestamp with locale instead of rendering raw server time', async () => {
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'planner',
        description: 'plans things',
        versions: [
          {
            id: 11,
            version: '1.0.0',
            status: 'PUBLISHED',
            submittedAt: '2026-04-27T10:20:30',
            publishedAt: '2026-04-27T12:34:56',
            packageSizeBytes: 2048,
          },
        ],
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: null, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    fireEvent.click(screen.getByRole('button', { name: 'Versions' }))
    expect(screen.getByText('FMT(2026-04-27T12:34:56|en)')).toBeInTheDocument()
    expect(screen.queryByText('2026-04-27T12:34:56')).toBeNull()
  })

  it('renders inline package files and opens preview from files tab', async () => {
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'planner',
        description: 'plans things',
        body: '# Agent Doc',
        soul: 'Soul text',
        workflowYaml: 'steps:\n  - id: greet',
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: null, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    fireEvent.click(screen.getByRole('button', { name: 'Files' }))
    expect(screen.getAllByText('agent.md').length).toBeGreaterThan(0)
    expect(screen.getAllByText('soul.md').length).toBeGreaterThan(0)
    expect(screen.getAllByText('workflow.yaml').length).toBeGreaterThan(0)

    fireEvent.click(screen.getAllByText('agent.md')[0]!)
    expect(screen.getByText('Agent Doc')).toBeInTheDocument()
  })

  it('shows sidebar file browser card and keeps report action outside social card', async () => {
    useAgentDetailMock.mockReturnValue({
      data: {
        name: 'planner',
        description: 'plans things',
        namespace: 'global',
        slug: 'planner',
        agentId: 1,
        body: '# Agent Doc',
        soul: 'Soul text',
      },
      isLoading: false,
      isError: false,
    })
    useAuthMock.mockReturnValue({ user: { userId: 'u-1' }, hasRole: () => false })

    render(<AgentDetailPage namespace="global" slug="planner" />, { wrapper })

    await waitFor(() => expect(screen.getByText('planner')).toBeInTheDocument())
    expect(screen.getByText('fileTree.title')).toBeInTheDocument()
    expect(screen.getByTestId('agent-star-widget')).toBeInTheDocument()
    expect(screen.getByTestId('agent-rating-widget')).toBeInTheDocument()

    const reportButton = screen.getByRole('button', { name: 'agents.detail.reportButton' })
    const reportCard = reportButton.closest('.p-5')
    expect(reportCard).not.toBeNull()
    expect(reportCard?.querySelector('[data-testid="agent-star-widget"]')).toBeNull()
    expect(reportCard?.querySelector('[data-testid="agent-rating-widget"]')).toBeNull()
  })
})
