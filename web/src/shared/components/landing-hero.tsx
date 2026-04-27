import { motion } from 'motion/react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Search as SearchIcon, Sparkles } from 'lucide-react'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

/**
 * Hero section of the WeaveHub landing page.
 *
 * Visual mirrors web/weavehub---知连/src/App.tsx lines 174-228.
 */
export function LandingHero() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const handleSearch = (query: string) => {
    const normalized = normalizeSearchQuery(query)
    navigate({
      to: '/search',
      search: { q: normalized, sort: 'relevance', page: 0, starredOnly: false },
    })
  }

  return (
    <section className="relative">
      <div className="flex flex-col items-center text-center max-w-4xl mx-auto space-y-10">
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className="inline-flex items-center gap-2.5 px-5 py-2 rounded-full bg-white/60 backdrop-blur-md text-brand-700 text-[11px] font-black uppercase tracking-[0.2em] border border-white shadow-sm"
        >
          <Sparkles size={14} className="text-brand-500" fill="currentColor" fillOpacity={0.2} />
          {t('landing.hero.badge')}
        </motion.div>

        <motion.h1
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          className="text-6xl md:text-8xl font-black tracking-tight leading-[0.9] text-slate-900"
        >
          {t('landing.hero.titleStart')} <br />
          <span className="brand-gradient font-serif italic font-normal tracking-tight">
            {t('landing.hero.titleAccent')}
          </span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2, duration: 0.8 }}
          className="text-slate-400 text-lg md:text-2xl max-w-2xl font-medium leading-relaxed"
        >
          {t('landing.hero.subtitle')}
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4, duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          className="w-full max-w-3xl flex flex-col sm:flex-row items-stretch gap-4 pt-6"
        >
          <div className="flex-1 glass-card !p-5 !rounded-3xl flex items-center gap-5 bg-white/80 focus-within:bg-white focus-within:ring-8 ring-brand-500/5 transition-all border-white border-2 shadow-2xl shadow-brand-500/5">
            <SearchIcon size={24} className="text-slate-300" />
            <input
              type="text"
              placeholder={t('landing.hero.searchPlaceholder')}
              className="flex-1 bg-transparent outline-none text-lg font-medium placeholder:text-slate-300"
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  handleSearch((e.target as HTMLInputElement).value)
                }
              }}
            />
            <div className="hidden sm:flex items-center gap-1.5 opacity-30">
              <kbd className="px-2.5 py-1 rounded bg-slate-100 text-[11px] font-mono font-bold">⌘</kbd>
              <kbd className="px-2.5 py-1 rounded bg-slate-100 text-[11px] font-mono font-bold">K</kbd>
            </div>
          </div>
          <button
            onClick={() => handleSearch('')}
            className="btn-primary justify-center px-10 shadow-2xl !rounded-3xl"
          >
            {t('landing.hero.exploreCta')}
          </button>
        </motion.div>
      </div>
    </section>
  )
}
