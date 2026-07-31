import { useParams, Link } from 'react-router-dom';
import { useMatch } from '../api/hooks/matches';
import { Scoreboard } from '../components/match/Scoreboard';
import { MatchPointsBreakdown } from '../components/match/MatchPointsBreakdown';
import { PlayerOpinions } from '../components/match/PlayerOpinions';
import { Badge } from '../components/ui/Badge';
import { CardSkeleton, ErrorState, EmptyState } from '../components/ui/States';
import { formatDateTime } from '../lib/format';
import type { MatchStatus } from '../api/types';

const STATUS_LABEL: Record<MatchStatus, string> = {
  DRAFT: 'Szkic',
  TEAMS_DRAWN: 'Wylosowano drużyny',
  DRAFT_READY: 'Gotowe do draftu',
  DRAFTING: 'Trwa draft',
  DRAFTED: 'Po draftcie',
  LIVE: 'W trakcie',
  LOBBY_READY: 'Lobby Riot gotowe',
  RESULTS_SUBMITTED: 'Oczekuje na akceptację',
  APPROVED: 'Zatwierdzony',
  REJECTED: 'Odesłany do edycji',
  CANCELLED: 'Anulowany',
};

export function MatchPage() {
  const { id } = useParams<{ id: string }>();
  const match = useMatch(id);

  if (match.isLoading) return <CardSkeleton lines={7} />;
  if (match.isError) return <ErrorState error={match.error} />;
  if (!match.data) return <EmptyState title="Nie znaleziono meczu" />;

  const m = match.data;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link to="/" className="text-sm text-text-lo hover:text-text">
            ← Wyniki
          </Link>
          <h1 className="mt-1 font-display text-3xl">Szczegóły meczu</h1>
        </div>
        <Badge tone={m.status === 'APPROVED' ? 'win' : m.status === 'RESULTS_SUBMITTED' ? 'pending' : 'default'}>
          {STATUS_LABEL[m.status]}
        </Badge>
      </div>

      <Scoreboard match={m} />

      {/*
        Opinions first, LP maths second. What people said about the game is the part everyone opens
        the page for; the point breakdown is reference material you scroll down to when a number
        looks wrong.
      */}
      <PlayerOpinions matchId={m.id} />

      <MatchPointsBreakdown match={m} />

      {m.approval && m.status === 'APPROVED' && (
        <div className="glass flex flex-wrap items-center gap-x-6 gap-y-1 p-4 text-sm text-text-lo">
          <span>
            Zatwierdzone przez podpis:{' '}
            <span className="text-text-hi">{m.approval.signatureName ?? '—'}</span>
          </span>
          {m.approval.reviewedAt && <span>· {formatDateTime(m.approval.reviewedAt)}</span>}
        </div>
      )}
    </div>
  );
}
