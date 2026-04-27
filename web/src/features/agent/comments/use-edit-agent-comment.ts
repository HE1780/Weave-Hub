import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentCommentsApi } from '@/api/client'
import { getAgentVersionCommentsQueryKey } from './query-keys'

export function useEditAgentComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => agentCommentsApi.edit(commentId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: getAgentVersionCommentsQueryKey(versionId) }),
  })
}
