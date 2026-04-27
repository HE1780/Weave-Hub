import { describe, expect, it } from 'vitest'
import * as mod from './use-agent-star'

/**
 * Module-shape contract test mirroring features/social/use-star.test.
 * Both hooks are thin react-query wrappers without exportable pure helpers.
 */
describe('use-agent-star module exports', () => {
  it('exports useAgentStar as a function', () => {
    expect(mod.useAgentStar).toBeDefined()
    expect(typeof mod.useAgentStar).toBe('function')
  })

  it('exports useToggleAgentStar as a function', () => {
    expect(mod.useToggleAgentStar).toBeDefined()
    expect(typeof mod.useToggleAgentStar).toBe('function')
  })
})
