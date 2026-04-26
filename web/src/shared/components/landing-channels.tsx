import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Bot, PackageOpen } from 'lucide-react'

interface ChannelCardProps {
  to: string
  icon: React.ReactNode
  label: string
  tagline: string
  description: string
  cta: string
}

function ChannelCard({ to, icon, label, tagline, description, cta }: ChannelCardProps) {
  return (
    <Link
      to={to}
      className="group flex flex-col rounded-2xl border bg-white p-7 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md"
      style={{ borderColor: 'hsl(var(--border-card))' }}
    >
      <div className="flex items-center gap-3 mb-4">
        <div className="w-11 h-11 rounded-xl bg-brand-gradient flex items-center justify-center text-white">
          {icon}
        </div>
        <div>
          <div className="text-lg font-semibold" style={{ color: 'hsl(var(--foreground))' }}>
            {label}
          </div>
          <div className="text-xs uppercase tracking-wider" style={{ color: 'hsl(var(--text-secondary))' }}>
            {tagline}
          </div>
        </div>
      </div>
      <p className="text-sm leading-relaxed mb-5 flex-1" style={{ color: 'hsl(var(--text-secondary))' }}>
        {description}
      </p>
      <span
        className="text-sm font-medium transition-colors group-hover:underline"
        style={{ color: 'hsl(var(--primary))' }}
      >
        {cta} →
      </span>
    </Link>
  )
}

export function LandingChannelsSection() {
  const { t } = useTranslation()
  return (
    <section
      className="relative z-10 w-full px-6 py-16 md:py-20"
      style={{ background: 'var(--bg-page, hsl(var(--background)))' }}
    >
      <div className="max-w-5xl mx-auto">
        <div className="text-center mb-10">
          <h2
            className="text-3xl md:text-4xl font-bold tracking-tight mb-3"
            style={{ color: 'hsl(var(--foreground))' }}
          >
            {t('landing.channels.title')}
          </h2>
          <p
            className="text-base md:text-lg max-w-2xl mx-auto leading-relaxed"
            style={{ color: 'hsl(var(--text-secondary))' }}
          >
            {t('landing.channels.subtitle')}
          </p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <ChannelCard
            to="/skills"
            icon={<PackageOpen className="w-5 h-5" strokeWidth={2} />}
            label={t('landing.channels.skill.label')}
            tagline={t('landing.channels.skill.tagline')}
            description={t('landing.channels.skill.description')}
            cta={t('landing.channels.skill.cta')}
          />
          <ChannelCard
            to="/agents"
            icon={<Bot className="w-5 h-5" strokeWidth={2} />}
            label={t('landing.channels.agent.label')}
            tagline={t('landing.channels.agent.tagline')}
            description={t('landing.channels.agent.description')}
            cta={t('landing.channels.agent.cta')}
          />
        </div>
      </div>
    </section>
  )
}
