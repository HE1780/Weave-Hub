import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import type { AgentSummary } from '@/api/agent-types'
import { MOCK_AGENTS } from './mock-agents'

/**
 * Returns the list of agents.
 *
 * The backend is not yet implemented; this hook resolves to a static fixture.
 * When the API lands, replace the queryFn body with a fetchJson call.
 */
export function useAgents(): UseQueryResult<AgentSummary[]> {
  return useQuery({
    queryKey: ['agents'],
    queryFn: async () => {
      return MOCK_AGENTS.map(({ name, description, version }) => ({
        name,
        description,
        version,
      }))
    },
  })
}
