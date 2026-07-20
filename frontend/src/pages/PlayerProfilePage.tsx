import { useParams } from 'react-router-dom';
import { usePlayer, usePlayerMatches, usePlayerStats } from '../api/hooks/players';
import { Avatar } from '../components/ui/Avatar';
import { Badge } from '../components/ui/Badge';
import { StatTile } from '../components/ui/StatTile';
import { Card, CardBody, CardHeader, CardTitle } from '../components/ui/Card';
import { ChampionIcon } from '../components/champion/ChampionIcon';
import { PrBadge } from '../components/ui/PrBadge';
import { Sparkline } from '../components/ui/Sparkline';
import { MatchCard } from '../components/match/MatchCard';
import { LoadingState, ErrorState, EmptyState } from '../components/ui/States';
import { championSplashUrl } from '../lib/ddragon';
import { fixed, roleLabel } from '../lib/format';

export function PlayerProfilePage() {
  const { id } = useParams<{ id: string }>();
  const player = usePlayer(id);
  const stats = usePlayerStats(id);
  const matches = usePlayerMatches(id);

  if (player.isLoading) return <LoadingState />;
  if (player.isError) return <ErrorState error={player.error} />;
  if (!player.data) return <EmptyState title="Nie znaleziono gracza" />;

  const p = player.data;
  const s = stats.data;
  const mainChampion = s?.championPool?.[0];

  return (
    <div className="space-y-8">
      {/* Header */}
      <section className="relative overflow-hidden rounded-lg border border-line bg-bg-1">
        {mainChampion && (
          <img
            src={championSplashUrl(mainChampion.championSlug) ?? undefined}
            alt=""
            aria-hidden="true"
            loading="lazy"
            className="absolute inset-0 h-full w-full object-cover object-top opacity-30"
          />
        )}
        <div className="splash-scrim absolute inset-0" />
        <div className="relative flex flex-wrap items-center gap-5 p-6">
          <Avatar src={p.avatarUrl} name={p.nickname} size={96} ring />
          <div className="min-w-0">
            <h1 className="text-3xl">{p.nickname}</h1>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-text-lo">
              {p.riotId && <span className="num">{p.riotId}</span>}
              <Badge tone="info">{roleLabel(p.mainRole)}</Badge>
              {p.secondaryRole && <Badge tone="neutral">2nd: {roleLabel(p.secondaryRole)}</Badge>}
              {s && <span className="num">MMR {Math.round(s.mmr)}</span>}
            </div>
            {p.bio && <p className="mt-2 max-w-lg text-sm text-text">{p.bio}</p>}
          </div>
        </div>
      </section>

      {/* Stat tiles */}
      {stats.isLoading ? (
        <LoadingState />
      ) : stats.isError ? (
        <ErrorState error={stats.error} />
      ) : s ? (
        <section className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <StatTile label="LP" value={s.totalLp} accent="var(--gold)" />
          <StatTile label="Gry" value={s.games} />
          <StatTile label="Win %" value={`${fixed(s.winRate, 0)}%`} />
          <StatTile label="Bilans" value={`${s.wins}-${s.losses}`} />
          <StatTile label="Avg PR" value={fixed(s.avgPerformanceRating, 1)} />
          <StatTile label="MVP" value={s.mvpCount} accent="var(--gold)" />
        </section>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-2">
        {/* PR chart */}
        <Card>
          <CardHeader>
            <CardTitle>Forma PR w czasie</CardTitle>
          </CardHeader>
          <CardBody>
            {s && s.prHistory.length > 1 ? (
              <div className="flex items-center gap-4">
                <Sparkline
                  data={s.prHistory.map((h) => h.performanceRating)}
                  width={260}
                  height={72}
                  color="var(--pr-high)"
                  className="w-full"
                />
              </div>
            ) : (
              <p className="text-sm text-text-lo">Za mało danych do wykresu.</p>
            )}
          </CardBody>
        </Card>

        {/* Champion pool */}
        <Card>
          <CardHeader>
            <CardTitle>Champion pool</CardTitle>
          </CardHeader>
          <CardBody className="p-0">
            {s && s.championPool.length > 0 ? (
              <ul className="divide-y divide-line">
                {s.championPool.slice(0, 6).map((c) => (
                  <li key={c.championId} className="flex items-center gap-3 px-4 py-2.5">
                    <ChampionIcon src={c.iconUrl} name={c.championName} size={32} />
                    <span className="flex-1 truncate text-sm text-text-hi">{c.championName}</span>
                    <span className="num text-xs text-text-lo">{c.games} gier</span>
                    <span className="num w-12 text-right text-xs text-text">
                      {fixed(c.winRate, 0)}%
                    </span>
                    <PrBadge value={c.avgPerformanceRating} size="sm" />
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-4 py-6 text-sm text-text-lo">
                Brak danych o championach (placeholder — pojawią się po rozegranych meczach).
              </p>
            )}
          </CardBody>
        </Card>
      </div>

      {/* Match history */}
      <section>
        <h2 className="mb-3 text-xl">Historia meczów</h2>
        {matches.isLoading ? (
          <LoadingState />
        ) : matches.isError ? (
          <ErrorState error={matches.error} />
        ) : (matches.data?.content ?? []).length === 0 ? (
          <EmptyState title="Brak meczów" />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {(matches.data?.content ?? []).map((m) => (
              <MatchCard key={m.id} match={m} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
