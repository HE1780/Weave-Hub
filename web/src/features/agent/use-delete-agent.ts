import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentLifecycleApi } from '@/api/client'

interface DeleteCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Hard-deletes an agent. Owner OR SUPER_ADMIN per backend policy.
 * Mirrors useDeleteSkill's cache cleanup: clears the agent-specific
 * detail/version cache and invalidates list queries so the row drops
 * from /agents and /my-weave + /dashboard/my-agents.
 */
export function useDeleteAgent(callbacks?: DeleteCallbacks) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ namespace, slug }: { namespace: string; slug: string }) =>
      agentLifecycleApi.deleteAgent(namespace, slug),
    onSuccess: (_data, variables) => {
      const detailKey = ['agents', variables.namespace, variables.slug] as const
      void queryClient.cancelQueries({ queryKey: detailKey })
      queryClient.removeQueries({ queryKey: detailKey })
      void queryClient.invalidateQueries({ queryKey: ['agents', 'my'] })
      void queryClient.invalidateQueries({ queryKey: ['agents'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
