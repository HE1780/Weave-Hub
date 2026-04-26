// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import type { ReactNode } from 'react'
import { AgentDetailPage } from './agent-detail'

i18n.use(initReactI18next).init({
  lng: 'en',
  resources: {
    en: {
      translation: {
        agents: {
          loading: 'Loading agents…',
          loadError: 'Failed to load agents.',
          detail: {
            soulHeading: 'Soul',
            workflowHeading: 'Workflow',
            skillsHeading: 'Skills used',
            noSoul: '(no soul provided)',
            noWorkflow: '(no workflow defined)',
            stepLabel: 'Step',
            typeLabel: 'Type',
            skillLabel: 'Skill',
            promptLabel: 'Prompt',
            inputsLabel: 'Inputs',
          },
        },
      },
    },
  },
  interpolation: { escapeValue: false },
})

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </I18nextProvider>
  )
}

describe('AgentDetailPage', () => {
  it('renders the agent name, description, soul, and workflow when found', async () => {
    render(<AgentDetailPage name="customer-support-agent" />, { wrapper })

    await waitFor(() =>
      expect(screen.getByText('customer-support-agent')).toBeInTheDocument(),
    )
    expect(screen.getByText('Soul')).toBeInTheDocument()
    expect(screen.getByText('Workflow')).toBeInTheDocument()
  })

  it('renders the load-error message when the agent is unknown', async () => {
    render(<AgentDetailPage name="does-not-exist" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Failed to load agents.')).toBeInTheDocument())
  })
})
