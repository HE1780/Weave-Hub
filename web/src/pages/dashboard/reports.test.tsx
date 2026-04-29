// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n/config'

const navigateMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
}))

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (v: string) => v,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: React.ReactNode }) => <div data-testid="card">{children}</div>,
}))

vi.mock('@/shared/ui/tabs', () => ({
  Tabs: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  TabsContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  TabsList: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  TabsTrigger: ({ children, value }: { children: React.ReactNode; value: string }) => (
    <button data-testid={`tab-${value}`}>{children}</button>
  ),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, onClick }: { children: React.ReactNode; onClick?: () => void }) => (
    <button onClick={onClick}>{children}</button>
  ),
}))

// Render the dialog as a plain wrapper that exposes its confirm button when open,
// so tests can drive the resolve / dismiss flow end-to-end.
vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: ({
    open,
    onConfirm,
    confirmText,
  }: {
    open: boolean
    onConfirm: () => void
    confirmText: string
  }) =>
    open ? (
      <button data-testid="confirm-dialog-confirm" onClick={onConfirm}>
        {confirmText}
      </button>
    ) : null,
}))

vi.mock('@/features/report/report-text', () => ({
  REPORT_TEXT_WRAP_CLASS_NAME: 'text-wrap',
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

const useSkillReportsMock = vi.fn((_status: string) => ({ data: [] as unknown[], isLoading: false }))
const useAgentReportsMock = vi.fn((_status: string) => ({ data: [] as unknown[], isLoading: false }))
const resolveAgentMutateAsync = vi.fn()
const dismissAgentMutateAsync = vi.fn()

vi.mock('@/features/report/use-skill-reports', () => ({
  useDismissSkillReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useResolveSkillReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSkillReports: (status: string) => useSkillReportsMock(status),
}))

vi.mock('@/features/report/use-agent-reports', () => ({
  useDismissAgentReport: () => ({ mutateAsync: dismissAgentMutateAsync, isPending: false }),
  useResolveAgentReport: () => ({ mutateAsync: resolveAgentMutateAsync, isPending: false }),
  useAgentReports: (status: string) => useAgentReportsMock(status),
}))

import { ReportsPage } from './reports'

function renderPage() {
  return render(
    <I18nextProvider i18n={i18n}>
      <ReportsPage />
    </I18nextProvider>,
  )
}

describe('ReportsPage', () => {
  it('exports a named component function', () => {
    expect(typeof ReportsPage).toBe('function')
  })

  it('renders both Skill and Agent top-level tabs', () => {
    useSkillReportsMock.mockReturnValue({ data: [], isLoading: false })
    useAgentReportsMock.mockReturnValue({ data: [], isLoading: false })
    renderPage()

    expect(screen.getByTestId('tab-SKILL')).toBeInTheDocument()
    expect(screen.getByTestId('tab-AGENT')).toBeInTheDocument()
  })

  it('renders a pending agent report card with agent label and Open agent button', () => {
    useSkillReportsMock.mockReturnValue({ data: [], isLoading: false })
    useAgentReportsMock.mockImplementation((status: string) => {
      if (status === 'PENDING') {
        return {
          data: [
            {
              id: 7,
              agentId: 42,
              namespace: 'global',
              agentSlug: 'planner',
              agentDisplayName: 'Planner Agent',
              reporterId: 'user-1',
              reason: 'Spam',
              details: 'details',
              status: 'PENDING',
              createdAt: '2026-04-29T12:00:00Z',
            },
          ],
          isLoading: false,
        }
      }
      return { data: [], isLoading: false }
    })

    renderPage()

    expect(screen.getByText('global/planner')).toBeInTheDocument()
    expect(screen.getByText('Planner Agent')).toBeInTheDocument()
    expect(screen.getAllByText(/Open agent/i).length).toBeGreaterThan(0)
  })

  it('agent panel resolve button drives the agent-specific mutation, not skill', async () => {
    resolveAgentMutateAsync.mockReset().mockResolvedValue({})
    dismissAgentMutateAsync.mockReset()
    useSkillReportsMock.mockReturnValue({ data: [], isLoading: false })
    useAgentReportsMock.mockImplementation((status: string) => {
      if (status === 'PENDING') {
        return {
          data: [
            {
              id: 7,
              agentId: 42,
              namespace: 'global',
              agentSlug: 'planner',
              agentDisplayName: 'Planner Agent',
              reporterId: 'user-1',
              reason: 'Spam',
              details: 'details',
              status: 'PENDING',
              createdAt: '2026-04-29T12:00:00Z',
            },
          ],
          isLoading: false,
        }
      }
      return { data: [], isLoading: false }
    })

    renderPage()

    // Tabs is mocked to render every TabsContent at once, so the same agent
    // PENDING row shows up under both the outer AGENT panel and the inner
    // PENDING tab — pick the first Resolve button (skill panel is empty).
    const resolveButtons = screen.getAllByRole('button', { name: /^Resolve$/i })
    fireEvent.click(resolveButtons[0])
    fireEvent.click(await screen.findByTestId('confirm-dialog-confirm'))

    expect(resolveAgentMutateAsync).toHaveBeenCalledTimes(1)
    expect(resolveAgentMutateAsync).toHaveBeenCalledWith({ id: 7, disposition: 'RESOLVE_ONLY' })
    expect(dismissAgentMutateAsync).not.toHaveBeenCalled()
  })
})
