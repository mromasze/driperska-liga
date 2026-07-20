import { useState } from 'react';
import { useRanking } from '../api/hooks/ranking';
import { useSeasons } from '../api/hooks/seasons';
import { RankingTable } from '../components/ranking/RankingTable';
import { Card, CardBody } from '../components/ui/Card';
import { LoadingState, ErrorState } from '../components/ui/States';

export function RankingPage() {
  const [season, setSeason] = useState<string | undefined>(undefined);
  const seasons = useSeasons();
  const ranking = useRanking(season);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl">Ranking ligi</h1>
          <p className="text-sm text-text-lo">
            Sortowanie domyślnie wg LP. Kliknij nagłówek kolumny, aby zmienić.
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <span className="text-text-lo">Sezon</span>
          <select
            value={season ?? ''}
            onChange={(e) => setSeason(e.target.value || undefined)}
            className="rounded-sm border border-line bg-bg-1 px-3 py-1.5 text-sm text-text-hi outline-none focus:border-[var(--gold)]"
          >
            <option value="">Aktywny</option>
            {(seasons.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <Card>
        <CardBody className="p-0 md:p-2">
          {ranking.isLoading ? (
            <LoadingState />
          ) : ranking.isError ? (
            <div className="p-4">
              <ErrorState error={ranking.error} />
            </div>
          ) : (
            <RankingTable rows={ranking.data ?? []} />
          )}
        </CardBody>
      </Card>
    </div>
  );
}
