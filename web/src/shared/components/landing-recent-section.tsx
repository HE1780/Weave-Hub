import { useTranslation } from 'react-i18next'
import { Zap, Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Grid, PlusCircle, type LucideIcon } from 'lucide-react'
import { ResourceCard, type ResourceCardData } from './resource-card'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { SkeletonList } from './skeleton-loader'

/**
 * Returns a stable pseudo-random icon based on id/name to keep variety
 * without causing icon flicker between renders.
 */
const TYPE_ICON_POOL: LucideIcon[] = [Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Zap, Grid, PlusCircle]
function pickIcon(seed: string | number): LucideIcon {
  const hash = String(seed).split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
  return TYPE_ICON_POOL[hash % TYPE_ICON_POOL.length]
}

/**
 * Formats ISO time into a compact relative text for recent cards.
 */
function relativeTime(iso?: string): string {
  if (!iso) return ''
  const ms = Date.now() - new Date(iso).getTime()
  if (ms < 0 || Number.isNaN(ms)) return ''
  const minutes = Math.floor(ms / 60000)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  if (days > 0) return `${days}d`
  if (hours > 0) return `${hours}h`
  if (minutes > 0) return `${minutes}m`
  return 'now'
}

/**
 * "最近更新" section — compact grid with 4 newest skill cards.
 */
export function LandingRecentSection() {
  const { t } = useTranslation()
  const { data: skills, isLoading: skillsLoading } = useSearchSkills({
    sort: 'newest',
    size: 4,
  })

  const isLoading = skillsLoading

  const latestCards: ResourceCardData[] = (skills?.items ?? []).slice(0, 4).map((s) => ({
    id: s.id,
    title: s.displayName,
    type: 'skill',
    category: s.summary ?? undefined,
    updatedAt: relativeTime(s.updatedAt),
    href: `/space/${s.namespace}/${encodeURIComponent(s.slug)}`,
    icon: pickIcon(s.id),
  }))

  return (
    <section className="lg:col-span-8 flex flex-col">
      <div className="flex items-center gap-4 mb-10">
        <div className="w-14 h-14 rounded-3xl bg-white border border-brand-50 flex items-center justify-center text-brand-600 shadow-sm">
          <Zap size={28} />
        </div>
        <div>
          <h3 className="text-xs font-black text-slate-300 uppercase tracking-[0.3em] mb-1">
            {t('landing.recent.eyebrow')}
          </h3>
          <h3 className="text-4xl font-black text-slate-800 tracking-tighter">
            {t('landing.recent.title')}
          </h3>
        </div>
      </div>

      {isLoading ? (
        <SkeletonList count={4} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 flex-1">
          {latestCards.map((resource, idx) => (
            <ResourceCard key={resource.id} variant="compact" resource={resource} index={idx} />
          ))}
        </div>
      )}
    </section>
  )
}
