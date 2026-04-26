// @vitest-environment jsdom
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AgentCard } from './agent-card'

describe('AgentCard', () => {
  const agent = {
    name: 'demo-agent',
    description: 'A demo agent for tests.',
    version: '1.2.3',
  }

  it('renders the agent name, description, and version', () => {
    render(<AgentCard agent={agent} />)

    expect(screen.getByText('demo-agent')).toBeInTheDocument()
    expect(screen.getByText('A demo agent for tests.')).toBeInTheDocument()
    expect(screen.getByText(/1\.2\.3/)).toBeInTheDocument()
  })

  it('fires onClick when activated', () => {
    const onClick = vi.fn()
    render(<AgentCard agent={agent} onClick={onClick} />)

    fireEvent.click(screen.getByRole('link'))
    expect(onClick).toHaveBeenCalledOnce()
  })
})
