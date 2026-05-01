// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

import { AgentVersionStatusBadge } from './version-status-badge'

afterEach(() => cleanup())

describe('AgentVersionStatusBadge', () => {
  const cases: Array<[string, string]> = [
    ['DRAFT', 'agents.versionStatus.draft'],
    ['SCANNING', 'agents.versionStatus.scanning'],
    ['SCAN_FAILED', 'agents.versionStatus.scanFailed'],
    ['UPLOADED', 'agents.versionStatus.uploaded'],
    ['PENDING_REVIEW', 'agents.versionStatus.pendingReview'],
    ['PUBLISHED', 'agents.versionStatus.published'],
    ['REJECTED', 'agents.versionStatus.rejected'],
    ['ARCHIVED', 'agents.versionStatus.archived'],
    ['YANKED', 'agents.versionStatus.yanked'],
  ]

  it.each(cases)('renders i18n key for %s', (status, expectedKey) => {
    render(<AgentVersionStatusBadge status={status} />)
    expect(screen.getByText(expectedKey)).toBeInTheDocument()
  })

  it('returns null for missing status', () => {
    const { container } = render(<AgentVersionStatusBadge />)
    expect(container.firstChild).toBeNull()
  })

  it('falls back to raw status for unknown enum value', () => {
    render(<AgentVersionStatusBadge status="WEIRD_STATUS" />)
    expect(screen.getByText('WEIRD_STATUS')).toBeInTheDocument()
  })

  it('applies destructive line-through for YANKED', () => {
    render(<AgentVersionStatusBadge status="YANKED" />)
    const node = screen.getByText('agents.versionStatus.yanked')
    expect(node.className).toContain('line-through')
  })
})
