import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { agentsApi, type AgentDto } from '@/api/client'
import type { AgentSummary } from '@/api/agent-types'

/**
 * Returns the list of public agents from the backend.
 *
 * Maps AgentDto -> AgentSummary so existing UI consumers (AgentCard) keep
 * working unchanged. Per ADR §File Contracts, agent slug is unique within a
 * namespace; v1 surfaces only a single namespace ('global') in the public list,
 * so we use slug as the display name. When multi-namespace publishing lands,
 * the AgentCard contract will need to disambiguate — out of scope for this PR.
 */
export function useAgents(): UseQueryResult<AgentSummary[]> {
  return useQuery({
    queryKey: ['agents'],
    queryFn: async () => {
      const page = await agentsApi.list({ page: 0, size: 50 })
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
