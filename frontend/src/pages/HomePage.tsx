import { Link } from 'react-router-dom';
import { useCurrentSeason } from '../api/hooks/seasons';
import { usePlayers } from '../api/hooks/players';
import { useMatches, useMatchDetails } from '../api/hooks/matches';
import { useRanking } from '../api/hooks/ranking';
import { MatchCard } from '../components/match/MatchCard';
import { RankingTable } from '../components/ranking/RankingTable';
import { StatTile } from '../components/ui/StatTile';
import { Button } from '../components/ui/Button';
import { LoadingState, EmptyState } from '../components/ui/States';
import type { MatchDetail } from '../api/types';

export function HomePage() {
  const season = useCurrentSeason();
  const players = usePlayers({ active: true });
  const matches = useMatches({ status: 'APPROVED', size: 12 });
  const ranking = useRanking(season.data?.id);

  const recentIds = (matches.data?.content ?? [])
    .slice()
    .sort((a, b) => (b.completedAt ?? '').localeCompare(a.completedAt ?? ''))
    .slice(0, 6)
    .map((m) => m.id);
  const details = useMatchDetails(recentIds);
  const recent = details.map((d) => d.data).filter((d): d is MatchDetail => Boolean(d));

  const leader = ranking.data?.[0];
  const playerCount = players.data?.totalElements ?? 0;
  const matchCount = matches.data?.totalElements ?? 0;

  return (
    <div className="space-y-12">
      {/* Hero */}
      <section className="glass grid-tex relative overflow-hidden px-6 py-12 sm:px-12 sm:py-16">
        <div className="relative z-10 max-w-2xl animate-rise">
          <div className="kicker mb-3 text-gold">
            {season.data ? season.data.name : 'Inhouse League of Legends'}
          </div>
          <h1 className="font-display text-4xl font-bold leading-none sm:text-6xl">
            DRIPERSKA <span className="text-gradient-gold">LIGA</span>
          </h1>
          <p className="mt-4 max-w-lg text-text-lo">
            Wyniki naszych meczów, ranking graczy i pełne statystyki z Summoner's Rift. Drużyny
            losujemy co mecz — liczy się każda gra.
          </p>
          <div className="mt-6 flex gap-3">
            <Link to="/ranking">
              <Button variant="gold">Zobacz ranking</Button>
            </Link>
            <Link to="/players">
              <Button variant="ghost">Gracze</Button>
            </Link>
          </div>
        </div>
      </section>

      {/* Headline stats */}
      <section className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <StatTile label="Aktywni gracze" value={playerCount} accent="cyan" />
        <StatTile label="Rozegrane mecze" value={matchCount} accent="violet" />
        <StatTile
          label="Lider sezonu"
          value={leader ? leader.nickname : '—'}
          sub={leader ? `${leader.totalLp} LP` : undefined}
          accent="gold"
          className="col-span-2 sm:col-span-1"
        />
      </section>

      {/* Recent results */}
      <section>
        <div className="mb-4 flex items-end justify-between">
          <h2 className="font-display text-2xl">Ostatnie wyniki</h2>
          <div className="kicker">Świeżo z Riftu</div>
        </div>
        {matches.isLoading ? (
          <LoadingState />
        ) : recent.length === 0 ? (
          <EmptyState title="Brak rozegranych meczów" description="Gdy pierwszy mecz zostanie zatwierdzony, pojawi się tutaj." />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {recent.map((m) => (
              <MatchCard key={m.id} match={m} />
            ))}
          </div>
        )}
      </section>

      {/* Top ranking */}
      <section>
        <div className="mb-4 flex items-end justify-between">
          <h2 className="font-display text-2xl">Czołówka</h2>
          <Link to="/ranking" className="text-sm text-gold hover:underline">
            Pełny ranking →
          </Link>
        </div>
        {ranking.isLoading ? (
          <LoadingState />
        ) : (ranking.data?.length ?? 0) === 0 ? (
          <EmptyState title="Ranking jest pusty" />
        ) : (
          <RankingTable rows={(ranking.data ?? []).slice(0, 5)} />
        )}
      </section>
    </div>
  );
}
