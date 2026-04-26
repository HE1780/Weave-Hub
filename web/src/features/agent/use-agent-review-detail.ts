import { useQuery } from '@tanstack/react-query'
import { agentReviewsApi } from '@/api/client'

/**
 * Fetches the full review payload for one task: the task row, the underlying
 * agent, and the version (including inline soul/workflow content).
 *
 * Backed by the dedicated /detail endpoint so the reviewer page renders without
 * paginated round-trips to /agents/{ns}/{slug}/versions.
 */
export function useAgentReviewDetail(taskId: number, enabled = true) {
  return useQuery({
    queryKey: ['agentReviews', taskId, 'detail'],
    queryFn: () => agentReviewsApi.getDetail(taskId),
    enabled: enabled && taskId > 0,
  })
}
