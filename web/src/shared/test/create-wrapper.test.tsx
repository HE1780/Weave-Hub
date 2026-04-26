// @vitest-environment jsdom
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useQuery } from '@tanstack/react-query'
import { createWrapper, createWrapperWithClient } from './create-wrapper'

describe('createWrapper', () => {
  it('provides a QueryClient so useQuery can run inside renderHook', async () => {
    const { result } = renderHook(
      () => useQuery({ queryKey: ['t'], queryFn: () => Promise.resolve(42) }),
      { wrapper: createWrapper() }
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toBe(42)
  })

  it('returns a fresh client per call so tests do not share cache', () => {
    const w1 = createWrapper()
    const w2 = createWrapper()
    expect(w1).not.toBe(w2)
  })

  it('disables retries so failed queries surface errors immediately', async () => {
    const { result } = renderHook(
      () =>
        useQuery({
          queryKey: ['err'],
          queryFn: () => Promise.reject(new Error('boom')),
        }),
      { wrapper: createWrapper() }
    )
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error).message).toBe('boom')
  })

  it('createWrapperWithClient exposes the client for cache spies', () => {
    const { Wrapper, client } = createWrapperWithClient()
    const spy = vi.spyOn(client, 'invalidateQueries')
    expect(typeof Wrapper).toBe('function')
    client.invalidateQueries({ queryKey: ['x'] })
    expect(spy).toHaveBeenCalledWith({ queryKey: ['x'] })
  })
})
