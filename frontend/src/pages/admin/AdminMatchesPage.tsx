import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMatches, useShareMatchToDiscord } from '../../api/hooks/matches';
import type { MatchStatus, MatchSummary } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States';
import { formatDateTime } from '../../lib/format';

const STATUSES: Array<{ value: MatchStatus | ''; label: string }> = [
  { value: '', label: 'Wszystkie statusy' },
  { value: 'DRAFT', label: 'Szkic' },
  { value: 'TEAMS_DRAWN', label: 'Wylosowane składy' },
  { value: 'LOBBY_READY', label: 'Lobby gotowe' },
  { value: 'LIVE', label: 'W toku' },
  { value: 'RESULTS_SUBMITTED', label: 'Do akceptacji' },
  { value: 'APPROVED', label: 'Zatwierdzony' },
  { value: 'REJECTED', label: 'Do poprawy' },
  { value: 'CANCELLED', label: 'Anulowany' },
];

const STATUS_LABEL: Record<MatchStatus, string> = Object.fromEntries(
  STATUSES.filter((entry) => entry.value).map((entry) => [entry.value, entry.label]),
) as Record<MatchStatus, string>;

function tone(status: MatchStatus): 'default' | 'win' | 'loss' | 'pending' | 'info' {
  if (status === 'APPROVED') return 'win';
  if (status === 'CANCELLED' || status === 'REJECTED') return 'loss';
  if (status === 'RESULTS_SUBMITTED') return 'pending';
  if (status === 'LIVE' || status === 'LOBBY_READY') return 'info';
  return 'default';
}

function MatchRow({ match }: { match: MatchSummary }) {
  const share = useShareMatchToDiscord(match.id);
  const [message, setMessage] = useState<string | null>(null);
  const canShare = match.participantCount > 0
    && ['RESULTS_SUBMITTED', 'APPROVED', 'REJECTED'].includes(match.status);

  return (
    <details className="match-dropdown glass overflow-hidden">
      <summary className="flex cursor-pointer list-none items-center gap-3 p-4">
        <Badge tone={tone(match.status)}>{STATUS_LABEL[match.status]}</Badge>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm text-text-hi">
            {match.participantCount} graczy
            {(match.startedAt ?? match.completedAt)
              ? ` · ${formatDateTime(match.startedAt ?? match.completedAt)}` : ''}
          </div>
          <div className="truncate font-mono text-xs text-text-lo">{match.id.slice(0, 8)}</div>
        </div>
        <span className="match-dropdown-chevron shrink-0 text-text-lo transition-transform">▾</span>
      </summary>
      <div className="border-t border-line p-4">
        <div className="text-sm text-text-hi">
          {match.participantCount} graczy
          {(match.startedAt ?? match.completedAt)
            ? ` · rozegrany ${formatDateTime(match.startedAt ?? match.completedAt)}` : ''}
        </div>
        <div className="mt-1 text-xs text-text-lo">Utworzony {formatDateTime(match.createdAt)}</div>
        <div className="mt-3 flex flex-wrap gap-2">
          {canShare && (
            <Button
              variant="ghost"
              size="sm"
              disabled={share.isPending}
              onClick={() => {
                setMessage(null);
                share.mutate(undefined, {
                  onSuccess: (result) => setMessage(result.sent ? '✓ Wysłano na Discord' : `⚠ ${result.message}`),
                  onError: (error) => setMessage(`⚠ ${error.message}`),
                });
              }}
            >
              {share.isPending ? 'Wysyłanie…' : 'Udostępnij na Discord'}
            </Button>
          )}
          <Link
            to={`/admin/matches/${match.id}/control`}
            className="inline-flex h-8 items-center rounded-md bg-gradient-to-b from-gold-soft to-gold px-3 text-xs font-semibold text-[#1a1205] transition hover:brightness-105"
          >
            Otwórz / edytuj
          </Link>
        </div>
        {message && <p className="mt-3 text-xs text-text-lo">{message}</p>}
      </div>
    </details>
  );
}

export function AdminMatchesPage() {
  const [status, setStatus] = useState<MatchStatus | ''>('');
  const [page, setPage] = useState(0);
  const matches = useMatches({ status: status || undefined, page, size: 25 });

  if (matches.isLoading) return <LoadingState />;
  if (matches.isError) return <ErrorState error={matches.error} />;

  const data = matches.data;
  const list = data?.content ?? [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="kicker text-gold">Administracja</div>
          <h1 className="font-display text-3xl">Wszystkie mecze</h1>
          <p className="mt-2 text-sm text-text-lo">
            Otwórz dowolny mecz, popraw wynik lub wyślij jego kartę ponownie na Discord.
          </p>
        </div>
        <Link
          to="/admin/matches/new"
          className="inline-flex h-10 items-center rounded-md bg-gradient-to-b from-gold-soft to-gold px-4 text-sm font-semibold text-[#1a1205]"
        >
          + Nowy mecz
        </Link>
      </div>

      <label className="block max-w-sm">
        <span className="kicker">Filtr statusu</span>
        <select
          className="form-control mt-1"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as MatchStatus | '');
            setPage(0);
          }}
        >
          {STATUSES.map((entry) => (
            <option key={entry.value || 'all'} value={entry.value}>{entry.label}</option>
          ))}
        </select>
      </label>

      {list.length === 0 ? (
        <EmptyState title="Brak meczów dla wybranego filtra" />
      ) : (
        <div className="space-y-3">{list.map((match) => <MatchRow key={match.id} match={match} />)}</div>
      )}

      {(data?.totalPages ?? 0) > 1 && (
        <div className="flex items-center justify-between">
          <Button variant="ghost" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
            ← Poprzednia
          </Button>
          <span className="text-sm text-text-lo">Strona {page + 1} z {data?.totalPages}</span>
          <Button
            variant="ghost"
            disabled={page + 1 >= (data?.totalPages ?? 0)}
            onClick={() => setPage((value) => value + 1)}
          >
            Następna →
          </Button>
        </div>
      )}
    </div>
  );
}
