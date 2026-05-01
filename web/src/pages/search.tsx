import { startTransition, useEffect, useRef, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { AgentCard } from '@/features/agent/agent-card'
import { useAgents } from '@/features/agent/use-agents'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useMyStars } from '@/shared/hooks/use-user-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'
import { Tabs, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

type SearchTab = 'skills' | 'agents'

const PAGE_SIZE = 12

function blurActiveElement() {
  if (typeof document === 'undefined' || typeof HTMLElement === 'undefined') {
    return
  }

  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur()
  }
}

function scrollToTopOnPageChange() {
  if (typeof window === 'undefined') {
    return () => {}
  }

  let secondFrame = 0
  const firstFrame = window.requestAnimationFrame(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
    secondFrame = window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: 'auto' })
    })
  })

  return () => {
    window.cancelAnimationFrame(firstFrame)
    if (secondFrame) {
      window.cancelAnimationFrame(secondFrame)
    }
  }
}

/**
 * Skill discovery page with synchronized URL state.
 *
 * Search text, sorting, pagination, and the starred-only filter are mirrored into router search
 * params so the page can be shared, restored, and revisited without losing state.
 */
function filterStarredSkills(skills: SkillSummary[], query: string): SkillSummary[] {
  const normalizedQuery = query.trim().toLowerCase()
  if (!normalizedQuery) {
    return skills
  }

  return skills.filter((skill) =>
    [skill.displayName, skill.summary, skill.namespace, skill.slug]
      .filter(Boolean)
      .some((value) => value!.toLowerCase().includes(normalizedQuery))
  )
}

function sortStarredSkills(skills: SkillSummary[], sort: string): SkillSummary[] {
  const sorted = [...skills]
  if (sort === 'downloads') {
    return sorted.sort((left, right) => right.downloadCount - left.downloadCount)
  }
  if (sort === 'newest' || sort === 'relevance') {
    return sorted.sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
  }
  return sorted
}

