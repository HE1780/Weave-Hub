import { describe, expect, it } from 'vitest'
import * as mod from './use-agent-rating'

describe('use-agent-rating module exports', () => {
  it('exports useAgentUserRating as a function', () => {
    expect(mod.useAgentUserRating).toBeDefined()
    expect(typeof mod.useAgentUserRating).toBe('function')
  })

  it('exports useRateAgent as a function', () => {
    expect(mod.useRateAgent).toBeDefined()
    expect(typeof mod.useRateAgent).toBe('function')
  })
})
