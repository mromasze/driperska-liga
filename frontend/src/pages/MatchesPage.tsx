import { useState } from 'react';
import { useMatches, useMatchDetails } from '../api/hooks/matches';
import { MatchCard } from '../components/match/MatchCard';
import { Button } from '../components/ui/Button';
import { CardGridSkeleton, EmptyState, ErrorState } from '../components/ui/States';
import type { MatchDetail } from '../api/types';

/** Nine keeps three clean rows of cards on desktop without a wall of detail requests per page. */
const PAGE_SIZE = 9;

/**
 * Every played match, paginated.
 *
 * The home page only ever showed the last six results, so older matches were reachable only by
 * guessing a URL. This is the full archive.
 *
 * Only APPROVED matches appear: anything earlier in the pipeline is either unfinished or still
 * waiting on someone's approval, and neither belongs on a public page.
 *
 * Ordering comes from the backend (`coalesce(started_at, created_at) desc`), and is deliberately not
 * re-sorted here — sorting one page of a paginated set client-side only produces a list that looks
 * ordered while the page boundaries say otherwise.
 */
export function MatchesPage() {
  const [page, setPage] = useState(0);
  const matches = useMatches({ status: 'APPROVED', page, size: PAGE_SIZE });
  const ids = (matches.data?.content ?? []).map((match) => match.id);
  const details = useMatchDetails(ids);
  const loaded = details
    .map((detail) => detail.data)
    .filter((detail): detail is MatchDetail => Boolean(detail));

  const totalPages = matches.data?.totalPages ?? 0;
  const total = matches.data?.totalElements ?? 0;

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="kicker text-gold">Archiwum</div>
          <h1 className="mt-1 font-display text-4xl">Wszystkie mecze</h1>
        </div>
        {total > 0 && (
          <span className="chip">
            <span className="num tabnum">{total}</span> rozegranych
          </span>
        )}
      </header>

      {matches.isError ? (
        <ErrorState error={matches.error} />
      ) : matches.isLoading ? (
        <CardGridSkeleton count={PAGE_SIZE} />
      ) : total === 0 ? (
        <EmptyState
          title="Brak rozegranych meczów"
          description="Gdy pierwszy mecz zostanie zatwierdzony, pojawi się tutaj."
        />
      ) : (
        <>
          {/* Cards arrive as their detail requests land, so the grid fills in rather than blocking
              on the slowest one. The skeleton covers the gap for the rest of the page. */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {loaded.map((match) => <MatchCard key={match.id} match={match} />)}
          </div>
          {loaded.length < ids.length && <CardGridSkeleton count={ids.length - loaded.length} />}
        </>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3">
          <Button variant="ghost" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
            ← Nowsze
          </Button>
          <span className="text-sm text-text-lo">
            Strona <span className="num tabnum">{page + 1}</span> z{' '}
            <span className="num tabnum">{totalPages}</span>
          </span>
          <Button
            variant="ghost"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((value) => value + 1)}
          >
            Starsze →
          </Button>
        </div>
      )}
    </div>
  );
}
