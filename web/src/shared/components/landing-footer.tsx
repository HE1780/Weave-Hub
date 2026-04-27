import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { Layout, Zap, Grid, Sparkles } from 'lucide-react'

/**
 * Landing page footer. Visual mirrors prototype lines 372-424.
 *
 * Brand block deliberately renders just "WeaveHub" (English only) — different
 * from the header's "知连 WeaveHub" — per design spec §3.7 locale-aware brand.
 */
export function LandingFooter() {
  const { t } = useTranslation()

  return (
    <footer className="mt-40 border-t border-brand-50/50 bg-white/40 backdrop-blur-md pt-20 pb-10 px-6">
      <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-start gap-16 mb-20">
        <div className="space-y-8 max-w-sm">
          <div data-testid="footer-brand" className="text-3xl font-black brand-gradient flex items-center gap-3">
            <Layout size={32} className="text-brand-600" />
            WeaveHub
          </div>
          <p className="text-slate-400 text-lg leading-relaxed font-medium">
            {t('landing.footer.tagline')}
          </p>
          <div className="flex items-center gap-6">
            {[Zap, Grid, Sparkles, Layout].map((Icon, i) => (
              <div
                key={i}
                className="text-slate-400 hover:text-brand-600 transition-all cursor-pointer hover:scale-110"
              >
                <Icon size={24} />
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-2 gap-20">
          <div className="space-y-8 font-black uppercase tracking-[0.2em] text-[11px]">
            <h4 className="text-slate-900 border-b-2 border-brand-500 w-fit pb-1">
              {t('landing.footer.documentation')}
            </h4>
            <ul className="space-y-4 text-slate-400">
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.apiReferences')}</a></li>
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.cloudSync')}</a></li>
              <li><Link to="/privacy" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.security')}</Link></li>
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.integration')}</a></li>
            </ul>
          </div>
          <div className="space-y-8 font-black uppercase tracking-[0.2em] text-[11px]">
            <h4 className="text-slate-900 border-b-2 border-brand-500 w-fit pb-1">
              {t('landing.footer.community')}
            </h4>
            <ul className="space-y-4 text-slate-400">
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.openSource')}</a></li>
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.forum')}</a></li>
              <li><Link to="/privacy" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.privacy')}</Link></li>
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.support')}</a></li>
            </ul>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-6 pt-10 border-t border-brand-50/30">
        <span className="text-[10px] font-mono font-bold text-slate-300 uppercase tracking-[0.5em]">
          {t('landing.footer.copyright')}
        </span>
        <div className="flex items-center gap-8">
          <span className="text-[10px] font-mono font-bold text-slate-300 uppercase tracking-[0.2em]">
            {t('landing.footer.version')}
          </span>
          <div className="flex items-center gap-2.5 px-4 py-1.5 rounded-full bg-white text-[11px] font-black text-brand-600 uppercase tracking-widest border border-brand-50 shadow-sm">
            <div className="w-2 h-2 rounded-full bg-brand-500 animate-ping"></div>
            {t('landing.footer.networkReady')}
          </div>
        </div>
      </div>
    </footer>
  )
}
