import { useState } from 'react';
import { useSeasons, useCurrentSeason } from '../api/hooks/seasons';
import { useRanking } from '../api/hooks/ranking';
import { RankingTable } from '../components/ranking/RankingTable';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

export function RankingPage() {
  const seasons = useSeasons();
  const current = useCurrentSeason();
  const [selected, setSelected] = useState<string | undefined>(undefined);
  const seasonId = selected ?? current.data?.id;
  const ranking = useRanking(seasonId);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="kicker mb-1 text-gold">Sezon</div>
          <h1 className="font-display text-3xl">Ranking graczy</h1>
        </div>
        <select
          className="h-10 rounded-md border border-line bg-bg-1 px-3 text-sm text-text-hi focus:border-line-strong"
          value={seasonId ?? ''}
          onChange={(e) => setSelected(e.target.value || undefined)}
        >
          {(seasons.data ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>

      {ranking.isLoading ? (
        <LoadingState />
      ) : ranking.isError ? (
        <ErrorState error={ranking.error} />
      ) : (ranking.data?.length ?? 0) === 0 ? (
        <EmptyState
          title="Ranking jest jeszcze pusty"
          description="Punkty pojawią się po zatwierdzeniu pierwszych meczów."
        />
      ) : (
        <RankingTable rows={ranking.data ?? []} />
      )}
    </div>
  );
}
