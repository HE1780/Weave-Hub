import { useMutation, useQueryClient } from '@tanstack/react-query'
import { commentsApi } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

export function usePostComment(versionId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => commentsApi.post(versionId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: getVersionCommentsQueryKey(versionId) }),
  })
}