export function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const searchParams = useSearch({ from: '/search' })
  const { isAuthenticated } = useAuth()

  const q = normalizeSearchQuery(searchParams.q || '')
  const selectedLabel = searchParams.label || ''
  const sort = searchParams.sort || 'newest'
  const page = searchParams.page ?? 0
  const starredOnly = searchParams.starredOnly ?? false
  const tab: SearchTab = searchParams.tab === 'agents' ? 'agents' : 'skills'
  const [queryInput, setQueryInput] = useState(q)
  const previousPageRef = useRef(page)

  const baseSearch = { q, label: selectedLabel, sort, page, starredOnly, tab }

  useEffect(() => {
    setQueryInput(q)
  }, [q])

  useEffect(() => {
    if (previousPageRef.current !== page) {
      blurActiveElement()
      const cleanupScroll = scrollToTopOnPageChange()

      previousPageRef.current = page
      return () => {
        cleanupScroll()
      }
    }

    previousPageRef.current = page
  }, [page])

  const { data, isLoading, isFetching } = useSearchSkills({
    q,
    label: selectedLabel || undefined,
    sort,
    page,
    size: PAGE_SIZE,
    starredOnly,
  })
  const { data: labels } = useVisibleLabels()
  const {
    data: starredSkills,
    isLoading: isLoadingStarred,
    isFetching: isFetchingStarred,
  } = useMyStars(starredOnly && isAuthenticated)
  const {
    data: agentsData,
    isLoading: isLoadingAgents,
    isFetching: isFetchingAgents,
  } = useAgents({ q: q || undefined })
  useEffect(() => {
    // Debounce URL updates while the user is typing so query state stays shareable without
    // triggering a navigation on every keystroke.
    const normalizedQuery = normalizeSearchQuery(queryInput)
    if (normalizedQuery === q) {
      return
    }

    if (!normalizedQuery) {
      startTransition(() => {
        navigate({ to: '/search', search: { ...baseSearch, q: '', page: 0 }, replace: page === 0 })
      })
      return
    }

    const timeoutId = window.setTimeout(() => {
      startTransition(() => {
        navigate({ to: '/search', search: { ...baseSearch, q: normalizedQuery, page: 0 }, replace: true })
      })
    }, 250)

    return () => window.clearTimeout(timeoutId)
  }, [navigate, page, q, queryInput, selectedLabel, sort, starredOnly, tab])

  const handleSearch = (query: string) => {
    const normalizedQuery = normalizeSearchQuery(query)
    setQueryInput(query)
    startTransition(() => {
      navigate({ to: '/search', search: { ...baseSearch, q: normalizedQuery, page: 0 }, replace: true })
    })
  }

  const handleSortChange = (newSort: string) => {
    navigate({ to: '/search', search: { ...baseSearch, sort: newSort, page: 0 } })
  }

  const handlePageChange = (newPage: number) => {
    blurActiveElement()
    navigate({ to: '/search', search: { ...baseSearch, page: newPage } })
  }

  const handleLabelToggle = (label: string) => {
    const nextLabel = selectedLabel === label ? '' : label
    navigate({ to: '/search', search: { ...baseSearch, label: nextLabel, page: 0 } })
  }

  const handleStarredToggle = () => {
    if (!isAuthenticated) {
      navigate({
        to: '/login',
        search: {
          returnTo: `${window.location.pathname}${window.location.search}${window.location.hash}`,
        },
      })
      return
    }

    navigate({ to: '/search', search: { ...baseSearch, page: 0, starredOnly: !starredOnly } })
  }

  const handleTabChange = (nextTab: SearchTab) => {
    navigate({ to: '/search', search: { ...baseSearch, tab: nextTab, page: 0 } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const filteredStarredSkills = starredOnly
    ? sortStarredSkills(filterStarredSkills(starredSkills ?? [], q), sort)
    : []
  const starredPageItems = starredOnly
    ? filteredStarredSkills.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
    : []
  const totalPages = starredOnly
    ? Math.ceil(filteredStarredSkills.length / PAGE_SIZE)
    : data
      ? Math.ceil(data.total / data.size)
      : 0
  const displayItems = starredOnly ? starredPageItems : (data?.items ?? [])
  const isPageLoading = starredOnly ? isLoadingStarred : isLoading
  const isUpdatingResults = starredOnly ? isFetchingStarred && !isLoadingStarred : isFetching && !isLoading
  const resultCount = starredOnly ? filteredStarredSkills.length : (data?.total ?? 0)

  const filteredAgents = agentsData ?? []
  const isAgentsLoading = isLoadingAgents
  const isAgentsFetching = isFetchingAgents && !isLoadingAgents

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      {/* Search Bar */}
      <div className="max-w-3xl mx-auto">
        <SearchBar
          value={queryInput}
          isSearching={tab === 'skills' ? isUpdatingResults : isAgentsFetching}
          onChange={setQueryInput}
          onSearch={handleSearch}
        />
      </div>

      {/* Skills/Agents tab switcher */}
      <Tabs value={tab} onValueChange={(value) => handleTabChange(value as SearchTab)}>
        <TabsList>
          <TabsTrigger value="skills">{t('search.tabSkills')}</TabsTrigger>
          <TabsTrigger value="agents">{t('search.tabAgents')}</TabsTrigger>
        </TabsList>
      </Tabs>

      {tab === 'skills' ? (
        <>
          {/* Sort And Filters */}
          <div className="space-y-4">
            <div className="flex items-center justify-between flex-wrap gap-4">
              <div className="flex items-center gap-3">
                <span className="text-sm font-medium text-muted-foreground">{t('search.sort.label')}</span>
                <div className="flex gap-2">
                  <Button
                    variant={sort === 'relevance' ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => handleSortChange('relevance')}
                  >
                    {t('search.sort.relevance')}
                  </Button>
                  <Button
                    variant={sort === 'downloads' ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => handleSortChange('downloads')}
                  >
                    {t('search.sort.downloads')}
                  </Button>
                  <Button
                    variant={sort === 'newest' ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => handleSortChange('newest')}
                  >
                    {t('search.sort.newest')}
                  </Button>
                </div>
              </div>

              {resultCount > 0 && (
                <div className="text-sm text-muted-foreground">
                  {t('search.results', { count: resultCount })}
                </div>
              )}
            </div>

            {isUpdatingResults ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                <span>{t('search.loadingMore')}</span>
              </div>
            ) : null}

            <div className="flex items-center gap-3">
              <span className="text-sm font-medium text-muted-foreground">{t('search.filters.label')}</span>
              <Button
                variant={starredOnly ? 'default' : 'outline'}
                size="sm"
                onClick={handleStarredToggle}
              >
                {t('search.filterStarred')}
              </Button>
              {!starredOnly && labels?.map((label) => (
                <Button
                  key={label.slug}
                  variant={selectedLabel === label.slug ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => handleLabelToggle(label.slug)}
                >
                  {label.displayName}
                </Button>
              ))}
            </div>
          </div>

          {/* Skill results */}
          {isPageLoading ? (
            <SkeletonList count={PAGE_SIZE} />
          ) : displayItems.length > 0 ? (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                {displayItems.map((skill, idx) => (
                  <div key={skill.id} className={`h-full animate-fade-up delay-${Math.min(idx % 6 + 1, 6)}`}>
                    <SkillCard
                      skill={skill}
                      highlightStarred
                      onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                    />
                  </div>
                ))}
              </div>
              {totalPages > 1 && (
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  onPageChange={handlePageChange}
                />
              )}
            </>
          ) : (
            <EmptyState
              title={starredOnly ? t('search.noStarredResults') : t('search.noResults')}
              description={
                starredOnly
                  ? (q ? t('search.noStarredResultsFor', { q }) : t('search.noStarredSkills'))
                  : (q ? t('search.noResultsFor', { q }) : undefined)
              }
            />
          )}
        </>
      ) : (
        <>
          {/* Agent results */}
          {isAgentsFetching ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span>{t('search.loadingMore')}</span>
            </div>
          ) : null}

          {isAgentsLoading ? (
            <SkeletonList count={PAGE_SIZE} />
          ) : filteredAgents.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {filteredAgents.map((agent, idx) => (
                <div
                  key={`${agent.namespace ?? 'global'}/${agent.slug}`}
                  className={`h-full animate-fade-up delay-${Math.min((idx % 6) + 1, 6)}`}
                >
                  <AgentCard agent={agent} />
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              title={t('search.emptyAgents')}
              description={q ? t('search.noResultsFor', { q }) : undefined}
            />
          )}
        </>
      )}
    </div>
  )
}
