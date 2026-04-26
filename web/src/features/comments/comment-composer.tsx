import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CommentMarkdownRenderer } from './comment-markdown-renderer'

const MAX = 8192

interface Props {
  initialValue?: string
  onSubmit: (body: string) => Promise<void> | void
  isSubmitting: boolean
  submitLabel?: string
}

export function CommentComposer({
  initialValue = '',
  onSubmit,
  isSubmitting,
  submitLabel,
}: Props) {
  const { t } = useTranslation()
  const [body, setBody] = useState(initialValue)
  const [tab, setTab] = useState<'write' | 'preview'>('write')

  const trimmed = body.trim()
  const tooLong = body.length > MAX
  const empty = trimmed.length === 0
  const canSubmit = !empty && !tooLong && !isSubmitting

  return (
    <div className="space-y-2">
      <div className="flex gap-2 border-b">
        <button
          type="button"
          onClick={() => setTab('write')}
          className={
            tab === 'write'
              ? 'border-b-2 border-primary px-3 py-1 text-sm font-medium'
              : 'px-3 py-1 text-sm text-muted-foreground'
          }
        >
          {t('comments.composer.write')}
        </button>
        <button
          type="button"
          onClick={() => setTab('preview')}
          className={
            tab === 'preview'
              ? 'border-b-2 border-primary px-3 py-1 text-sm font-medium'
              : 'px-3 py-1 text-sm text-muted-foreground'
          }
        >
          {t('comments.composer.preview')}
        </button>
      </div>
      {tab === 'write' ? (
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={t('comments.composer.placeholder')}
          rows={5}
          className="w-full rounded border bg-background p-2 text-sm"
        />
      ) : (
        <div className="min-h-[8rem] rounded border bg-background p-2">
          <CommentMarkdownRenderer content={body} />
        </div>
      )}
      {tooLong && <p className="text-xs text-destructive">{t('comments.error.tooLong')}</p>}
      <button
        type="button"
        disabled={!canSubmit}
        onClick={async () => {
          await onSubmit(trimmed)
          setBody('')
          setTab('write')
        }}
        className="rounded bg-primary px-4 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
      >
        {submitLabel ?? t('comments.composer.submit')}
      </button>
    </div>
  )
}
