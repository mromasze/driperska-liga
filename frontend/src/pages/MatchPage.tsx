import { useParams, Link } from 'react-router-dom';
import { useMatch } from '../api/hooks/matches';
import { Scoreboard } from '../components/match/Scoreboard';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody } from '../components/ui/Card';
import { LoadingState, ErrorState, EmptyState } from '../components/ui/States';
import { formatDateTime } from '../lib/format';

export function MatchPage() {
  const { id } = useParams<{ id: string }>();
  const match = useMatch(id);

  if (match.isLoading) return <LoadingState />;
  if (match.isError) return <ErrorState error={match.error} />;
  if (!match.data) return <EmptyState title="Nie znaleziono meczu" />;

  const m = match.data;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/" className="text-xs text-text-lo hover:text-text-hi">
            ← Wróć
          </Link>
          <h1 className="text-2xl">Szczegóły meczu</h1>
          <p className="text-sm text-text-lo">{formatDateTime(m.completedAt ?? m.createdAt)}</p>
        </div>
        <Badge tone={m.status === 'APPROVED' ? 'win' : 'pending'}>{m.status}</Badge>
      </div>

      <Card>
        <CardBody>
          <Scoreboard match={m} />
        </CardBody>
      </Card>

      {m.notes && (
        <Card>
          <CardBody className="text-sm text-text">{m.notes}</CardBody>
        </Card>
      )}
    </div>
  );
}
