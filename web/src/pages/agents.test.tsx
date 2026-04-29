// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'

const useAgentsMock = vi.fn()
vi.mock('@/features/agent/use-agents', () => ({
  useAgents: (...args: unknown[]) => useAgentsMock(...args),
}))

const useAuthMock = vi.fn()
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => useAuthMock(),
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    className,
    children,
  }: {
    to: string
    className?: string
    children: React.ReactNode
  }) => (
    <a href={to} className={className}>
      {children}
    </a>
  ),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k }),
}))

vi.mock('@/features/agent/agent-card', () => ({
  AgentCard: ({ agent }: { agent: { slug: string } }) => (
    <div data-testid="agent-card">{agent.slug}</div>
  ),
}))

import { AgentsPage } from './agents'

beforeEach(() => {
  useAgentsMock.mockReset()
  useAuthMock.mockReset()
  useAgentsMock.mockReturnValue({ data: [], isLoading: false, isError: false })
  useAuthMock.mockReturnValue({ user: null })
})

afterEach(() => cleanup())

describe('AgentsPage', () => {
  it('debounces the search input by ~300ms before calling useAgents with q', async () => {
    vi.useFakeTimers()
    try {
      render(<AgentsPage />)
      const input = screen.getByPlaceholderText('agents.search.placeholder') as HTMLInputElement

      fireEvent.change(input, { target: { value: 'hello' } })
      // Immediately after typing the hook should still see undefined q.
      expect(useAgentsMock.mock.calls.at(-1)?.[0]).toMatchObject({ q: undefined })

      await act(async () => {
        vi.advanceTimersByTime(300)
      })

      expect(useAgentsMock.mock.calls.at(-1)?.[0]).toMatchObject({ q: 'hello' })
    } finally {
      vi.useRealTimers()
    }
  })

  it('does not render admin filter controls', () => {
    useAuthMock.mockReturnValue({ user: null })
    render(<AgentsPage />)
    expect(screen.queryByText('agents.search.allNamespaces')).toBeNull()
    expect(screen.queryByText('agents.search.allVisibility')).toBeNull()
  })

  it('keeps publish button for authenticated users without showing filter controls', () => {
    useAuthMock.mockReturnValue({ user: { id: 'u-1' } })
    render(<AgentsPage />)
    expect(screen.getByText('agents.publish.title')).toBeTruthy()
    expect(screen.queryByText('agents.search.allNamespaces')).toBeNull()
    expect(screen.queryByText('agents.search.allVisibility')).toBeNull()
  })

  it('shows search-noResults empty state when filter is active and result list is empty', async () => {
    vi.useFakeTimers()
    try {
      useAgentsMock.mockReturnValue({ data: [], isLoading: false, isError: false })
      render(<AgentsPage />)
      const input = screen.getByPlaceholderText('agents.search.placeholder') as HTMLInputElement
      fireEvent.change(input, { target: { value: 'no-such' } })
      await act(async () => {
        vi.advanceTimersByTime(300)
      })
      expect(screen.queryByText('agents.search.noResults')).toBeTruthy()
    } finally {
      vi.useRealTimers()
    }
  })
})
