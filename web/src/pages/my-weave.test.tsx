import { describe, expect, it } from 'vitest'
import * as mod from './my-weave'

describe('MyWeavePage', () => {
  it('exports MyWeavePage', () => {
    expect(typeof mod.MyWeavePage).toBe('function')
  })
})
