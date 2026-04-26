import { useQuery } from '@tanstack/react-query'
import { agentReviewsApi, type AgentReviewTaskDtoStatus } from '@/api/client'

/**
 * Loads agent review tasks for the namespace inbox.
 *
 * Backend contract (AgentReviewController): namespaceId is required and the
 * caller must hold an admin role inside that namespace; the controller returns
 * 403 otherwise. Surface that with `enabled` so we don't fire requests we know
 * will fail.
 */
export function useAgentReviews(
  namespaceId: number | undefined,
  status: AgentReviewTaskDtoStatus = 'PENDING',
  page = 0,
  size = 20,
  enabled = true,
) {
  return useQuery({
    queryKey: ['agentReviews', namespaceId, status, page, size],
    queryFn: async () => {
      if (!namespaceId) {
        throw new Error('namespaceId is required')
      }
      const response = await agentReviewsApi.list({ namespaceId, status, page, size })
      return {
        items: response.items,
        totalElements: response.total,
        totalPages: response.size > 0 ? Math.ceil(response.total / response.size) : 0,
        page: response.page,
      }
    },
    enabled: enabled && !!namespaceId && namespaceId > 0,
  })
}
