import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentLifecycleApi } from '@/api/client'

interface DeleteVersionCallbacks {
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * Deletes a single non-PUBLISHED agent version. Mirrors useDeleteSkillVersion.
 * Backend rejects PUBLISHED (must archive at agent level instead) and
 * PENDING_REVIEW (must withdraw first).
 */
export function useDeleteAgentVersion(callbacks?: DeleteVersionCallbacks) {
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
    }) => agentLifecycleApi.deleteAgentVersion(namespace, slug, version),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['agents', variables.namespace, variables.slug] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
