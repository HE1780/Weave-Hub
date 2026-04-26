// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { PopularAgents } from './popular-agents'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

const useAgentsMock = vi.fn()
vi.mock('@/features/agent/use-agents', () => ({
  useAgents: () => useAgentsMock(),
}))

vi.mock('@/features/agent/agent-card', () => ({
  AgentCard: ({ agent }: { agent: { name: string } }) => (
    <div data-testid="agent-card">{agent.name}</div>
  ),
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: ({ count }: { count: number }) => (
    <div data-testid="skeleton" data-count={count} />
  ),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: React.ReactNode }) => <button>{children}</button>,
}))

afterEach(() => cleanup())

describe('PopularAgents', () => {
  it('renders title and description', () => {
    useAgentsMock.mockReturnValue({ data: [], isLoading: false })
    render(<PopularAgents />)
    expect(screen.getByText('landing.popularAgents.title')).toBeTruthy()
    expect(screen.getByText('landing.popularAgents.description')).toBeTruthy()
  })

  it('renders top 3 agents when more are returned', () => {
    useAgentsMock.mockReturnValue({
      data: [
        { name: 'a1', description: 'd1' },
        { name: 'a2', description: 'd2' },
        { name: 'a3', description: 'd3' },
        { name: 'a4', description: 'd4' },
        { name: 'a5', description: 'd5' },
      ],
      isLoading: false,
    })
    render(<PopularAgents />)
    expect(screen.getAllByTestId('agent-card')).toHaveLength(3)
    expect(screen.getByText('a1')).toBeTruthy()
    expect(screen.getByText('a3')).toBeTruthy()
    expect(screen.queryByText('a4')).toBeNull()
  })

  it('renders skeleton list while loading', () => {
    useAgentsMock.mockReturnValue({ data: undefined, isLoading: true })
    render(<PopularAgents />)
    expect(screen.getByTestId('skeleton')).toBeTruthy()
  })

  it('renders View all link with the correct i18n key', () => {
    useAgentsMock.mockReturnValue({ data: [], isLoading: false })
    render(<PopularAgents />)
    expect(screen.getByText('landing.popularAgents.viewAll')).toBeTruthy()
  })
})
