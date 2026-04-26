import { useTranslation } from 'react-i18next'
import type { AgentWorkflow, AgentWorkflowStep } from '@/api/agent-types'

interface WorkflowStepsProps {
  workflow: AgentWorkflow | undefined
}

/**
 * Read-only renderer for a workflow's step list.
 * Knows nothing about routing or fetching — pure presentation.
 */
export function WorkflowSteps({ workflow }: WorkflowStepsProps) {
  const { t } = useTranslation()

  if (!workflow || workflow.steps.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic">
        {t('agents.detail.noWorkflow')}
      </p>
    )
  }

  return (
    <ol className="space-y-3">
      {workflow.steps.map((step, index) => (
        <li key={step.id} className="rounded-lg border p-4" style={{ borderColor: 'hsl(var(--border-card))' }}>
          <div className="flex items-center gap-3 mb-2">
            <span className="px-2 py-0.5 rounded-full bg-secondary/60 text-xs font-mono">
              {t('agents.detail.stepLabel')} {index + 1}
            </span>
            <span className="font-mono text-sm">{step.id}</span>
            {step.type && (
              <span className="text-xs text-muted-foreground">
                {t('agents.detail.typeLabel')}: {step.type}
              </span>
            )}
          </div>
          <StepBody step={step} />
        </li>
      ))}
    </ol>
  )
}

function StepBody({ step }: { step: AgentWorkflowStep }) {
  const { t } = useTranslation()
  return (
    <dl className="text-sm space-y-1">
      {step.skill && (
        <div className="flex gap-2">
          <dt className="text-muted-foreground">{t('agents.detail.skillLabel')}:</dt>
          <dd className="font-mono">{step.skill}</dd>
        </div>
      )}
      {step.prompt && (
        <div className="flex gap-2">
          <dt className="text-muted-foreground">{t('agents.detail.promptLabel')}:</dt>
          <dd>{step.prompt}</dd>
        </div>
      )}
      {step.inputs && Object.keys(step.inputs).length > 0 && (
        <div>
          <dt className="text-muted-foreground">{t('agents.detail.inputsLabel')}:</dt>
          <dd>
            <pre className="text-xs bg-muted/50 rounded p-2 mt-1 overflow-x-auto">
              {JSON.stringify(step.inputs, null, 2)}
            </pre>
          </dd>
        </div>
      )}
    </dl>
  )
}
