import { useMutation, useQueryClient } from '@tanstack/react-query'
import { commentsApi } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

export function useDeleteComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => commentsApi.delete(commentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: getVersionCommentsQueryKey(versionId) }),
  })
}
