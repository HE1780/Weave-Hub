export function getAgentVersionCommentsQueryKey(versionId: number) {
  return ['agent-version-comments', versionId] as const
}
