// @vitest-environment jsdom
import { describe, expect, it, afterEach, vi } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, ...props }: { children?: React.ReactNode; to?: string; className?: string }) => (
    <a {...props}>{children}</a>
  ),
}))

import { LandingFooter } from './landing-footer'

i18n.use(initReactI18next).init({
  lng: 'zh',
  resources: {
    zh: {
      translation: {
        landing: {
          footer: {
            tagline: 'WeaveHub 知连:连接每一种智力片段。',
            documentation: 'Documentation',
            community: 'Community',
            links: {
              apiReferences: 'API References',
              cloudSync: 'Cloud Sync',
              security: 'Security',
              integration: 'Integration',
              openSource: 'Open Source',
              forum: 'Forum',
              privacy: 'Privacy',
              support: 'Support',
            },
            copyright: '© 2026 WEAVEHUB INTELLIGENCE.',
            version: 'VER 0.1.0',
            networkReady: 'NETWORK READY',
          },
        },
      },
    },
  },
})

afterEach(() => cleanup())

describe('LandingFooter', () => {
  it('renders WeaveHub brand (English-only) without 知连 prefix', () => {
    const { container } = render(
      <I18nextProvider i18n={i18n}>
        <LandingFooter />
      </I18nextProvider>,
    )
    const brandHeading = container.querySelector('[data-testid="footer-brand"]')
    expect(brandHeading?.textContent).toContain('WeaveHub')
    expect(brandHeading?.textContent).not.toContain('知连')
  })

  it('renders Documentation and Community columns with 4 links each', () => {
    const { container } = render(
      <I18nextProvider i18n={i18n}>
        <LandingFooter />
      </I18nextProvider>,
    )
    const text = container.textContent ?? ''
    expect(text).toContain('Documentation')
    expect(text).toContain('Community')
    expect(text).toContain('API References')
    expect(text).toContain('Open Source')
    expect(text).toContain('NETWORK READY')
  })
})
