import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'

interface UserRating {
  score: number
  rated: boolean
}

function pathFor(namespace: string, slug: string): string {
  const cleanNs = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return `${WEB_API_PREFIX}/agents/${encodeURIComponent(cleanNs)}/${encodeURIComponent(slug)}/rating`
}

/**
 * Reads the current user's rating for an agent. Mirrors features/social/use-rating.
 * Unauthenticated users normalize to an unrated state so the rating widget can stay
 * renderable without a hard error.
 */
async function getUserRating(namespace: string, slug: string): Promise<UserRating> {
  try {
    return await fetchJson<UserRating>(pathFor(namespace, slug))
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { score: 0, rated: false }
    }
    throw error
  }
}

async function rateAgent(namespace: string, slug: string, rating: number): Promise<void> {
  await fetchJson<void>(pathFor(namespace, slug), {
    method: 'PUT',
    headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ score: rating }),
  })
}

export function useAgentUserRating(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['agents', namespace, slug, 'rating'],
    queryFn: () => getUserRating(namespace, slug),
    enabled: !!namespace && !!slug,
  })
}

export function useRateAgent(namespace: string, slug: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (rating: number) => rateAgent(namespace, slug, rating),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agents', namespace, slug, 'rating'] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
    },
  })
}
