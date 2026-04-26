import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'
import { cn } from '@/shared/lib/utils'

interface Props {
  content: string
  className?: string
}

// GFM intentionally OFF (ADR §8.3): smaller sanitization surface for user bodies.
export function CommentMarkdownRenderer({ content, className }: Props) {
  return (
    <div className={cn('prose prose-sm max-w-none break-words text-foreground/90', className)}>
      <ReactMarkdown rehypePlugins={[rehypeSanitize]}>{content}</ReactMarkdown>
    </div>
  )
}
