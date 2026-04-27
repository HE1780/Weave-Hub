import { describe, expect, it } from 'vitest'
import * as mod from './agent-label-panel'

/**
 * agent-label-panel.tsx exports the AgentLabelPanel React component.
 * Mirrors skill-label-panel: shape-only test guards the export contract,
 * since the panel's internal helpers are not directly importable.
 */
describe('agent-label-panel module exports', () => {
  it('exports the AgentLabelPanel component', () => {
    expect(mod.AgentLabelPanel).toBeDefined()
    expect(typeof mod.AgentLabelPanel).toBe('function')
  })
})
