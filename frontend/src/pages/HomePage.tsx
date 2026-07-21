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
import { RELEASES } from '../content/releases';
import type { MatchDetail } from '../api/types';

export function HomePage() {
  const season = useCurrentSeason();
  const players = usePlayers({ active: true });
  const matches = useMatches({ status: 'APPROVED', size: 12 });
  const ranking = useRanking(season.data?.id);
  const recentIds = (matches.data?.content ?? []).slice()
    .sort((a, b) => (b.completedAt ?? '').localeCompare(a.completedAt ?? ''))
    .slice(0, 6).map((match) => match.id);
  const details = useMatchDetails(recentIds);
  const recent = details.map((detail) => detail.data).filter((detail): detail is MatchDetail => Boolean(detail));
  const leader = ranking.data?.[0];

  return (
    <div className="space-y-12">
      <section className="glass grid-tex relative overflow-hidden px-6 py-12 sm:px-12 sm:py-16">
        <div className="relative z-10 max-w-2xl animate-rise">
          <div className="mb-3 flex items-center gap-3">
            <span className="kicker text-gold">{season.data?.name ?? 'Inhouse League of Legends'}</span>
            <span className="chip border-[color:var(--gold)]/30 text-gold">v0.1</span>
          </div>
          <h1 className="font-display text-4xl font-bold leading-none sm:text-6xl">
            DRIPERSKA <span className="text-gradient-gold">LIGA</span>
          </h1>
          <p className="mt-4 max-w-lg text-text-lo">
            Wyniki, ranking i wspólne losowanie składów na Summoner's Rift. Zaloguj się, zobacz drużynę na żywo i oddaj głos.
          </p>
          <div className="mt-6 flex gap-3">
            <Link to="/ranking"><Button variant="gold">Zobacz ranking</Button></Link>
            <Link to="/login"><Button variant="ghost">Zaloguj się</Button></Link>
          </div>
        </div>
      </section>

      <section className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <StatTile label="Aktywni gracze" value={players.data?.totalElements ?? 0} accent="cyan" />
        <StatTile label="Rozegrane mecze" value={matches.data?.totalElements ?? 0} accent="violet" />
        <StatTile label="Lider sezonu" value={leader?.nickname ?? '—'}
          sub={leader ? `${leader.totalLp} LP` : undefined} accent="gold" className="col-span-2 sm:col-span-1" />
      </section>

      <section>
        <div className="mb-4 flex items-end justify-between">
          <h2 className="font-display text-2xl">Ostatnie wyniki</h2>
          <div className="kicker">Świeżo z Riftu</div>
        </div>
        {matches.isLoading ? <LoadingState /> : recent.length === 0
          ? <EmptyState title="Brak rozegranych meczów" description="Gdy pierwszy mecz zostanie zatwierdzony, pojawi się tutaj." />
          : <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{recent.map((match) => <MatchCard key={match.id} match={match} />)}</div>}
      </section>

      <section>
        <div className="mb-4 flex items-end justify-between">
          <h2 className="font-display text-2xl">Czołówka</h2>
          <Link to="/ranking" className="text-sm text-gold hover:underline">Pełny ranking →</Link>
        </div>
        {ranking.isLoading ? <LoadingState /> : (ranking.data?.length ?? 0) === 0
          ? <EmptyState title="Ranking jest pusty" />
          : <RankingTable rows={(ranking.data ?? []).slice(0, 5)} />}
      </section>

      <section id="changelog" className="scroll-mt-24">
        <div className="mb-4">
          <div className="kicker text-gold">Patch notes</div>
          <h2 className="font-display text-2xl">Co nowego</h2>
        </div>
        <div className="space-y-4">
          {RELEASES.map((release) => (
            <article key={release.version} className="glass p-5 sm:p-6">
              <div className="flex flex-wrap items-baseline gap-3">
                <span className="rounded-md bg-gold px-2.5 py-1 font-display text-sm font-bold text-[#1a1205]">{release.version}</span>
                <h3 className="font-display text-xl">{release.title}</h3>
                <time className="ml-auto text-xs text-text-lo">{release.date}</time>
              </div>
              <ul className="mt-4 grid gap-2 text-sm text-text sm:grid-cols-2">
                {release.changes.map((change) => <li key={change} className="flex gap-2"><span className="text-gold">◆</span><span>{change}</span></li>)}
              </ul>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}