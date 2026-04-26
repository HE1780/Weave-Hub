// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { WorkflowSteps } from './workflow-steps'
import type { AgentWorkflow } from '@/api/agent-types'

i18n.use(initReactI18next).init({
  lng: 'en',
  resources: {
    en: {
      translation: {
        agents: {
          detail: {
            stepLabel: 'Step',
            typeLabel: 'Type',
            skillLabel: 'Skill',
            promptLabel: 'Prompt',
            inputsLabel: 'Inputs',
            noWorkflow: '(no workflow defined)',
          },
        },
      },
    },
  },
  interpolation: { escapeValue: false },
})

function renderWith(workflow: AgentWorkflow | undefined) {
  return render(
    <I18nextProvider i18n={i18n}>
      <WorkflowSteps workflow={workflow} />
    </I18nextProvider>,
  )
}

describe('WorkflowSteps', () => {
  it('renders skill and think steps with their type-specific fields', () => {
    renderWith({
      steps: [
        { id: 'a', type: 'skill', skill: 'foo' },
        { id: 'b', type: 'think', prompt: 'reflect on a' },
      ],
    })

    expect(screen.getByText('a')).toBeInTheDocument()
    expect(screen.getByText('foo')).toBeInTheDocument()
    expect(screen.getByText('b')).toBeInTheDocument()
    expect(screen.getByText('reflect on a')).toBeInTheDocument()
  })

  it('renders an empty-state message when workflow is missing', () => {
    renderWith(undefined)
    expect(screen.getByText('(no workflow defined)')).toBeInTheDocument()
  })
})
