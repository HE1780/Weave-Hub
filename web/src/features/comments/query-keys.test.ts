import { describe, expect, it } from 'vitest'
import { getVersionCommentsQueryKey } from './query-keys'

describe('getVersionCommentsQueryKey', () => {
  it('keys by versionId', () => {
    expect(getVersionCommentsQueryKey(99)).toEqual(['version-comments', 99])
  })
})
