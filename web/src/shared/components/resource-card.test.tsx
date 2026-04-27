// @vitest-environment jsdom
import { describe, expect, it, afterEach, vi } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { ResourceCard } from './resource-card'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ to, children, className }: { to: string; children: React.ReactNode; className?: string }) => (
    <a href={to} className={className}>{children}</a>
  ),
}))

vi.mock('motion/react', () => ({
  motion: {
    div: ({ children, className, ...rest }: React.HTMLAttributes<HTMLDivElement> & { children?: React.ReactNode }) => (
      <div className={className}>{children}</div>
    ),
  },
}))

afterEach(() => cleanup())

describe('ResourceCard', () => {
  const baseResource = {
    id: 'r1',
    title: 'Prompt Optimizer',
    type: 'skill' as const,
    category: '文案优化',
    updatedAt: '2h ago',
    href: '/skills/prompt-optimizer',
  }

  it('renders title, category, and type label in featured variant', () => {
    const { container } = render(
      <ResourceCard variant="featured" resource={baseResource} index={0} />,
    )
    expect(container.textContent).toContain('Prompt Optimizer')
    expect(container.textContent).toContain('文案优化')
    expect(container.textContent?.toLowerCase()).toContain('skill')
    expect(container.textContent?.toUpperCase()).toContain('EXPLORE')
  })

  it('renders compact variant without category subtitle and without EXPLORE row', () => {
    const { container } = render(
      <ResourceCard variant="compact" resource={baseResource} index={0} />,
    )
    expect(container.textContent).toContain('Prompt Optimizer')
    expect(container.textContent).not.toContain('文案优化')
    expect(container.textContent?.toUpperCase()).not.toContain('EXPLORE')
  })

  it('shows updatedAt in both variants', () => {
    const { container: featuredEl } = render(
      <ResourceCard variant="featured" resource={baseResource} index={0} />,
    )
    expect(featuredEl.textContent).toContain('2h ago')
    cleanup()
    const { container: compactEl } = render(
      <ResourceCard variant="compact" resource={baseResource} index={0} />,
    )
    expect(compactEl.textContent).toContain('2h ago')
  })

  it('renders agent type label correctly', () => {
    const agent = { ...baseResource, type: 'agent' as const, title: 'Code Reviewer' }
    const { container } = render(
      <ResourceCard variant="featured" resource={agent} index={0} />,
    )
    expect(container.textContent?.toLowerCase()).toContain('agent')
  })

  it('uses glass-card class for both variants', () => {
    const { container: a } = render(
      <ResourceCard variant="featured" resource={baseResource} index={0} />,
    )
    expect(a.firstChild as HTMLElement).toBeTruthy()
    expect((a.firstChild as HTMLElement).className).toContain('glass-card')
    cleanup()
    const { container: b } = render(
      <ResourceCard variant="compact" resource={baseResource} index={0} />,
    )
    expect((b.firstChild as HTMLElement).className).toContain('glass-card')
  })

  it('renders as a Link when href is provided', () => {
    const { container } = render(
      <ResourceCard variant="featured" resource={baseResource} index={0} />,
    )
    const link = container.querySelector('a')
    expect(link).toBeTruthy()
    expect(link?.getAttribute('href')).toContain('prompt-optimizer')
  })
})
