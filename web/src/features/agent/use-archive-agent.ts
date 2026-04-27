import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentLifecycleApi } from '@/api/client'

interface ArchiveCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Archives an agent (owner OR namespace ADMIN/OWNER per backend policy).
 * Mirrors useArchiveSkill: invalidates the agent-list, my-agents, and the
 * specific namespace/slug query keys.
 */
export function useArchiveAgent(callbacks?: ArchiveCallbacks) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      namespace,
      slug,
      reason,
    }: {
      namespace: string
      slug: string
      reason?: string
    }) => agentLifecycleApi.archiveAgent(namespace, slug, reason),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      queryClient.invalidateQueries({ queryKey: ['agents', 'my'] })
      queryClient.invalidateQueries({ queryKey: ['agents', variables.namespace, variables.slug] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
