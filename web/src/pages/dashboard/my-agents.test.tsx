// @vitest-environment jsdom
import { afterEach, describe, it, expect, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

const useAuthMock = vi.fn()
vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => useAuthMock(),
}))

const listMock = vi.fn()
vi.mock('@/api/client', () => ({
  agentsApi: {
    list: (...args: unknown[]) => listMock(...args),
  },
}))

vi.mock('@/features/agent/use-archive-agent', () => ({
  useArchiveAgent: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))
vi.mock('@/features/agent/use-unarchive-agent', () => ({
  useUnarchiveAgent: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'en' } }),
}))

import { MyAgentsPage } from './my-agents'

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

afterEach(() => {
  cleanup()
  useAuthMock.mockReset()
  listMock.mockReset()
})

describe('MyAgentsPage', () => {
  it('renders an Archive button for an active owned agent', async () => {
    useAuthMock.mockReturnValue({ user: { userId: 'u-1' } })
    listMock.mockResolvedValue({
      items: [
        {
          id: 1,
          namespace: 'global',
          slug: 'planner',
          displayName: 'Planner',
          description: 'plans',
          visibility: 'PUBLIC',
          ownerId: 'u-1',
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ],
      page: 0,
      size: 200,
      total: 1,
    })

    render(<MyAgentsPage />, { wrapper })

    await screen.findByText('Planner')
    expect(screen.getByRole('button', { name: 'agents.lifecycle.archive' })).toBeTruthy()
    expect(screen.getByText('agents.lifecycle.statusActive')).toBeTruthy()
  })

  it('renders an Unarchive button + Archived badge for an archived owned agent', async () => {
    useAuthMock.mockReturnValue({ user: { userId: 'u-1' } })
    listMock.mockResolvedValue({
      items: [
        {
          id: 2,
          namespace: 'global',
          slug: 'sleeper',
          displayName: 'Sleeper',
          description: null,
          visibility: 'PRIVATE',
          ownerId: 'u-1',
          status: 'ARCHIVED',
          createdAt: '',
          updatedAt: '',
        },
      ],
      page: 0,
      size: 200,
      total: 1,
    })

    render(<MyAgentsPage />, { wrapper })

    await screen.findByText('Sleeper')
    expect(screen.getByRole('button', { name: 'agents.lifecycle.unarchive' })).toBeTruthy()
    expect(screen.getByText('agents.lifecycle.statusArchived')).toBeTruthy()
  })

  it('filters out agents owned by other users', async () => {
    useAuthMock.mockReturnValue({ user: { userId: 'u-1' } })
    listMock.mockResolvedValue({
      items: [
        {
          id: 3,
          namespace: 'global',
          slug: 'someone-else',
          displayName: 'Someone Else',
          description: null,
          visibility: 'PUBLIC',
          ownerId: 'u-2',
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ],
      page: 0,
      size: 200,
      total: 1,
    })

    render(<MyAgentsPage />, { wrapper })

    await screen.findByText('agents.myAgents.empty')
    expect(screen.queryByText('Someone Else')).toBeNull()
  })
})
