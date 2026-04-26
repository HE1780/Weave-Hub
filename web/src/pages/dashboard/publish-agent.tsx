import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api/client'
import { usePublishAgent } from '@/features/agent/use-publish-agent'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Label } from '@/shared/ui/label'
import {
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

export function AgentPublishPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [file, setFile] = useState<File | null>(null)
  const [namespace, setNamespace] = useState<string>(NAMESPACE_PLACEHOLDER)
  const [visibility, setVisibility] = useState<AgentDtoVisibility>('PRIVATE')

  const { data: namespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()
  const publish = usePublishAgent()

  const canSubmit =
    !!file && namespace !== NAMESPACE_PLACEHOLDER && !publish.isPending

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const next = e.target.files?.[0] ?? null
    setFile(next)
  }

  const handleSubmit = async () => {
    if (!file) {
      toast.error(t('agents.publish.errorMissingFile'))
      return
    }
    if (namespace === NAMESPACE_PLACEHOLDER) {
      toast.error(t('agents.publish.errorMissingNamespace'))
      return
    }
    try {
      const result = await publish.mutateAsync({ namespace, file, visibility })
      const isAutoPublished = result.status === 'PUBLISHED'
      toast.success(
        isAutoPublished
          ? t('agents.publish.successAutoPublished')
          : t('agents.publish.successPendingReview'),
      )
      navigate({ to: '/agents/$name', params: { name: result.slug } })
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.message
          : t('agents.publish.errorGeneric')
      toast.error(message)
    }
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <DashboardPageHeader
        title={t('agents.publish.title')}
        subtitle={t('agents.publish.subtitle')}
      />

      <Card className="p-6 space-y-6 mt-6">
        <div className="space-y-2">
          <Label htmlFor="agent-package-file">{t('agents.publish.fileLabel')}</Label>
          <input
            id="agent-package-file"
            type="file"
            accept=".zip,application/zip"
            onChange={handleFileChange}
            className="block w-full text-sm text-foreground file:mr-3 file:rounded-lg file:border file:border-input file:bg-secondary file:px-3 file:py-2 file:text-sm file:font-medium hover:file:bg-secondary/80"
          />
          {file ? (
            <p className="text-sm text-muted-foreground">
              {t('agents.publish.fileSelected', {
                name: file.name,
                size: Math.round(file.size / 1024),
              })}
            </p>
          ) : (
            <p className="text-sm text-muted-foreground">
              {t('agents.publish.filePlaceholder')}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="agent-namespace">{t('agents.publish.namespaceLabel')}</Label>
          <Select value={namespace} onValueChange={setNamespace}>
            <SelectTrigger id="agent-namespace">
              <SelectValue placeholder={t('agents.publish.namespacePlaceholder')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NAMESPACE_PLACEHOLDER} disabled>
                {t('agents.publish.namespacePlaceholder')}
              </SelectItem>
              {!isLoadingNamespaces &&
                namespaces?.map((ns) => (
                  <SelectItem key={ns.slug} value={ns.slug}>
                    {ns.displayName} ({ns.slug})
                  </SelectItem>
                ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>{t('agents.publish.visibilityLabel')}</Label>
          <div className="space-y-2">
            <label className="flex items-start gap-2 cursor-pointer">
              <input
                type="radio"
                name="visibility"
                value="PRIVATE"
                checked={visibility === 'PRIVATE'}
                onChange={() => setVisibility('PRIVATE')}
                className="mt-1"
              />
              <span className="text-sm">{t('agents.publish.visibilityPrivate')}</span>
            </label>
            <label className="flex items-start gap-2 cursor-pointer">
              <input
                type="radio"
                name="visibility"
                value="NAMESPACE_ONLY"
                checked={visibility === 'NAMESPACE_ONLY'}
                onChange={() => setVisibility('NAMESPACE_ONLY')}
                className="mt-1"
              />
              <span className="text-sm">{t('agents.publish.visibilityNamespace')}</span>
            </label>
            <label className="flex items-start gap-2 cursor-pointer">
              <input
                type="radio"
                name="visibility"
                value="PUBLIC"
                checked={visibility === 'PUBLIC'}
                onChange={() => setVisibility('PUBLIC')}
                className="mt-1"
              />
              <span className="text-sm">{t('agents.publish.visibilityPublic')}</span>
            </label>
          </div>
        </div>

        <div className="flex justify-end pt-2">
          <Button onClick={handleSubmit} disabled={!canSubmit}>
            {publish.isPending ? t('agents.publish.submitting') : t('agents.publish.submit')}
          </Button>
        </div>
      </Card>
    </div>
  )
}
