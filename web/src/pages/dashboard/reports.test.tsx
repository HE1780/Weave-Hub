// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
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

vi.mock('@/shared/components/confirm-dialog', () => ({
  ConfirmDialog: () => null,
}))

vi.mock('@/features/report/report-text', () => ({
  REPORT_TEXT_WRAP_CLASS_NAME: 'text-wrap',
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

const useSkillReportsMock = vi.fn((_status: string) => ({ data: [] as unknown[], isLoading: false }))
const useAgentReportsMock = vi.fn((_status: string) => ({ data: [] as unknown[], isLoading: false }))

vi.mock('@/features/report/use-skill-reports', () => ({
  useDismissSkillReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useResolveSkillReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSkillReports: (status: string) => useSkillReportsMock(status),
}))

vi.mock('@/features/report/use-agent-reports', () => ({
  useDismissAgentReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useResolveAgentReport: () => ({ mutateAsync: vi.fn(), isPending: false }),
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
})
