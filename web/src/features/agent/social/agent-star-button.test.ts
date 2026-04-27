import { describe, expect, it } from 'vitest'
import * as mod from './agent-star-button'

describe('agent-star-button module exports', () => {
  it('exports the AgentStarButton component', () => {
    expect(mod.AgentStarButton).toBeDefined()
    expect(typeof mod.AgentStarButton).toBe('function')
  })
})
