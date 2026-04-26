import { useInfiniteQuery } from '@tanstack/react-query'
import { commentsApi } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

const PAGE_SIZE = 20

export function useVersionComments(versionId: number, enabled = true) {
  return useInfiniteQuery({
    queryKey: getVersionCommentsQueryKey(versionId),
    enabled,
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      commentsApi.list(versionId, { page: pageParam as number, size: PAGE_SIZE }),
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  })
}
