import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { agentsApi, type AgentDto, type AgentVisibilityFilter } from '@/api/client'
import type { AgentSummary } from '@/api/agent-types'

export interface UseAgentsParams {
  /** Case-insensitive keyword applied to display_name + description on the backend. */
  q?: string
  /** Namespace slug filter; backend resolves to id and 404s if unknown. */
  namespace?: string
  /** Optional visibility narrowing on top of the "everything I can see" default. */
  visibility?: AgentVisibilityFilter
}

/**
 * Returns the list of agents the caller can see, optionally filtered.
 *
 * Maps AgentDto -> AgentSummary so existing UI consumers (AgentCard) keep
 * working unchanged. Backend applies visibility rules; frontend just forwards
 * the params it has.
 */
export function useAgents(params: UseAgentsParams = {}): UseQueryResult<AgentSummary[]> {
  return useQuery({
    queryKey: ['agents', params],
    queryFn: async () => {
      const page = await agentsApi.list({ page: 0, size: 50, ...params })
      return page.items.map(toSummary)
    },
  })
}

function toSummary(dto: AgentDto): AgentSummary {
  return {
    name: dto.slug,
    description: dto.description ?? '',
    namespace: dto.namespace,
  }
}
