import { Link } from '@tanstack/react-router'
import { motion } from 'motion/react'
import { Clock, ChevronRight, Grid, type LucideIcon } from 'lucide-react'

export interface ResourceCardData {
  id: string | number
  title: string
  type: 'skill' | 'agent'
  category?: string
  updatedAt: string
  href: string
  icon?: LucideIcon
}

interface ResourceCardProps {
  resource: ResourceCardData
  variant: 'featured' | 'compact'
  index: number
}

/**
 * Visual card for one skill or agent resource on the landing page.
 *
 * Two variants:
 * - `featured` — used in 热门推荐, 3-column grid, large icon container + EXPLORE row
 * - `compact` — used in 全域动态流, 2-column grid, smaller, icon left + title right + type/time row
 *
 * Visual className strings mirror the WeaveHub prototype at
 * web/weavehub---知连/src/App.tsx (lines 70-104 and 267-286).
 */
export function ResourceCard({ resource, variant, index }: ResourceCardProps) {
  const Icon = resource.icon ?? Grid

  if (variant === 'compact') {
    return (
      <motion.div
        initial={{ opacity: 0, x: -10 }}
        whileInView={{ opacity: 1, x: 0 }}
        viewport={{ once: true }}
        transition={{ delay: index * 0.1 }}
        className="glass-card !p-6 group flex items-start gap-6 hover:bg-white border-white/40 shadow-sm"
      >
        <Link to={resource.href} className="flex items-start gap-6 flex-1 no-underline">
          <div className="w-14 h-14 rounded-2xl bg-slate-50 flex items-center justify-center text-slate-400 group-hover:bg-brand-50 group-hover:text-brand-600 transition-all duration-500 border border-white shrink-0">
            <Icon size={26} />
          </div>
          <div className="flex-1 pt-1">
            <h4 className="font-bold text-slate-800 text-lg group-hover:text-brand-600 transition-all tracking-tight leading-tight">
              {resource.title}
            </h4>
            <div className="flex items-center justify-between mt-3 text-[10px] font-black uppercase tracking-widest text-slate-400">
              <span className="bg-slate-100 px-2 py-0.5 rounded-md">{resource.type}</span>
              <span className="flex items-center gap-1">
                <Clock size={10} /> {resource.updatedAt}
              </span>
            </div>
          </div>
        </Link>
      </motion.div>
    )
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ delay: index * 0.05, duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
      className="glass-card group flex flex-col justify-between h-full"
    >
      <Link to={resource.href} className="no-underline flex flex-col justify-between h-full">
        <div>
          <div className="flex items-start justify-between mb-6">
            <div className="w-14 h-14 rounded-2xl bg-brand-50 flex items-center justify-center text-brand-600 group-hover:bg-brand-600 group-hover:text-white transition-all duration-500 shadow-sm border border-white">
              <Icon size={26} />
            </div>
            <span className="text-[10px] uppercase tracking-widest font-black px-3 py-1 rounded-lg bg-slate-100/80 text-slate-500 border border-white">
              {resource.type}
            </span>
          </div>
          <h3 className="text-xl font-bold text-slate-800 group-hover:text-brand-600 transition-colors tracking-tight mb-2">
            {resource.title}
          </h3>
          {resource.category && (
            <p className="text-xs text-slate-400 font-medium tracking-wide uppercase italic">
              {resource.category}
            </p>
          )}
        </div>
        <div className="mt-8 pt-5 border-t border-brand-50/50 flex items-center justify-between text-[11px] text-slate-400 font-bold uppercase tracking-tighter">
          <span className="flex items-center gap-1.5 opacity-70">
            <Clock size={14} /> {resource.updatedAt}
          </span>
          <div className="text-brand-600 flex items-center gap-1 group-hover:translate-x-1 transition-transform">
            EXPLORE <ChevronRight size={14} />
          </div>
        </div>
      </Link>
    </motion.div>
  )
}
