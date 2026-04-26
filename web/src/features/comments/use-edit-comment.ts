import { useMutation, useQueryClient } from '@tanstack/react-query'
import { commentsApi } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

export function useEditComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => commentsApi.edit(commentId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: getVersionCommentsQueryKey(versionId) }),
  })
}
