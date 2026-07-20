import { Link } from 'react-router-dom';
import { useMatches } from '../../api/hooks/matches';
import { useMe } from '../../api/hooks/auth';
import { StatTile } from '../../components/ui/StatTile';
import { Card, CardBody, CardHeader, CardTitle } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { LoadingState } from '../../components/ui/States';

export function DashboardPage() {
  const me = useMe();
  const pending = useMatches({ status: 'RESULTS_SUBMITTED', size: 50 });
  const live = useMatches({ status: 'LIVE', size: 50 });
  const draft = useMatches({ status: 'DRAFT', size: 50 });

  const pendingCount = pending.data?.totalElements ?? 0;
  const liveCount = live.data?.totalElements ?? 0;
  const draftCount = draft.data?.totalElements ?? 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl">Dashboard</h1>
        <p className="text-sm text-text-lo">
          {me.data ? `Zalogowano jako ${me.data.username} (${me.data.role}).` : 'Panel administracyjny.'}
        </p>
      </div>

      {pending.isLoading ? (
        <LoadingState />
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <StatTile
            label="Do akceptacji"
            value={pendingCount}
            accent={pendingCount > 0 ? 'var(--pending)' : undefined}
          />
          <StatTile label="Mecze LIVE" value={liveCount} />
          <StatTile label="Szkice" value={draftCount} />
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Szybkie akcje</CardTitle>
          </CardHeader>
          <CardBody className="flex flex-wrap gap-2">
            <Link to="/admin/matches/new">
              <Button variant="gold">Nowy mecz</Button>
            </Link>
            <Link to="/admin/approvals">
              <Button variant="secondary">
                Kolejka akceptacji{pendingCount > 0 ? ` (${pendingCount})` : ''}
              </Button>
            </Link>
            <Link to="/admin/players">
              <Button variant="secondary">Gracze</Button>
            </Link>
          </CardBody>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Oczekujące na decyzję</CardTitle>
          </CardHeader>
          <CardBody className="p-0">
            {(pending.data?.content ?? []).length === 0 ? (
              <p className="px-5 py-6 text-sm text-text-lo">Brak meczów do akceptacji.</p>
            ) : (
              <ul className="divide-y divide-line">
                {(pending.data?.content ?? []).map((m) => (
                  <li key={m.id}>
                    <Link
                      to="/admin/approvals"
                      className="flex items-center justify-between px-5 py-3 text-sm transition hover:bg-bg-2"
                    >
                      <span className="text-text-hi">Mecz {m.id.slice(0, 8)}</span>
                      <span className="num text-text-lo">
                        {m.blueScore} : {m.redScore}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
