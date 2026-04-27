import { LandingHero } from '@/shared/components/landing-hero'
import { LandingHotSection } from '@/shared/components/landing-hot-section'
import { LandingRecentSection } from '@/shared/components/landing-recent-section'
import { LandingWorkspace } from '@/shared/components/landing-workspace'

/**
 * 知连 WeaveHub landing page — 4-section IA per spec §3.1.
 *
 * Visual mirrors prototype web/weavehub---知连/src/App.tsx layout.
 */
export function LandingPage() {
  return (
    <>
      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-12 md:py-20 space-y-24">
        <LandingHero />
        <LandingHotSection />
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10">
          <LandingRecentSection />
          <LandingWorkspace />
        </div>
      </main>
    </>
  )
}
