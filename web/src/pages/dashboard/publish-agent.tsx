import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api/client'
import { usePublishAgent } from '@/features/agent/use-publish-agent'
import { UploadZone } from '@/features/publish/upload-zone'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Label } from '@/shared/ui/label'
import {
  normalizeSelectValue,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { toast } from '@/shared/lib/toast'
import type { AgentDtoVisibility } from '@/api/client'

const NAMESPACE_PLACEHOLDER = '__select_namespace__'

/**
 * Agent publish page aligned with the skill publish visual system.
 *
 * The publishing workflow remains specific to agents, while layout and controls
 * (notice card, upload zone, selects, and CTA rhythm) match dashboard publish.
 */
export function AgentPublishPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [namespace, setNamespace] = useState<string>('')
  const [visibility, setVisibility] = useState<AgentDtoVisibility>('PRIVATE')

  const { data: namespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()
  const publish = usePublishAgent()
  const selectedNamespace = namespaces?.find((ns) => ns.slug === namespace)
  const namespaceOnlyLabel = selectedNamespace?.type === 'GLOBAL'
    ? t('publish.visibilityOptions.loggedInUsersOnly')
    : t('publish.visibilityOptions.namespaceOnly')

  const handleFileSelect = (file: File) => {
    setSelectedFile(file)
  }

  const handleRemoveSelectedFile = () => {
    setSelectedFile(null)
  }

  const handleSubmit = async () => {
    if (!selectedFile) {
      toast.error(t('agents.publish.errorMissingFile'))
      return
    }
    if (!namespace) {
      toast.error(t('agents.publish.errorMissingNamespace'))
      return
    }
    try {
      const result = await publish.mutateAsync({ namespace, file: selectedFile, visibility })
      const isAutoPublished = result.status === 'PUBLISHED'
      toast.success(
        isAutoPublished
          ? t('agents.publish.successAutoPublished')
          : t('agents.publish.successPendingReview'),
      )
      navigate({
        to: '/agents/$namespace/$slug',
        params: { namespace: result.namespace, slug: result.slug },
      })
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.message
          : t('agents.publish.errorGeneric')
      toast.error(message)
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('agents.publish.title')}
        subtitle={t('agents.publish.subtitle')}
      />

      <Card className="p-4 bg-blue-500/5 border-blue-500/20">
        <div className="flex items-start gap-3">
          <svg className="w-5 h-5 text-blue-500 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div className="flex-1">
            <h3 className="text-sm font-semibold text-foreground mb-1">{t('publish.reviewNotice.title')}</h3>
            <p className="text-sm text-muted-foreground">{t('publish.reviewNotice.description')}</p>
          </div>
        </div>
      </Card>

      <Card className="p-8 space-y-8">
        <div className="space-y-3">
          <Label htmlFor="agent-namespace" className="text-sm font-semibold font-heading">
            {t('agents.publish.namespaceLabel')}
          </Label>
          {isLoadingNamespaces ? (
            <div className="h-11 animate-shimmer rounded-lg" />
          ) : (
            <Select
              value={normalizeSelectValue(namespace) ?? NAMESPACE_PLACEHOLDER}
              onValueChange={(value) => setNamespace(value === NAMESPACE_PLACEHOLDER ? '' : value)}
            >
              <SelectTrigger id="agent-namespace">
                <SelectValue placeholder={t('agents.publish.namespacePlaceholder')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NAMESPACE_PLACEHOLDER}>{t('agents.publish.namespacePlaceholder')}</SelectItem>
                {namespaces?.map((ns) => (
                  <SelectItem key={ns.slug} value={ns.slug}>
                    {ns.displayName} (@{ns.slug})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>

        <div className="space-y-3">
          <Label htmlFor="agent-visibility" className="text-sm font-semibold font-heading">
            {t('publish.visibility')}
          </Label>
          <Select value={visibility} onValueChange={(value) => setVisibility(value as AgentDtoVisibility)}>
            <SelectTrigger id="agent-visibility">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="PUBLIC">{t('publish.visibilityOptions.public')}</SelectItem>
              <SelectItem value="NAMESPACE_ONLY">{namespaceOnlyLabel}</SelectItem>
              <SelectItem value="PRIVATE">{t('publish.visibilityOptions.private')}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-3">
          <Label className="text-sm font-semibold font-heading">{t('agents.publish.fileLabel')}</Label>
          <UploadZone
            key={selectedFile ? `${selectedFile.name}-${selectedFile.lastModified}` : 'empty'}
            onFileSelect={handleFileSelect}
            disabled={publish.isPending}
          />
          {selectedFile ? (
            <div className="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-secondary/30 px-4 py-3">
              <div className="min-w-0 text-sm text-muted-foreground flex items-center gap-2">
                <svg className="w-4 h-4 text-emerald-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
                <span className="truncate">
                  {t('agents.publish.fileSelected', {
                    name: selectedFile.name,
                    size: Math.round(selectedFile.size / 1024),
                  })}
                </span>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleRemoveSelectedFile}
                disabled={publish.isPending}
              >
                {t('publish.removeSelectedFile')}
              </Button>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">{t('agents.publish.filePlaceholder')}</p>
          )}
        </div>

        <Button
          className="w-full text-primary-foreground disabled:text-primary-foreground"
          size="lg"
          onClick={handleSubmit}
          disabled={publish.isPending}
        >
            {publish.isPending ? t('agents.publish.submitting') : t('agents.publish.submit')}
        </Button>
      </Card>
    </div>
  )
}
