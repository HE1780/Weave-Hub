// @vitest-environment jsdom
import { describe, expect, it, afterEach, vi } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from '@/i18n/locales/en.json'
import zh from '@/i18n/locales/zh.json'

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
      translation: zh,
    },
    en: {
      translation: en,
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

  it('renders zh footer labels and supports language toggle to en', async () => {
    await i18n.changeLanguage('zh')

    const { container } = render(
      <I18nextProvider i18n={i18n}>
        <LandingFooter />
      </I18nextProvider>,
    )
    const text = container.textContent ?? ''
    expect(text).toContain('文档')
    expect(text).toContain('社区')
    expect(text).toContain('API 参考')
    expect(text).toContain('开源')
    expect(text).toContain('网络就绪')

    await i18n.changeLanguage('en')
    const textAfterSwitch = container.textContent ?? ''
    expect(textAfterSwitch).toContain('Documentation')
    expect(textAfterSwitch).toContain('Community')
    expect(textAfterSwitch).toContain('API references')
    expect(textAfterSwitch).toContain('Open source')
    expect(textAfterSwitch).toContain('Network ready')
  })

  it('uses actionable links instead of hash placeholders', async () => {
    await i18n.changeLanguage('en')
    const { container } = render(
      <I18nextProvider i18n={i18n}>
        <LandingFooter />
      </I18nextProvider>,
    )
    expect(container.innerHTML).not.toContain('href="#"')
  })
})
