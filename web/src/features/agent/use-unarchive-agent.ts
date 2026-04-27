import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentLifecycleApi } from '@/api/client'

interface UnarchiveCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Unarchives an agent (same permission rule as archive).
 * Mirrors useUnarchiveSkill on the cache invalidation surface.
 */
export function useUnarchiveAgent(callbacks?: UnarchiveCallbacks) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ namespace, slug }: { namespace: string; slug: string }) =>
      agentLifecycleApi.unarchiveAgent(namespace, slug),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      queryClient.invalidateQueries({ queryKey: ['agents', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['agents', variables.namespace, variables.slug] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
