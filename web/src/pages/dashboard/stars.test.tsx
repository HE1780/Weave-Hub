// @vitest-environment jsdom
import type * as React from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'

const useMyStarsPageMock = vi.fn()
const useMyAgentStarsPageMock = vi.fn()

vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMyStarsPage: (...args: unknown[]) => useMyStarsPageMock(...args),
  useMyAgentStarsPage: (...args: unknown[]) => useMyAgentStarsPageMock(...args),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children, ...rest }: { children: React.ReactNode } & Record<string, unknown>) => (
    <a {...(rest as Record<string, unknown>)}>{children}</a>
  ),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'en' } }),
}))

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: ({ skill }: { skill: { slug: string } }) => (
    <div data-testid="skill-card">{skill.slug}</div>
  ),
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: () => null,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: ({ title }: { title: string }) => <h1>{title}</h1>,
}))

import { MyStarsPage } from './stars'

beforeEach(() => {
  useMyStarsPageMock.mockReturnValue({
    data: { items: [], total: 0, page: 0, size: 12 },
    isLoading: false,
  })
  useMyAgentStarsPageMock.mockReturnValue({
    data: { items: [], total: 0, page: 0, size: 12 },
    isLoading: false,
  })
})

afterEach(() => {
  cleanup()
  useMyStarsPageMock.mockReset()
  useMyAgentStarsPageMock.mockReset()
})

describe('MyStarsPage', () => {
  it('defaults to the Skills tab and shows the skills empty state', () => {
    render(<MyStarsPage />)
    expect(screen.getByText('stars.tabSkills')).toBeTruthy()
    expect(screen.getByText('stars.tabAgents')).toBeTruthy()
    // Skills panel is the default; its empty state is visible.
    expect(screen.getByText('stars.empty')).toBeTruthy()
  })

  it('switches to Agents tab and renders agent star cards', () => {
    useMyAgentStarsPageMock.mockReturnValue({
      data: {
        items: [
          {
            id: 7,
            slug: 'agent-alpha',
            displayName: 'Agent Alpha',
            description: 'first agent',
            namespace: 'global',
            starCount: 5,
          },
        ],
        total: 1,
        page: 0,
        size: 12,
      },
      isLoading: false,
    })

    render(<MyStarsPage />)
    fireEvent.click(screen.getByText('stars.tabAgents'))

    expect(screen.getByText('Agent Alpha')).toBeTruthy()
  })

  it('shows the agents empty state when no agent stars exist', () => {
    render(<MyStarsPage />)
    fireEvent.click(screen.getByText('stars.tabAgents'))

    expect(screen.getByText('stars.emptyAgents')).toBeTruthy()
  })
})
