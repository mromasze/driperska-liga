import { Link } from 'react-router-dom';
import { useMatches } from '../api/hooks/matches';
import { useRanking } from '../api/hooks/ranking';
import { useCurrentSeason } from '../api/hooks/seasons';
import { MatchCard } from '../components/match/MatchCard';
import { Card, CardBody, CardHeader, CardTitle } from '../components/ui/Card';
import { Avatar } from '../components/ui/Avatar';
import { RankMedal } from '../components/ui/RankMedal';
import { Badge } from '../components/ui/Badge';
import { PrBadge } from '../components/ui/PrBadge';
import { Button } from '../components/ui/Button';
import { LoadingState, ErrorState, EmptyState } from '../components/ui/States';
import { championSplashUrl } from '../lib/ddragon';
import { formatDate } from '../lib/format';

export function HomePage() {
  const matches = useMatches({ status: 'APPROVED', size: 6 });
  const ranking = useRanking();
  const season = useCurrentSeason();

  const recent = matches.data?.content ?? [];
  const hero = recent[0];
  const top5 = (ranking.data ?? []).slice(0, 5);

  return (
    <div className="space-y-10">
      {/* Hero */}
      <section
        className="relative overflow-hidden rounded-lg border border-line bg-bg-1"
        style={{ minHeight: 260 }}
      >
        {hero?.mvp && (
          <img
            src={championSplashUrl(hero.mvp.championSlug) ?? undefined}
            alt=""
            aria-hidden="true"
            loading="lazy"
            className="absolute inset-0 h-full w-full object-cover object-top opacity-40"
          />
        )}
        <div className="splash-scrim absolute inset-0" />
        <div className="relative flex h-full flex-col justify-end gap-3 p-6 md:p-10">
          <Badge tone="gold" className="w-fit">
            {season.data ? season.data.name : 'Driperska Liga'}
          </Badge>
          <h1 className="max-w-xl text-3xl md:text-5xl">Inhouse League of Legends</h1>
          <p className="max-w-lg text-sm text-text">
            Losowane składy, uczciwy system punktów (PR / LP / MMR) i pełne statystyki każdego meczu.
          </p>
          <div className="flex gap-2">
            <Link to="/ranking">
              <Button variant="gold">Zobacz ranking</Button>
            </Link>
            <Link to="/players">
              <Button variant="secondary">Gracze</Button>
            </Link>
          </div>
        </div>
      </section>

      <div className="grid gap-8 lg:grid-cols-3">
        {/* Recent matches */}
        <section className="lg:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-xl">Ostatnie mecze</h2>
          </div>
          {matches.isLoading ? (
            <LoadingState />
          ) : matches.isError ? (
            <ErrorState error={matches.error} />
          ) : recent.length === 0 ? (
            <EmptyState
              title="Brak rozegranych meczów"
              description="Zaakceptowane mecze pojawią się tutaj."
            />
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              {recent.map((match) => (
                <MatchCard key={match.id} match={match} />
              ))}
            </div>
          )}
        </section>

        {/* Top 5 ranking teaser */}
        <section>
          <Card>
            <CardHeader className="flex items-center justify-between">
              <CardTitle>Top 5</CardTitle>
              <Link to="/ranking" className="text-xs text-gold hover:underline">
                pełna tabela →
              </Link>
            </CardHeader>
            <CardBody className="p-0">
              {ranking.isLoading ? (
                <LoadingState />
              ) : ranking.isError ? (
                <div className="p-4">
                  <ErrorState error={ranking.error} />
                </div>
              ) : top5.length === 0 ? (
                <div className="p-4">
                  <EmptyState title="Brak rankingu" />
                </div>
              ) : (
                <ul className="divide-y divide-line">
                  {top5.map((row) => (
                    <li key={row.player.id}>
                      <Link
                        to={`/players/${row.player.id}`}
                        className="flex items-center gap-3 px-4 py-2.5 transition hover:bg-bg-2"
                      >
                        <RankMedal rank={row.rank} />
                        <Avatar src={row.player.avatarUrl} name={row.player.nickname} size={28} />
                        <span className="flex-1 truncate text-sm text-text-hi">
                          {row.player.nickname}
                        </span>
                        <PrBadge value={row.avgPerformanceRating} size="sm" />
                        <span className="num w-14 text-right text-sm text-gold">
                          {row.totalLp} LP
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </CardBody>
          </Card>

          {season.data && (
            <Card className="mt-4">
              <CardBody className="text-sm text-text-lo">
                <div className="text-text-hi">{season.data.name}</div>
                <div className="mt-1">
                  {formatDate(season.data.startDate)} — {formatDate(season.data.endDate)}
                </div>
              </CardBody>
            </Card>
          )}
        </section>
      </div>
    </div>
  );
}
