// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { useAgentDetail } from './use-agent-detail'

const getMock = vi.fn()
const listVersionsMock = vi.fn()
const getVersionMock = vi.fn()

vi.mock('@/api/client', () => ({
  agentsApi: {
    get: (...args: unknown[]) => getMock(...args),
    listVersions: (...args: unknown[]) => listVersionsMock(...args),
    getVersion: (...args: unknown[]) => getVersionMock(...args),
  },
}))

beforeEach(() => {
  getMock.mockReset()
  listVersionsMock.mockReset()
  getVersionMock.mockReset()
})

const sampleWorkflow = `
steps:
  - id: greet
    type: llm
    prompt: hello
  - id: classify
    type: skill
    skill: ticket-classifier
`

describe('useAgentDetail', () => {
  it('hydrates name + soul + workflow steps from the latest PUBLISHED version', async () => {
    getMock.mockResolvedValueOnce({
      id: 7, namespace: 'global', slug: 'agent-a',
      displayName: 'Agent A', description: 'd',
      visibility: 'PUBLIC', ownerId: 'owner-1', status: 'ACTIVE',
      createdAt: '2026-04-26T00:00:00Z', updatedAt: '2026-04-26T00:00:00Z',
    })
    listVersionsMock.mockResolvedValueOnce([
      { id: 70, agentId: 7, version: '1.0.0', status: 'PUBLISHED',
        submittedBy: 'owner-1', submittedAt: '...', publishedAt: '...',
        packageSizeBytes: 1, manifestYaml: null, soulMd: null, workflowYaml: null },
    ])
    getVersionMock.mockResolvedValueOnce({
      id: 70, agentId: 7, version: '1.0.0', status: 'PUBLISHED',
      submittedBy: 'owner-1', submittedAt: '...', publishedAt: '...',
      packageSizeBytes: 1,
      manifestYaml: '---\nname: agent-a\n---\n',
      soulMd: 'You are helpful.',
      workflowYaml: sampleWorkflow,
    })

    const { result } = renderHook(() => useAgentDetail('global', 'agent-a'), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data?.name).toBe('agent-a')
    expect(result.current.data?.soul).toBe('You are helpful.')
    expect(result.current.data?.workflow?.steps?.length).toBe(2)
  })

  it('errors when agent has no PUBLISHED version yet', async () => {
    getMock.mockResolvedValueOnce({
      id: 7, namespace: 'global', slug: 'agent-b',
      displayName: 'Agent B', description: 'd',
      visibility: 'PUBLIC', ownerId: 'owner-1', status: 'ACTIVE',
      createdAt: '...', updatedAt: '...',
    })
    listVersionsMock.mockResolvedValueOnce([
      { id: 71, agentId: 7, version: '0.1.0', status: 'PENDING_REVIEW',
        submittedBy: 'owner-1', submittedAt: '...', publishedAt: null,
        packageSizeBytes: 1, manifestYaml: null, soulMd: null, workflowYaml: null },
    ])

    const { result } = renderHook(() => useAgentDetail('global', 'agent-b'), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('errors when backend returns 404 for the agent', async () => {
    getMock.mockRejectedValueOnce(new Error('not found'))

    const { result } = renderHook(() => useAgentDetail('global', 'does-not-exist'), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('omits workflow when YAML is empty', async () => {
    getMock.mockResolvedValueOnce({
      id: 7, namespace: 'global', slug: 'agent-c',
      displayName: 'Agent C', description: 'd',
      visibility: 'PUBLIC', ownerId: 'owner-1', status: 'ACTIVE',
      createdAt: '...', updatedAt: '...',
    })
    listVersionsMock.mockResolvedValueOnce([
      { id: 72, agentId: 7, version: '1.0.0', status: 'PUBLISHED',
        submittedBy: 'owner-1', submittedAt: '...', publishedAt: '...',
        packageSizeBytes: 1, manifestYaml: null, soulMd: null, workflowYaml: null },
    ])
    getVersionMock.mockResolvedValueOnce({
      id: 72, agentId: 7, version: '1.0.0', status: 'PUBLISHED',
      submittedBy: 'owner-1', submittedAt: '...', publishedAt: '...',
      packageSizeBytes: 1,
      manifestYaml: null, soulMd: null, workflowYaml: null,
    })

    const { result } = renderHook(() => useAgentDetail('global', 'agent-c'), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data?.workflow).toBeUndefined()
  })
})
