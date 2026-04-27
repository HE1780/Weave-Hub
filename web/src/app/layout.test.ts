import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// Layout is a component-only file with no exported pure functions or constants.
// We verify that the named export exists for the router to consume.

const useRouterStateMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  Outlet: () => null,
  Link: ({ children }: { children: unknown }) => children,
  useRouterState: () => useRouterStateMock(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({
    user: null,
    isLoading: false,
  }),
}))

vi.mock('@/shared/components/language-switcher', () => ({
  LanguageSwitcher: () => null,
}))

vi.mock('@/shared/components/user-menu', () => ({
  UserMenu: () => null,
}))

vi.mock('./layout-header-style', () => ({
  getAppHeaderClassName: () => 'header-class',
}))

vi.mock('./layout-main-content', () => ({
  resolveAppMainContentPathname: (p: string) => p,
  getAppMainContentLayout: () => ({
    mainClassName: 'main-class',
    contentClassName: 'content-class',
  }),
}))

vi.mock('@/shared/components/landing-footer', () => ({
  LandingFooter: () => 'landing-footer-component',
}))

import { Layout } from './layout'

describe('Layout', () => {
  beforeEach(() => {
    useRouterStateMock.mockReturnValue({ pathname: '/', resolvedPathname: '/' })
  })

  it('exports a named Layout component function', () => {
    expect(typeof Layout).toBe('function')
    expect(Layout.name).toBe('Layout')
  })

  it('renders landing footer on landing route through layout', () => {
    const html = renderToStaticMarkup(createElement(Layout))
    expect(html).toContain('landing-footer-component')
  })
})
