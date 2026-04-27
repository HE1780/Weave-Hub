import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Zap, Grid, type LucideIcon } from 'lucide-react'
import { ResourceCard, type ResourceCardData } from './resource-card'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useAgents } from '@/features/agent/use-agents'
import { SkeletonList } from './skeleton-loader'

/**
 * "热门推荐" section — mixed skill+agent grid (3 + 3 = 6 cards),
 * visual mirrors web/weavehub---知连/src/App.tsx lines 230-250.
 */
const TYPE_ICON_POOL: LucideIcon[] = [Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Zap, Grid]

function pickIcon(seed: string | number): LucideIcon {
  const hash = String(seed).split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
  return TYPE_ICON_POOL[hash % TYPE_ICON_POOL.length]
}

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
    id: a.name,
    title: a.name,
    type: 'agent',
    category: a.description,
    updatedAt: '',
    href: `/agents/${a.namespace ?? 'global'}/${a.name}`,
    icon: pickIcon(a.name),
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
        <div>
          <h3 className="text-xs font-black text-brand-600 uppercase tracking-[0.3em] mb-3 flex items-center gap-2">
            <div className="h-1 w-8 bg-brand-500 rounded-full"></div>
            {t('landing.hot.eyebrow')}
          </h3>
          <h2 className="text-4xl font-black text-slate-800 tracking-tight">
            {t('landing.hot.title')}
          </h2>
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
