// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { LandingChannelsSection } from './landing-channels'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, ...rest }: { children: React.ReactNode; to: string }) => (
    <a href={to} {...rest}>
      {children}
    </a>
  ),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

vi.mock('lucide-react', () => ({
  Bot: () => <span data-testid="icon-bot" />,
  PackageOpen: () => <span data-testid="icon-package" />,
}))

afterEach(() => cleanup())

describe('LandingChannelsSection', () => {
  it('renders title and subtitle', () => {
    render(<LandingChannelsSection />)
    expect(screen.getByText('landing.channels.title')).toBeTruthy()
    expect(screen.getByText('landing.channels.subtitle')).toBeTruthy()
  })

  it('renders both channel labels', () => {
    render(<LandingChannelsSection />)
    expect(screen.getByText('landing.channels.skill.label')).toBeTruthy()
    expect(screen.getByText('landing.channels.agent.label')).toBeTruthy()
  })

  it('renders skill card link to /skills', () => {
    const { container } = render(<LandingChannelsSection />)
    const skillsLink = Array.from(container.querySelectorAll('a')).find(
      (a) => a.getAttribute('href') === '/skills',
    )
    expect(skillsLink).toBeTruthy()
    expect(skillsLink?.textContent).toContain('landing.channels.skill.label')
  })

  it('renders agent card link to /agents', () => {
    const { container } = render(<LandingChannelsSection />)
    const agentsLink = Array.from(container.querySelectorAll('a')).find(
      (a) => a.getAttribute('href') === '/agents',
    )
    expect(agentsLink).toBeTruthy()
    expect(agentsLink?.textContent).toContain('landing.channels.agent.label')
  })
})
