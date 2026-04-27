import { useInfiniteQuery } from '@tanstack/react-query'
import { agentCommentsApi } from '@/api/client'
import { getAgentVersionCommentsQueryKey } from './query-keys'

const PAGE_SIZE = 20

export function useAgentVersionComments(versionId: number, enabled = true) {
  return useInfiniteQuery({
    queryKey: getAgentVersionCommentsQueryKey(versionId),
    enabled,
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      agentCommentsApi.list(versionId, { page: pageParam as number, size: PAGE_SIZE }),
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  })
}
