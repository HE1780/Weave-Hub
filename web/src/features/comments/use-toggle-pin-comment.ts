import { useMutation, useQueryClient } from '@tanstack/react-query'
import { commentsApi } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

export function useTogglePinComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (pinned: boolean) => commentsApi.togglePin(commentId, pinned),
    onSuccess: () => qc.invalidateQueries({ queryKey: getVersionCommentsQueryKey(versionId) }),
  })
}
