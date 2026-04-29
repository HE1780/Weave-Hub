import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Flame, Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Zap, Grid, type LucideIcon } from 'lucide-react'
import { ResourceCard, type ResourceCardData } from './resource-card'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useAgents } from '@/features/agent/use-agents'
import { SkeletonList } from './skeleton-loader'

/**
 * "热门推荐" section — mixed skill+agent grid (3 + 3 = 6 cards).
 */
/**
 * Returns a stable pseudo-random icon based on id/name to keep variety
 * without causing icon flicker between renders.
 */
const TYPE_ICON_POOL: LucideIcon[] = [Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Zap, Grid]
function pickIcon(seed: string | number): LucideIcon {
  const hash = String(seed).split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
  return TYPE_ICON_POOL[hash % TYPE_ICON_POOL.length]
}

/**
 * Formats ISO time into a short relative text for card footer display.
 */
function relativeTime(iso?: string): string {
  if (!iso) return ''
  const ms = Date.now() - new Date(iso).getTime()
  if (ms < 0 || Number.isNaN(ms)) return ''
  const minutes = Math.floor(ms / 60000)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  if (days > 0) return `${days}d ago`
  if (hours > 0) return `${hours}h ago`
  if (minutes > 0) return `${minutes}m ago`
  return 'now'
}

export function LandingHotSection() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: skills, isLoading: skillsLoading } = useSearchSkills({
    sort: 'downloads',
    size: 3,
  })
  const { data: agents, isLoading: agentsLoading } = useAgents()

  const isLoading = skillsLoading || agentsLoading

  const skillCards: ResourceCardData[] = (skills?.items ?? []).slice(0, 3).map((s) => ({
    id: s.id,
    title: s.displayName,
    type: 'skill',
    category: s.summary ?? undefined,
    updatedAt: relativeTime(s.updatedAt),
    href: `/space/${s.namespace}/${encodeURIComponent(s.slug)}`,
    icon: pickIcon(s.id),
  }))

  const agentCards: ResourceCardData[] = (agents ?? []).slice(0, 3).map((a) => ({
    id: a.slug,
    title: a.slug,
    type: 'agent',
    category: a.description,
    updatedAt: '',
    href: `/agents/${a.namespace ?? 'global'}/${a.slug}`,
    icon: pickIcon(a.slug),
  }))

  // Interleave skills and agents: skill, agent, skill, agent, skill, agent
  const mixed: ResourceCardData[] = []
  for (let i = 0; i < 3; i += 1) {
    if (skillCards[i]) mixed.push(skillCards[i])
    if (agentCards[i]) mixed.push(agentCards[i])
  }

  return (
    <section>
      <div className="flex items-center justify-between mb-12">
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-3xl bg-brand-50/80 border border-brand-100 flex items-center justify-center text-brand-600 shadow-sm">
            <Flame size={26} />
          </div>
          <div className="text-left">
            <div className="h-1 w-8 bg-brand-500 rounded-full mb-3"></div>
            <h3 className="text-xs font-black text-slate-300 uppercase tracking-[0.3em] mb-2 text-left">
              {t('landing.hot.eyebrow')}
            </h3>
            <h2 className="text-4xl font-black text-slate-800 tracking-tight text-left">
              {t('landing.hot.title')}
            </h2>
          </div>
        </div>
        <button
          onClick={() => navigate({ to: '/search', search: { q: '', sort: 'downloads', page: 0, starredOnly: false } })}
          className="btn-secondary group !bg-transparent border-none hover:bg-white/40 !rounded-full"
        >
          {t('landing.hot.browseAll')}{' '}
          <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
        </button>
      </div>

      {isLoading ? (
        <SkeletonList count={6} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-10">
          {mixed.map((resource, idx) => (
            <ResourceCard key={resource.id} variant="featured" resource={resource} index={idx} />
          ))}
        </div>
      )}
    </section>
  )
}
