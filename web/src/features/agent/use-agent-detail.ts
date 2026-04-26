import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { parse as parseYaml } from 'yaml'
import { agentsApi } from '@/api/client'
import type { AgentDetail, AgentWorkflow } from '@/api/agent-types'

/**
 * Returns one agent's full detail by namespace + slug.
 *
 * Fetches the latest PUBLISHED version's inline soul.md and workflow.yaml.
 * Errors if the agent is missing OR has no PUBLISHED version yet so callers
 * can render a not-found state.
 */
export function useAgentDetail(namespace: string, slug: string): UseQueryResult<AgentDetail> {
  return useQuery({
    queryKey: ['agents', namespace, slug],
    queryFn: async () => {
      const agent = await agentsApi.get(namespace, slug)
      const versions = await agentsApi.listVersions(namespace, slug)
      const latestPublished = versions.find((v) => v.status === 'PUBLISHED')
      if (!latestPublished) {
        throw new Error(`Agent has no published version: ${namespace}/${slug}`)
      }
      const full = await agentsApi.getVersion(namespace, slug, latestPublished.version)
      return {
        name: agent.slug,
        description: agent.description ?? '',
        version: full.version,
        body: full.manifestYaml ?? undefined,
        soul: full.soulMd ?? undefined,
        workflow: parseWorkflow(full.workflowYaml),
        frontmatter: undefined,
      }
    },
    enabled: namespace.length > 0 && slug.length > 0,
    retry: false,
  })
}

function parseWorkflow(yaml: string | null): AgentWorkflow | undefined {
  if (!yaml) return undefined
  try {
    const parsed = parseYaml(yaml)
    if (parsed && typeof parsed === 'object' && Array.isArray((parsed as { steps?: unknown }).steps)) {
      return parsed as AgentWorkflow
    }
    return undefined
  } catch {
    return undefined
  }
}
