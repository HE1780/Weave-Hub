import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'

interface StarStatus {
  starred: boolean
}

function pathFor(namespace: string, slug: string): string {
  const cleanNs = namespace.startsWith('@') ? namespace.slice(1) : namespace
  return `${WEB_API_PREFIX}/agents/${encodeURIComponent(cleanNs)}/${encodeURIComponent(slug)}/star`
}

/**
 * Star-state hooks for one agent. Mirrors features/social/use-star but uses
 * the agent {namespace}/{slug} path.
 *
 * Anonymous users are treated as unstarred instead of surfacing authorization
 * failures into the UI.
 */
async function getStarStatus(namespace: string, slug: string): Promise<StarStatus> {
  try {
    const starred = await fetchJson<boolean>(pathFor(namespace, slug))
    return { starred }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { starred: false }
    }
    throw error
  }
}

async function toggleStar(namespace: string, slug: string, starred: boolean): Promise<void> {
  await fetchJson<void>(pathFor(namespace, slug), {
    method: starred ? 'DELETE' : 'PUT',
    headers: getCsrfHeaders(),
  })
}

export function useAgentStar(namespace: string, slug: string, enabled = true) {
  return useQuery({
    queryKey: ['agents', namespace, slug, 'star'],
    queryFn: () => getStarStatus(namespace, slug),
    enabled: !!namespace && !!slug && enabled,
  })
}

export function useToggleAgentStar(namespace: string, slug: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (starred: boolean) => toggleStar(namespace, slug, starred),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agents', namespace, slug, 'star'] })
      queryClient.invalidateQueries({ queryKey: ['agents'] })
    },
  })
}
