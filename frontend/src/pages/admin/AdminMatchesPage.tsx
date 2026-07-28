import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMatches, useShareMatchToDiscord } from '../../api/hooks/matches';
import {
  useDeleteDraftsInProgress, useDeleteMatch, useMatchMaintenance, usePurgeUnapprovedMatches,
  useStopAllMatches,
} from '../../api/hooks/matchMaintenance';
import type { MatchStatus, MatchSummary } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, SectionSkeleton } from '../../components/ui/States';
import { formatDateTime } from '../../lib/format';

const STATUSES: Array<{ value: MatchStatus | ''; label: string }> = [
  { value: '', label: 'Wszystkie statusy' },
  { value: 'DRAFT', label: 'Szkic' },
  { value: 'TEAMS_DRAWN', label: 'Wylosowane składy' },
  // Without these three the badge on a drafting match rendered blank.
  { value: 'DRAFT_READY', label: 'Draft gotowy' },
  { value: 'DRAFTING', label: 'Draft w toku' },
  { value: 'DRAFTED', label: 'Po drafcie' },
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
  if (status === 'DRAFTING' || status === 'DRAFTED' || status === 'DRAFT_READY') return 'info';
  return 'default';
}

function MatchRow({ match }: { match: MatchSummary }) {
  const share = useShareMatchToDiscord(match.id);
  const remove = useDeleteMatch();
  const [message, setMessage] = useState<string | null>(null);
  const canShare = match.participantCount > 0
    && ['RESULTS_SUBMITTED', 'APPROVED', 'REJECTED'].includes(match.status);

  const deleteThis = () => {
    const warning = match.status === 'APPROVED'
      ? 'To ZATWIERDZONY mecz — jego wyniki liczą się do rankingu i statystyk graczy.\n\n'
      : '';
    if (!window.confirm(
      `${warning}Usunąć mecz ${match.id.slice(0, 8)} (${STATUS_LABEL[match.status]}) na zawsze?\n\n`
      + 'Zniknie razem z draftem, wynikami, ocenami i historią. Tego nie można cofnąć.',
    )) return;
    setMessage(null);
    remove.mutate(match.id, { onError: (error) => setMessage(`⚠ ${error.message}`) });
  };

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
          <Button variant="danger" size="sm" disabled={remove.isPending} onClick={deleteThis}>
            {remove.isPending ? 'Usuwanie…' : '🗑 Usuń mecz'}
          </Button>
        </div>
        {message && <p className="mt-3 text-xs text-text-lo">{message}</p>}
      </div>
    </details>
  );
}

/**
 * Bulk housekeeping. Every button states how many matches it would touch and refuses to be pressed
 * when that number is zero, so an admin never fires a destructive action blind.
 */
