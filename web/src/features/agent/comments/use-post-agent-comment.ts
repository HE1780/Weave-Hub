import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentCommentsApi } from '@/api/client'
import { getAgentVersionCommentsQueryKey } from './query-keys'

export function usePostAgentComment(versionId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => agentCommentsApi.post(versionId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: getAgentVersionCommentsQueryKey(versionId) }),
  })
}
