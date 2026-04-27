import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentCommentsApi } from '@/api/client'
import { getAgentVersionCommentsQueryKey } from './query-keys'

export function useTogglePinAgentComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (pinned: boolean) => agentCommentsApi.togglePin(commentId, pinned),
    onSuccess: () => qc.invalidateQueries({ queryKey: getAgentVersionCommentsQueryKey(versionId) }),
  })
}
