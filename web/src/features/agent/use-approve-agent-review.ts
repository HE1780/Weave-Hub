import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentReviewsApi } from '@/api/client'

interface ApproveCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Approves an agent review task and invalidates the inbox cache so the row
 * disappears from the PENDING tab and reappears in APPROVED on next render.
 */
export function useApproveAgentReview(callbacks?: ApproveCallbacks) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ taskId, comment }: { taskId: number; comment?: string }) =>
      agentReviewsApi.approve(taskId, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agentReviews'] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
