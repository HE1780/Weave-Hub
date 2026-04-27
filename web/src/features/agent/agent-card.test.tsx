// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    params,
    className,
    children,
  }: {
    to: string
    params?: Record<string, string>
    className?: string
    children: React.ReactNode
  }) => {
    const href = Object.entries(params ?? {}).reduce(
      (acc, [k, v]) => acc.replace(`$${k}`, v),
      to,
    )
    return (
      <a href={href} className={className}>
        {children}
      </a>
    )
  },
}))

import { AgentCard } from './agent-card'

afterEach(() => cleanup())

describe('AgentCard', () => {
  const agent = {
    name: 'demo-agent',
    displayName: 'Demo Agent',
    description: 'A demo agent for tests.',
    version: '1.2.3',
    namespace: 'team-x',
    starCount: 42,
  }

  it('renders skill-style card meta: display name, namespace, version, downloads, and stars', () => {
    render(<AgentCard agent={{ ...agent, downloadCount: 1234 }} />)

    expect(screen.getByText('Demo Agent')).toBeInTheDocument()
    expect(screen.getByText('@team-x')).toBeInTheDocument()
    expect(screen.getByText('A demo agent for tests.')).toBeInTheDocument()
    expect(screen.getByText(/1\.2\.3/)).toBeInTheDocument()
    expect(screen.getByText('1.2K')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
  })

  it('falls back to 0 download count when missing', () => {
    render(<AgentCard agent={agent} />)
    expect(screen.getByText('0')).toBeInTheDocument()
  })

  it('wraps the card in a link to the agent detail route', () => {
    render(<AgentCard agent={agent} />)

    const link = screen.getByRole('link')
    expect(link).toHaveAttribute('href', '/agents/team-x/demo-agent')
  })

  it('falls back to the global namespace when AgentSummary has none', () => {
    render(<AgentCard agent={{ name: 'orphan', description: 'no ns' }} />)

    expect(screen.getByRole('link')).toHaveAttribute('href', '/agents/global/orphan')
    expect(screen.getByText('@global')).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })

  it('applies a fixed minimum height so cards align like the skills grid', () => {
    render(<AgentCard agent={agent} />)

    const title = screen.getByText('Demo Agent')
    const card = title.closest('div[class*="glass-card"]')
    expect(card?.className).toContain('min-h-[220px]')
  })
})
