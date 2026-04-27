import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentLifecycleApi } from '@/api/client'

interface WithdrawCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Withdraws a PENDING_REVIEW agent version back to DRAFT. Mirrors
 * useWithdrawSkillReview's cache surface — invalidates the version
 * list + agent detail + review inbox.
 */
export function useWithdrawAgentReview(callbacks?: WithdrawCallbacks) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      namespace,
      slug,
      version,
    }: {
      namespace: string
      slug: string
      version: string
    }) => agentLifecycleApi.withdrawAgentReview(namespace, slug, version),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['agents', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['agentReviews'] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
