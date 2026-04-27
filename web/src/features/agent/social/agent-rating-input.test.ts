import { describe, expect, it } from 'vitest'
import * as mod from './agent-rating-input'

describe('agent-rating-input module exports', () => {
  it('exports the AgentRatingInput component', () => {
    expect(mod.AgentRatingInput).toBeDefined()
    expect(typeof mod.AgentRatingInput).toBe('function')
  })
})
