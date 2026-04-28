import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reportApi } from '@/api/client'
import type { AgentReportDisposition } from '@/api/types'

/**
 * Loads reported agents for the requested moderation status. Mirrors
 * {@link useSkillReports} so the dashboard reports page can render either
 * report family with the same renderer.
 */
export function useAgentReports(status: string) {
  return useQuery({
    queryKey: ['agent-reports', status],
    queryFn: async () => {
      const page = await reportApi.listAdminAgentReports({ status })
      return page.items
    },
  })
}

/**
 * Resolves an agent report and refreshes any list/governance widget that
 * derives counts from the same backend sources.
 */
export function useResolveAgentReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment, disposition }: { id: number; comment?: string; disposition?: AgentReportDisposition }) =>
      reportApi.resolveAgentReport(id, comment, disposition),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent-reports'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
    },
  })
}

/**
 * Dismisses an agent report without taking any heavier resolution path.
 */
export function useDismissAgentReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => reportApi.dismissAgentReport(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent-reports'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
    },
  })
}
