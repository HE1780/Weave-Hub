import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentLifecycleApi } from '@/api/client'

interface RereleaseCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Clones a PUBLISHED agent version into a new version with a fresh
 * version string. Status follows agent visibility (PRIVATE auto-publishes,
 * PUBLIC/NS goes back through review).
 *
 * Differs from skill rerelease: no confirmWarnings retry — agent's
 * pre-publish validator treats warnings as errors. If a precheck fails,
 * the mutation reports the error directly.
 */
export function useRereleaseAgentVersion(callbacks?: RereleaseCallbacks) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      namespace,
      slug,
      version,
      targetVersion,
    }: {
      namespace: string
      slug: string
      version: string
      targetVersion: string
    }) => agentLifecycleApi.rereleaseAgentVersion(namespace, slug, version, targetVersion),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['agents', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['agentReviews'] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
