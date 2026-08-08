import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../../store/auth';
import { useMatches } from '../../api/hooks/matches';
import { usePlayers } from '../../api/hooks/players';
import { useRecalculateRanking } from '../../api/hooks/ranking';
import { StatTile } from '../../components/ui/StatTile';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ErrorState, SectionSkeleton, TilesSkeleton } from '../../components/ui/States';
import { formatDateTime } from '../../lib/format';

export function DashboardPage() {
  const account = useAuthStore((s) => s.account);
  const pending = useMatches({ status: 'RESULTS_SUBMITTED', size: 20 });
  const live = useMatches({ status: 'LIVE', size: 20 });
  const drawn = useMatches({ status: 'TEAMS_DRAWN', size: 20 });
  const lobby = useMatches({ status: 'LOBBY_READY', size: 20 });
  const drafting = useMatches({ status: 'DRAFTING', size: 20 });
  const drafted = useMatches({ status: 'DRAFTED', size: 20 });
  const players = usePlayers({ active: true, size: 1 });

  // Each block reports its own state: one slow query no longer blanks the whole pulpit.
  const sections = [pending, live, drawn, lobby, drafting, drafted, players];
  const errored = sections.find((q) => q.isError);
  if (errored) return <ErrorState error={errored.error} />;

  const pendingList = pending.data?.content ?? [];
  const liveList = live.data?.content ?? [];
  const prepList = [...(drawn.data?.content ?? []), ...(lobby.data?.content ?? [])]
    .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  const draftList = [...(drafting.data?.content ?? []), ...(drafted.data?.content ?? [])]
    .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  const draftingIds = new Set((drafting.data?.content ?? []).map((m) => m.id));
  const inProgress = liveList.length + draftList.length + prepList.length;
  const statsLoading = pending.isLoading || live.isLoading || players.isLoading;

  return (
    <div className="space-y-8">
      <div>
        <div className="kicker text-gold">Witaj{account ? `, ${account.username}` : ''}</div>
        <h1 className="font-display text-3xl">Pulpit</h1>
      </div>

      {statsLoading ? (
        <TilesSkeleton count={3} />
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <StatTile label="Do akceptacji" value={pending.data?.totalElements ?? 0} accent="gold" />
          <StatTile label="Mecze w toku" value={inProgress} accent="cyan" />
          <StatTile label="Aktywni gracze" value={players.data?.totalElements ?? 0} />
        </div>
      )}

      {inProgress > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-lg border border-[color:var(--cyan)]/40 bg-[color:var(--cyan)]/10 p-4">
          <span className="h-2.5 w-2.5 animate-pulse rounded-full bg-cyan" />
          <span className="text-sm text-text-hi">
            {inProgress === 1 ? 'Jeden mecz jest w trakcie' : `${inProgress} mecze są w trakcie`} —
            {draftList.length > 0 ? ' trwa draft.' : liveList.length > 0 ? ' gra się toczy.' : ' czekamy na start.'}
          </span>
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        <Link to="/admin/matches/new">
          <Button variant="gold">+ Rozpocznij mecz</Button>
        </Link>
        <Link to="/admin/approvals">
          <Button variant="ghost">Kolejka akceptacji</Button>
        </Link>
        <RecalculateRankingButton />
      </div>

      {draftList.length > 0 && (
        <section>
          <h2 className="mb-3 font-display text-xl">Draft w toku</h2>
          <div className="space-y-2">
            {draftList.map((m) => (
              <Link
                key={m.id}
                to={`/admin/matches/${m.id}/control`}
                className="glass lift flex items-center justify-between p-4"
              >
                <div>
                  <div className="font-medium text-text-hi">
                    {draftingIds.has(m.id) ? 'Bany i wybór postaci' : 'Draft zakończony — lobby w grze'}
                  </div>
                  <div className="num text-xs text-text-lo">{formatDateTime(m.createdAt)}</div>
                </div>
                <Badge tone="pending">{draftingIds.has(m.id) ? 'Podgląd draftu →' : 'Uruchom mecz →'}</Badge>
              </Link>
            ))}
          </div>
        </section>
      )}

      {prepList.length > 0 && (
        <section>
          <h2 className="mb-3 font-display text-xl">Mecze w przygotowaniu</h2>
          <div className="space-y-2">
            {prepList.map((m) => (
              <Link
                key={m.id}
                to={`/admin/matches/${m.id}/control`}
                className="glass lift flex items-center justify-between p-4"
              >
                <div>
                  <div className="font-medium text-text-hi">
                    {m.status === 'LOBBY_READY' ? 'Lobby gotowe' : 'Losowanie / głosowanie'}
                  </div>
                  <div className="num text-xs text-text-lo">{formatDateTime(m.createdAt)}</div>
                </div>
                <Badge tone="info">Wróć do meczu →</Badge>
              </Link>
            ))}
          </div>
        </section>
      )}

      {liveList.length > 0 && (
        <section>
          <h2 className="mb-3 font-display text-xl">Mecze w toku</h2>
          <div className="space-y-2">
            {liveList.map((m) => (
              <Link
                key={m.id}
                to={`/admin/matches/${m.id}/control`}
                className="glass lift flex items-center justify-between p-4"
              >
                <div>
                  <div className="font-medium text-text-hi">Mecz w toku</div>
                  <div className="num text-xs text-text-lo">{formatDateTime(m.createdAt)}</div>
                </div>
                <Badge tone="info">Wpisz wynik →</Badge>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section>
        <h2 className="mb-3 font-display text-xl">Oczekujące na akceptację</h2>
        {pending.isLoading ? (
          <SectionSkeleton rows={2} />
        ) : pendingList.length === 0 ? (
          <p className="text-sm text-text-lo">Brak meczów do akceptacji. 🎉</p>
        ) : (
          <div className="space-y-2">
            {pendingList.map((m) => (
              <Link
                key={m.id}
                to="/admin/approvals"
                className="glass lift flex items-center justify-between p-4"
              >
                <div>
                  <div className="font-medium text-text-hi">Wyniki do zatwierdzenia</div>
                  <div className="num text-xs text-text-lo">{formatDateTime(m.createdAt)}</div>
                </div>
                <Badge tone="pending">Sprawdź →</Badge>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

/**
 * Rebuilds the current season from its approved matches.
 *
 * Needed whenever a scoring rule changes: a match keeps the LP, PR, MMR and MVP/ACE marks it was
 * scored with, so without this the old rule stays visible on every match already in the database. The
 * recomputation is deterministic — same matches in, same numbers out — but it does rewrite history, so
 * it asks first and says plainly what it touched.
 */
function RecalculateRankingButton() {
  const recalculate = useRecalculateRanking();
  const [message, setMessage] = useState<string | null>(null);

  const run = () => {
    if (!window.confirm(
      'Przeliczyć ranking sezonu od nowa ze wszystkich zatwierdzonych meczów?\n\n'
      + 'LP, PR, MMR oraz tytuły MVP/ACE zostaną wyliczone ponownie według obecnych zasad — '
      + 'wyniki starszych meczów mogą się zmienić.',
    )) return;
    setMessage(null);
    recalculate.mutate(undefined, {
      onSuccess: () => setMessage('✓ Ranking i wszystkie zatwierdzone mecze przeliczone.'),
      onError: (error) => setMessage('⚠ ' + (error as Error).message),
    });
  };

  return (
    <span className="flex flex-wrap items-center gap-3">
      <Button variant="ghost" disabled={recalculate.isPending} onClick={run}>
        {recalculate.isPending ? 'Przeliczanie…' : '↻ Przelicz ranking sezonu'}
      </Button>
      {message && <span className="text-sm text-text-lo">{message}</span>}
    </span>
  );
}
