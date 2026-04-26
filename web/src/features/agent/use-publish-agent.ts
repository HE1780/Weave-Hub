import { useMutation, useQueryClient } from '@tanstack/react-query'
import { agentsApi, type AgentDtoVisibility, type AgentPublishResponseDto } from '@/api/client'

interface PublishArgs {
  namespace: string
  file: File
  visibility: AgentDtoVisibility
}

export function usePublishAgent() {
  const qc = useQueryClient()
  return useMutation<AgentPublishResponseDto, Error, PublishArgs>({
    mutationFn: ({ namespace, file, visibility }) =>
      agentsApi.publish(namespace, file, visibility),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['agents'] })
    },
  })
}
