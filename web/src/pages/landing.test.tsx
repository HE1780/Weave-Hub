// @vitest-environment jsdom
import { describe, expect, it, afterEach, vi } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { LandingPage } from './landing'

vi.mock('@/shared/components/landing-hero', () => ({
  LandingHero: () => <div data-testid="hero">hero</div>,
}))
vi.mock('@/shared/components/landing-hot-section', () => ({
  LandingHotSection: () => <div data-testid="hot">hot</div>,
}))
vi.mock('@/shared/components/landing-recent-section', () => ({
  LandingRecentSection: () => <div data-testid="recent">recent</div>,
}))
vi.mock('@/shared/components/landing-workspace', () => ({
  LandingWorkspace: () => <div data-testid="workspace">workspace</div>,
}))
vi.mock('@/shared/components/landing-footer', () => ({
  LandingFooter: () => <div data-testid="footer">footer</div>,
}))

afterEach(() => cleanup())

describe('LandingPage', () => {
  it('renders 4 sections + footer in correct order', () => {
    const { container } = render(<LandingPage />)
    expect(container.textContent).toContain('hero')
    expect(container.textContent).toContain('hot')
    expect(container.textContent).toContain('recent')
    expect(container.textContent).toContain('workspace')
    expect(container.textContent).toContain('footer')
  })
})
