export function getVersionCommentsQueryKey(versionId: number) {
  return ['version-comments', versionId] as const
}