function MaintenanceSection() {
  const summary = useMatchMaintenance();
  const stopAll = useStopAllMatches();
  const deleteDrafts = useDeleteDraftsInProgress();
  const purge = usePurgeUnapprovedMatches();
  const [message, setMessage] = useState<string | null>(null);

  if (summary.isLoading || !summary.data) return null;
  const s = summary.data;
  const busy = stopAll.isPending || deleteDrafts.isPending || purge.isPending;

  const run = (
    mutation: typeof stopAll,
    confirmText: string,
    done: (count: number) => string,
    typeToConfirm?: string,
  ) => {
    if (!window.confirm(confirmText)) return;
    if (typeToConfirm) {
      const typed = window.prompt(`Wpisz ${typeToConfirm}, żeby potwierdzić:`);
      if (typed?.trim().toUpperCase() !== typeToConfirm) {
        setMessage('⚠ Anulowano — potwierdzenie nie zgadza się.');
        return;
      }
    }
    setMessage(null);
    mutation.mutate(undefined, {
      onSuccess: (result) => setMessage(done(result.affected)),
      onError: (error) => setMessage(`⚠ ${error.message}`),
    });
  };

  return (
    <section className="glass grid-tex p-5">
      <h2 className="font-display text-xl">Porządki</h2>
      <p className="mt-1 max-w-3xl text-sm text-text-lo">
        Na liście jest {s.total} meczów, z czego {s.approved} zatwierdzonych.
        Operacje oznaczone jako usuwanie są <strong className="text-loss">nieodwracalne</strong> —
        kasują mecz razem z draftem, wynikami, ocenami i historią.
      </p>

      <div className="mt-4 grid gap-3 lg:grid-cols-3">
        <div className="rounded-xl border border-line bg-[color:var(--bg-1)]/60 p-4">
          <div className="font-display text-base text-text-hi">Zatrzymaj trwające mecze</div>
          <p className="mt-1 text-xs text-text-lo">
            Anuluje wszystko, co jest jeszcze w toku (losowanie, draft, lobby, gra).
            Mecze zostają na liście jako anulowane — nic nie znika.
          </p>
          <div className="num mt-3 font-display text-2xl text-cyan">{s.running}</div>
          <Button
            className="mt-2 w-full"
            variant="ghost"
            disabled={busy || s.running === 0}
            onClick={() => run(
              stopAll,
              `Anulować ${s.running} trwających meczów? Zostaną na liście jako anulowane.`,
              (n) => `✓ Anulowano ${n} meczów.`,
            )}
          >
            {stopAll.isPending ? 'Zatrzymywanie…' : '■ Zatrzymaj wszystkie'}
          </Button>
        </div>

        <div className="rounded-xl border border-[color:var(--loss)]/40 bg-[color:var(--loss)]/5 p-4">
          <div className="font-display text-base text-text-hi">Usuń mecze z rozpoczętym draftem</div>
          <p className="mt-1 text-xs text-text-lo">
            Kasuje mecze, w których draft już wystartował (trwa lub się zakończył, ale gra się nie
            odbyła). Przydatne po testach i porzuconych wieczorach.
          </p>
          <div className="num mt-3 font-display text-2xl text-loss">{s.draftInProgress}</div>
          <Button
            className="mt-2 w-full"
            variant="danger"
            disabled={busy || s.draftInProgress === 0}
            onClick={() => run(
              deleteDrafts,
              `Usunąć na zawsze ${s.draftInProgress} meczów z rozpoczętym draftem?\n\n`
              + 'Tego nie można cofnąć.',
              (n) => `✓ Usunięto ${n} meczów.`,
            )}
          >
            {deleteDrafts.isPending ? 'Usuwanie…' : '🗑 Usuń drafty'}
          </Button>
        </div>

        <div className="rounded-xl border border-[color:var(--loss)]/40 bg-[color:var(--loss)]/5 p-4">
          <div className="font-display text-base text-text-hi">Wyczyść niezaakceptowane</div>
          <p className="mt-1 text-xs text-text-lo">
            Kasuje wszystko, co nie jest zatwierdzone — szkice, drafty, anulowane, odrzucone i wyniki
            czekające na akceptację. Zostaje tylko {s.approved} zatwierdzonych meczów.
          </p>
          <div className="num mt-3 font-display text-2xl text-loss">{s.unapproved}</div>
          <Button
            className="mt-2 w-full"
            variant="danger"
            disabled={busy || s.unapproved === 0}
            onClick={() => run(
              purge,
              `Usunąć na zawsze ${s.unapproved} niezaakceptowanych meczów?\n\n`
              + `Na liście zostanie ${s.approved} zatwierdzonych. Tego nie można cofnąć.`,
              (n) => `✓ Usunięto ${n} meczów. Zostały tylko zatwierdzone.`,
              'USUN',
            )}
          >
            {purge.isPending ? 'Czyszczenie…' : '🧹 Wyczyść listę'}
          </Button>
        </div>
      </div>

      {message && <p className="mt-4 text-sm text-text-hi">{message}</p>}
    </section>
  );
}

export function AdminMatchesPage() {
  const [status, setStatus] = useState<MatchStatus | ''>('');
  const [page, setPage] = useState(0);
  const matches = useMatches({ status: status || undefined, page, size: 25 });

  if (matches.isLoading) return <SectionSkeleton title="Mecze" rows={6} />;
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

      <MaintenanceSection />

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
