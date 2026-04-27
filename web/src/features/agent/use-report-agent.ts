import { useMutation } from '@tanstack/react-query'
import { reportApi } from '@/api/client'

/**
 * Submits an abuse report against an agent. Mirrors useSubmitSkillReport;
 * the body shape ({ reason, details? }) is identical so consumers can
 * share form state if they end up rendering both widgets.
 */
export function useReportAgent(namespace: string, slug: string) {
  return useMutation({
    mutationFn: (request: { reason: string; details?: string }) =>
      reportApi.submitAgentReport(namespace, slug, request),
  })
}
