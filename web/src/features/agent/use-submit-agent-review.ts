import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentLifecycleApi } from '@/api/client'

interface SubmitReviewCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Transitions an UPLOADED agent version to PENDING_REVIEW for PUBLIC or
 * NAMESPACE_ONLY visibility, creating a review task. Mirrors
 * useSubmitSkillReview cache surface.
 */
export function useSubmitAgentReview(callbacks?: SubmitReviewCallbacks) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      namespace,
      slug,
      version,
      targetVisibility,
    }: {
      namespace: string
      slug: string
      version: string
      targetVisibility: 'PUBLIC' | 'NAMESPACE_ONLY'
    }) => agentLifecycleApi.submitAgentReview(namespace, slug, version, targetVisibility),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['agents', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['agentReviews'] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
