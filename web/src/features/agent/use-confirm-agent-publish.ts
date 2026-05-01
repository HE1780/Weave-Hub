import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentLifecycleApi } from '@/api/client'

interface ConfirmPublishCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Transitions an UPLOADED PRIVATE agent version directly to PUBLISHED with no
 * review (auto-publish for the owner). Mirrors useConfirmSkillPublish.
 */
export function useConfirmAgentPublish(callbacks?: ConfirmPublishCallbacks) {
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
    }) => agentLifecycleApi.confirmAgentPublish(namespace, slug, version),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['agents', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
