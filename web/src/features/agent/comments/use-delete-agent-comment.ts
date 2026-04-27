import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentCommentsApi } from '@/api/client'
import { getAgentVersionCommentsQueryKey } from './query-keys'

export function useDeleteAgentComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => agentCommentsApi.delete(commentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: getAgentVersionCommentsQueryKey(versionId) }),
  })
}
