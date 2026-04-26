import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import type { AgentDetail } from '@/api/agent-types'
import { findMockAgent } from './mock-agents'

/**
 * Returns one agent's full detail by name.
 * Errors if the name is unknown so callers can render a not-found state.
 */
export function useAgentDetail(name: string): UseQueryResult<AgentDetail> {
  return useQuery({
    queryKey: ['agents', name],
    queryFn: async () => {
      const agent = findMockAgent(name)
      if (!agent) {
        throw new Error(`Agent not found: ${name}`)
      }
      return agent
    },
    enabled: name.length > 0,
    retry: false,
  })
}
