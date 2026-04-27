import { createElement, type ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const cardClasses: string[] = []
const buttonRecords: Array<{ label: string; className?: string; disabled?: boolean }> = []
const uploadZoneCalls: Array<{ disabled?: boolean }> = []

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/agent/use-publish-agent', () => ({
  usePublishAgent: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => ({ data: [], isLoading: false }),
}))

vi.mock('@/features/publish/upload-zone', () => ({
  UploadZone: ({ disabled }: { disabled?: boolean }) => {
    uploadZoneCalls.push({ disabled })
    return createElement('div', { 'data-testid': 'upload-zone' }, 'upload-zone')
  },
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({
    children,
    className,
    disabled,
  }: {
    children?: ReactNode
    className?: string
    disabled?: boolean
  }) => {
    const label = Array.isArray(children) ? children.join('') : String(children ?? '')
    buttonRecords.push({ label, className, disabled })
    return createElement('button', { className, disabled }, children)
  },
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => {
    cardClasses.push(className ?? '')
    return createElement('div', { className }, children)
  },
}))

vi.mock('@/shared/ui/label', () => ({
  Label: ({ children }: { children?: ReactNode }) => createElement('label', null, children),
}))

vi.mock('@/shared/ui/select', () => ({
  Select: ({ children }: { children?: ReactNode }) => createElement('div', null, children),
  SelectContent: ({ children }: { children?: ReactNode }) => createElement('div', null, children),
  SelectItem: ({ children }: { children?: ReactNode }) => createElement('div', null, children),
  SelectTrigger: ({ children }: { children?: ReactNode }) => createElement('div', null, children),
  SelectValue: () => null,
  normalizeSelectValue: (v: string) => v || null,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/api/client', () => ({
  ApiError: class ApiError extends Error {},
}))

import { AgentPublishPage } from './publish-agent'

describe('AgentPublishPage', () => {
  beforeEach(() => {
    cardClasses.length = 0
    buttonRecords.length = 0
    uploadZoneCalls.length = 0
  })

  it('matches publish page visual structure with notice card, upload zone and full-width submit', () => {
    const html = renderToStaticMarkup(createElement(AgentPublishPage))

    expect(html).toContain('max-w-2xl mx-auto space-y-8 animate-fade-up')
    expect(cardClasses).toContain('p-4 bg-blue-500/5 border-blue-500/20')
    expect(cardClasses).toContain('p-8 space-y-8')
    expect(uploadZoneCalls.length).toBe(1)
    expect(html).not.toContain('type="radio"')
    expect(html).toContain('publish.visibilityOptions.public')
    expect(html).toContain('publish.visibilityOptions.namespaceOnly')
    expect(html).toContain('publish.visibilityOptions.private')
    expect(html).not.toContain('agents.publish.visibilityPublic')
    expect(html).not.toContain('agents.publish.visibilityNamespace')
    expect(html).not.toContain('agents.publish.visibilityPrivate')

    const submitButton = buttonRecords.find((record) => record.label === 'agents.publish.submit')
    expect(submitButton?.className).toContain('w-full')
    expect(submitButton?.className).toContain('text-primary-foreground')
  })

  it('keeps submit button enabled when not pending so validation can run on click', () => {
    renderToStaticMarkup(createElement(AgentPublishPage))
    const submitButton = buttonRecords.find((record) => record.label === 'agents.publish.submit')
    expect(submitButton?.disabled).toBe(false)
  })
})
