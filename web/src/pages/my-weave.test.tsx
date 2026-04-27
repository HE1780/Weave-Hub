// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'

const useNavigateMock = vi.fn()
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => useNavigateMock,
}))

const useAuthMock = vi.fn()
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => useAuthMock(),
}))

const useMySkillsMock = vi.fn()
vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMySkills: (...args: unknown[]) => useMySkillsMock(...args),
}))

const useQueryMock = vi.fn()
vi.mock('@tanstack/react-query', () => ({
  useQuery: (...args: unknown[]) => useQueryMock(...args),
}))

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({
    t: (key: string) => {
      const dict: Record<string, string> = {
        'myWeave.title': '我的 Weave',
        'myWeave.subtitle': 'subtitle',
        'myWeave.skillsHeading': '我的技能包',
        'myWeave.agentsHeading': '我的智能体',
        'myWeave.viewAll': '查看全部',
        'myWeave.skillsEmpty': '暂无技能包',
        'myWeave.agentsEmpty': '暂无智能体',
        'agents.loading': '加载中',
      }
      return dict[key] ?? key
    },
  }),
}))

import { MyWeavePage } from './my-weave'

beforeEach(() => {
  useNavigateMock.mockReset()
  useAuthMock.mockReset()
  useMySkillsMock.mockReset()
  useQueryMock.mockReset()

  useAuthMock.mockReturnValue({
    user: { userId: 'u1' },
  })

  useMySkillsMock.mockReturnValue({
    data: {
      items: [
        { id: 's1', displayName: 'Skill One', summary: 'Summary', namespace: 'global', slug: 'skill-one' },
      ],
    },
    isLoading: false,
  })

  useQueryMock.mockReturnValue({
    data: [
      { id: 'a1', ownerId: 'u1', displayName: 'Agent One', description: 'Agent Desc', namespace: 'global', slug: 'agent-one', visibility: 'PUBLIC' },
    ],
    isLoading: false,
  })
})

afterEach(() => cleanup())

describe('MyWeavePage', () => {
  it('uses tag tabs to switch between SKILL and 智能体 lists', () => {
    render(<MyWeavePage />)

    const skillTab = screen.getByText('SKILL')
    const agentTab = screen.getByText('智能体')

    expect(skillTab).toBeTruthy()
    expect(agentTab).toBeTruthy()
    expect(skillTab.className).toContain('rounded-full')
    expect(skillTab.className).toContain('bg-primary')
    expect(skillTab.className).toContain('text-primary-foreground')
    expect(skillTab.className).toContain('ring-1')
    expect(skillTab.className).toContain('text-sm')
    expect(skillTab.className).toContain('font-extrabold')
    expect(skillTab.className).not.toContain('border-b-2')
    expect(skillTab.parentElement?.className).toContain('bg-brand-50')
    expect(skillTab.parentElement?.className).toContain('border-brand-200')
    expect(skillTab.parentElement?.className).toContain('sm:w-[380px]')
    expect(skillTab.className).toContain('flex-1')
    expect(agentTab.className).toContain('flex-1')
    expect(skillTab.className).toContain('text-center')
    expect(agentTab.className).toContain('text-center')
    const viewAllButton = screen.getByText('查看全部')
    expect(viewAllButton.className).toContain('h-11')
    expect(viewAllButton.className).toContain('rounded-full')
    expect(viewAllButton.className).toContain('px-5')

    expect(screen.getByText('Skill One')).toBeTruthy()
    expect(screen.queryByText('Agent One')).toBeNull()

    fireEvent.click(agentTab)
    expect(screen.getByText('Agent One')).toBeTruthy()
    expect(screen.queryByText('Skill One')).toBeNull()
  })
})
