import { describe, expect, it } from 'vitest'
import * as mod from './promote-agent-button'

/**
 * promote-agent-button.tsx exports the PromoteAgentButton React component.
 * Behavior is exercised through page-level snapshot tests in
 * agent-detail.test.tsx; here we just verify the export contract so
 * downstream importers break fast if the module shape changes.
 */
describe('promote-agent-button module exports', () => {
  it('exports PromoteAgentButton as a function', () => {
    expect(mod.PromoteAgentButton).toBeDefined()
    expect(typeof mod.PromoteAgentButton).toBe('function')
  })
})
