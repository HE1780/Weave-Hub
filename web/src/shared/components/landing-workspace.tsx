import { useNavigate } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { Grid, User as UserIcon, ChevronRight } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { useMySkills } from '@/shared/hooks/use-user-queries'
import { agentsApi, type AgentDto } from '@/api/client'

/**
 * "工作台" right rail (col-span-4). Guest sees sign-in CTA; authenticated user
 * sees their top 3 skills + top 3 agents. Visual mirrors prototype lines 293-368.
 *
 * Both data hooks are gated on isLoggedIn to avoid 401-spamming the auth
 * endpoint for guests, and the agent list is filtered by `ownerId === user.userId`
 * (mirrors my-weave.tsx) so we surface the user's own agents, not all public ones.
 */
export function LandingWorkspace() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const isLoggedIn = !!user

  const { data: mySkillsPage } = useMySkills({ page: 0, size: 3 }, isLoggedIn)
  const { data: ownAgentsList } = useQuery({
    queryKey: ['agents', 'my', user?.userId],
    queryFn: async () => {
      const page = await agentsApi.list({ page: 0, size: 200 })
      return page.items
    },
    enabled: isLoggedIn,
  })
  const mySkills = mySkillsPage?.items?.slice(0, 3) ?? []
  const myAgents = (ownAgentsList ?? [])
    .filter((a: AgentDto) => a.ownerId === user?.userId)
    .slice(0, 3)

  return (
    <section className="lg:col-span-4 h-full">
      <div className="glass-card h-full flex flex-col bg-brand-50/20 border-white/80 p-8">
        <div className="flex items-center gap-4 mb-10">
          <div className="w-12 h-12 rounded-2xl bg-brand-600 text-white flex items-center justify-center shadow-xl shadow-brand-600/20">
            <Grid size={24} />
          </div>
          <h3 className="text-2xl font-black text-slate-800 tracking-tighter">
            {t('landing.workspace.title')}
          </h3>
        </div>

        <div className="flex-1">
          <AnimatePresence mode="wait">
            {!isLoggedIn ? (
              <motion.div
                key="guest"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="flex flex-col items-center justify-center h-full py-12 text-center"
              >
                <div className="w-24 h-24 rounded-full bg-white border border-brand-50 flex items-center justify-center text-slate-200 mb-8 shadow-inner shadow-slate-100">
                  <UserIcon size={48} />
                </div>
                <p className="text-sm font-black text-slate-800 mb-3 uppercase tracking-widest leading-none">
                  {t('landing.workspace.guestEyebrow')}
                </p>
                <p className="text-xs text-slate-400 mb-10 max-w-[200px] font-medium leading-relaxed">
                  {t('landing.workspace.guestPrompt')}
                </p>
                <button
                  onClick={() => navigate({ to: '/login', search: { returnTo: '/' } })}
                  className="btn-primary w-full justify-center !rounded-2xl"
                >
                  {t('landing.workspace.guestCta')}
                </button>
              </motion.div>
            ) : (
              <motion.div
                key="auth"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="space-y-8"
              >
                <div className="space-y-4">
                  <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-[0.3em] text-slate-400 px-1">
                    <span>{t('landing.workspace.skillsSection')}</span>
                    <span className="text-brand-600">
                      {mySkills.length} {t('landing.workspace.pcs')}
                    </span>
                  </div>
                  {mySkills.map((skill) => (
                    <div
                      key={skill.id}
                      onClick={() => navigate({ to: `/space/${skill.namespace}/${skill.slug}` })}
                      className="flex items-center justify-between p-4 rounded-2xl bg-white/70 hover:bg-white border border-transparent hover:border-brand-200 transition-all cursor-pointer group shadow-sm"
                    >
                      <span className="text-sm font-bold text-slate-700 group-hover:text-brand-600 uppercase tracking-tight">
                        {skill.displayName}
                      </span>
                      <ChevronRight size={16} className="text-slate-300 group-hover:text-brand-500" />
                    </div>
                  ))}
                </div>

                <div className="space-y-4">
                  <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-[0.3em] text-slate-400 px-1">
                    <span>{t('landing.workspace.agentsSection')}</span>
                    <span className="text-brand-600">
                      {myAgents.length} {t('landing.workspace.pcs')}
                    </span>
                  </div>
                  {myAgents.map((agent) => (
                    <div
                      key={agent.id}
                      onClick={() =>
                        navigate({
                          to: '/agents/$namespace/$slug',
                          params: { namespace: agent.namespace, slug: agent.slug },
                        })
                      }
                      className="flex items-center justify-between p-4 rounded-2xl bg-white/70 hover:bg-white border border-transparent hover:border-brand-200 transition-all cursor-pointer group shadow-sm"
                    >
                      <span className="text-sm font-bold text-slate-700 group-hover:text-brand-600 uppercase tracking-tight">
                        {agent.displayName}
                      </span>
                      <ChevronRight size={16} className="text-slate-300 group-hover:text-brand-500" />
                    </div>
                  ))}
                </div>

                <button
                  onClick={() => navigate({ to: '/dashboard' })}
                  className="w-full py-5 rounded-2xl bg-white text-slate-800 text-[11px] font-black uppercase tracking-[0.3em] hover:bg-brand-600 hover:text-white transition-all shadow-sm border border-slate-100"
                >
                  {t('landing.workspace.openPanel')}
                </button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </section>
  )
}
