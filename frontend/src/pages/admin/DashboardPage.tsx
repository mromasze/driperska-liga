import { Link } from 'react-router-dom';
import { useAuthStore } from '../../store/auth';
import { useMatches } from '../../api/hooks/matches';
import { usePlayers } from '../../api/hooks/players';
import { StatTile } from '../../components/ui/StatTile';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { formatDateTime } from '../../lib/format';

export function DashboardPage() {
  const account = useAuthStore((s) => s.account);
  const pending = useMatches({ status: 'RESULTS_SUBMITTED', size: 20 });
  const live = useMatches({ status: 'LIVE', size: 20 });
  const drawn = useMatches({ status: 'TEAMS_DRAWN', size: 20 });
  const lobby = useMatches({ status: 'LOBBY_READY', size: 20 });
  const players = usePlayers({ active: true, size: 1 });

  const sections = [pending, live, drawn, lobby, players];
  if (sections.some((q) => q.isLoading)) return <LoadingState />;
  const errored = sections.find((q) => q.isError);
  if (errored) return <ErrorState error={errored.error} />;

  const pendingList = pending.data?.content ?? [];
  const liveList = live.data?.content ?? [];
  const prepList = [...(drawn.data?.content ?? []), ...(lobby.data?.content ?? [])]
    .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));

  return (
    <div className="space-y-8">
      <div>
        <div className="kicker text-gold">Witaj{account ? `, ${account.username}` : ''}</div>
        <h1 className="font-display text-3xl">Pulpit</h1>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <StatTile label="Do akceptacji" value={pending.data?.totalElements ?? 0} accent="gold" />
        <StatTile label="Mecze w toku" value={live.data?.totalElements ?? 0} accent="cyan" />
        <StatTile label="Aktywni gracze" value={players.data?.totalElements ?? 0} />
      </div>

      <div className="flex flex-wrap gap-3">
        <Link to="/admin/matches/new">
          <Button variant="gold">+ Rozpocznij mecz</Button>
        </Link>
        <Link to="/admin/approvals">
          <Button variant="ghost">Kolejka akceptacji</Button>
        </Link>
      </div>

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
        {pendingList.length === 0 ? (
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
