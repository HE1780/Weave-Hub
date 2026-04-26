// @vitest-environment jsdom
import { describe, expect, it, afterEach } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { Card } from './card'

afterEach(() => cleanup())

describe('Card', () => {
  it('renders without glass class by default', () => {
    const { container } = render(<Card>content</Card>)
    const card = container.firstChild as HTMLElement
    expect(card.className).not.toContain('glass-card')
    expect(card.className).toContain('rounded-xl')
  })

  it('applies glass-card class when glass prop is true', () => {
    const { container } = render(<Card glass>content</Card>)
    const card = container.firstChild as HTMLElement
    expect(card.className).toContain('glass-card')
  })

  it('does not set inline border when glass prop is true', () => {
    const { container } = render(<Card glass>content</Card>)
    const card = container.firstChild as HTMLElement
    expect(card.getAttribute('style') ?? '').not.toContain('border-color')
  })

  it('passes through className alongside glass', () => {
    const { container } = render(<Card glass className="custom-extra">content</Card>)
    const card = container.firstChild as HTMLElement
    expect(card.className).toContain('glass-card')
    expect(card.className).toContain('custom-extra')
  })
})
