import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentReviewsApi } from '@/api/client'

interface RejectCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Rejects an agent review task. Comment is required by UX (rejection should
 * always carry feedback) but the wire format keeps it optional to match the
 * backend's permissive contract.
 */
export function useRejectAgentReview(callbacks?: RejectCallbacks) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ taskId, comment }: { taskId: number; comment: string }) =>
      agentReviewsApi.reject(taskId, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agentReviews'] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
