import { Link, useParams } from 'react-router-dom';
import { usePlayer, usePlayerStats, usePlayerMatches } from '../api/hooks/players';
import { useChampions } from '../api/hooks/champions';
import { useCurrentSeason } from '../api/hooks/seasons';
import { Avatar } from '../components/ui/Avatar';
import { Badge } from '../components/ui/Badge';
import { StatTile } from '../components/ui/StatTile';
import { PrBadge } from '../components/ui/PrBadge';
import { ChampionIcon } from '../components/champion/ChampionIcon';
import { LoadingState, ErrorState, EmptyState } from '../components/ui/States';
import { championSplashUrl } from '../lib/ddragon';
import { formatDate, roleLabel } from '../lib/format';

export function PlayerProfilePage() {
  const { id } = useParams<{ id: string }>();
  const player = usePlayer(id);
  const season = useCurrentSeason();
  const stats = usePlayerStats(id, season.data?.id);
  const matches = usePlayerMatches(id);
  const champions = useChampions();

  if (player.isLoading) return <LoadingState />;
  if (player.isError) return <ErrorState error={player.error} />;
  if (!player.data) return <EmptyState title="Nie znaleziono gracza" />;

  const p = player.data;
  const agg = stats.data?.season;
  const pool = stats.data?.championPool ?? [];
  const topChampId = pool[0]?.championId;
  const topSlug = champions.data?.find((c) => c.id === topChampId)?.slug;
  const splash = championSplashUrl(topSlug);
  const favoriteChampions = (p.favoriteChampionIds ?? [])
    .map((cid) => champions.data?.find((c) => c.id === cid))
    .filter((c): c is NonNullable<typeof c> => Boolean(c));

  return (
    <div className="space-y-8">
      {/* Header with optional splash backdrop */}
      <section className="glass relative overflow-hidden">
        {splash && (
          <>
            <img src={splash} alt="" className="absolute inset-0 h-full w-full object-cover object-top opacity-40" />
            <div className="absolute inset-0 scrim-side" />
          </>
        )}
        <div className="relative z-10 flex flex-col gap-5 p-6 sm:flex-row sm:items-center sm:p-8">
          <Avatar src={p.avatarUrl} name={p.nickname} size={96} ring />
          <div className="flex-1">
            <h1 className="font-display text-4xl">{p.nickname}</h1>
            <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-text-lo">
              <Badge tone="gold">{roleLabel(p.mainRole)}</Badge>
              {p.secondaryRole && <Badge>{roleLabel(p.secondaryRole)}</Badge>}
              {p.riotId && <span className="num">{p.riotId}</span>}
              {p.realName && <span>· {p.realName}</span>}
            </div>
            {p.bio && <p className="mt-3 max-w-xl text-sm text-text">{p.bio}</p>}
            {favoriteChampions.length > 0 && (
              <div className="mt-4">
                <div className="kicker mb-2">Ulubieni bohaterowie</div>
                <div className="flex flex-wrap gap-2">
                  {favoriteChampions.map((c) => (
                    <div
                      key={c.id}
                      className="flex items-center gap-2 rounded-full border border-line bg-[color:var(--bg-1)]/70 py-1 pl-1 pr-3"
                    >
                      <ChampionIcon iconUrl={c.iconUrl} name={c.name} size={28} className="rounded-full" />
                      <span className="text-sm font-medium text-text-hi">{c.name}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* Season stat tiles */}
      <section className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        <StatTile label="Punkty (LP)" value={agg?.totalLp ?? 0} accent="gold" />
        <StatTile label="Mecze" value={agg?.games ?? 0} />
        <StatTile
          label="Winrate"
          value={`${Math.round((agg?.winRate ?? 0) * 100)}%`}
          sub={agg ? `${agg.wins}W ${agg.losses}L` : undefined}
          accent="win"
        />
        <StatTile label="Śr. PR" value={Math.round(agg?.avgPerformanceRating ?? 0)} accent="cyan" />
        <StatTile label="MVP" value={agg?.mvpCount ?? 0} accent="violet" />
        <StatTile label="MMR" value={Math.round(agg?.mmr ?? 1000)} />
      </section>

      <div className="grid gap-8 lg:grid-cols-[1fr_1.4fr]">
        {/* Champion pool */}
        <section>
          <h2 className="mb-4 font-display text-2xl">Pula bohaterów</h2>
          {pool.length === 0 ? (
            <EmptyState title="Brak danych" description="Statystyki pojawią się po pierwszych meczach." />
          ) : (
            <div className="glass divide-y divide-line">
              {pool.map((c) => (
                <div key={c.championId} className="flex items-center gap-3 p-3">
                  <ChampionIcon iconUrl={c.iconUrl} name={c.championName} size={40} />
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-medium text-text-hi">{c.championName ?? '—'}</div>
                    <div className="num text-xs text-text-lo">
                      {c.games} gier · {Math.round(c.winRate * 100)}% WR
                    </div>
                  </div>
                  <PrBadge value={c.avgPerformanceRating} size="sm" />
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Match history */}
        <section>
          <h2 className="mb-4 font-display text-2xl">Historia meczów</h2>
          {matches.isLoading ? (
            <LoadingState />
          ) : (matches.data?.length ?? 0) === 0 ? (
            <EmptyState title="Brak rozegranych meczów" />
          ) : (
            <div className="space-y-2">
              {(matches.data ?? []).map((m) => (
                <Link
                  key={m.matchId}
                  to={`/matches/${m.matchId}`}
                  className="glass lift flex items-center gap-3 p-3"
                  style={{
                    borderLeft: `3px solid ${m.won ? 'var(--win)' : 'var(--loss)'}`,
                  }}
                >
                  <ChampionIcon iconUrl={m.championIconUrl} name={m.championName} size={40} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className={m.won ? 'font-semibold text-win' : 'font-semibold text-loss'}>
                        {m.won ? 'Wygrana' : 'Przegrana'}
                      </span>
                      {m.mvp && <span title="MVP">👑</span>}
                      <span className="kicker">{roleLabel(m.role)}</span>
                    </div>
                    <div className="num text-xs text-text-lo">
                      {m.kills}/{m.deaths}/{m.assists} · KDA {m.kda.toFixed(2)} · {formatDate(m.completedAt)}
                    </div>
                  </div>
                  {m.lpAwarded != null && (
                    <span className="num text-sm font-semibold text-gold">+{m.lpAwarded}</span>
                  )}
                  <PrBadge value={m.performanceRating} size="sm" />
                </Link>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
