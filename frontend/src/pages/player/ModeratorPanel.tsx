import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  useCancelSubmission,
  useCreateSubmission,
  useMySubmissions,
  useSubmitSubmissionResults,
  useUpdateSubmission,
} from '../../api/hooks/moderation';
import { useMatch } from '../../api/hooks/matches';
import { usePlayers } from '../../api/hooks/players';
import { ManualTeamBuilder } from '../../components/match/ManualTeamBuilder';
import { ResultsForm } from '../../components/match/ResultsForm';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { CardSkeleton, EmptyState, ErrorState, SectionSkeleton } from '../../components/ui/States';
import { formatDateTime } from '../../lib/format';
import type { ManualSlot, MatchStatus, Submission } from '../../api/types';

const ROSTER_SIZE = 10;

/** Statuses in which a moderator may still change the roster (see the backend guard). */
const ROSTER_EDITABLE: MatchStatus[] = ['LIVE', 'REJECTED'];

type View =
  | { mode: 'list' }
  | { mode: 'new' }
  | { mode: 'edit'; matchId: string };

/** `datetime-local` needs local wall-clock time, not an ISO instant. */
function toLocalInput(iso?: string | null): string {
  const date = iso ? new Date(iso) : new Date();
  if (Number.isNaN(date.getTime())) return toLocalInput(null);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function statusBadge(submission: Submission) {
  switch (submission.status) {
    case 'LIVE':
      return <Badge tone="info">Szkic — brak statystyk</Badge>;
    case 'RESULTS_SUBMITTED':
      return <Badge tone="pending">Oczekuje na akceptację</Badge>;
    case 'REJECTED':
      return <Badge tone="loss">Odesłany do poprawy</Badge>;
    case 'APPROVED':
      return <Badge tone="win">Zatwierdzony</Badge>;
    default:
      return <Badge>{submission.status}</Badge>;
  }
}

/**
 * The moderator's mini panel: record a match that has already been played and push it to the admin
 * approval queue. Nothing here starts a draw, a champion draft or a Riot lobby — those belong to the
 * live pipeline in the admin panel. A submission stays editable until an admin signs it off.
 */
export function ModeratorPanel() {
  const [view, setView] = useState<View>({ mode: 'list' });
  const submissions = useMySubmissions();
  const cancel = useCancelSubmission();

  const list = submissions.data?.content ?? [];
  const pending = list.filter((s) => s.status === 'RESULTS_SUBMITTED').length;

  if (view.mode === 'new') {
    return (
      <NewSubmission
        onCancel={() => setView({ mode: 'list' })}
        onCreated={(matchId) => setView({ mode: 'edit', matchId })}
      />
    );
  }
  if (view.mode === 'edit') {
    return <SubmissionEditor matchId={view.matchId} onBack={() => setView({ mode: 'list' })} />;
  }

  const withdraw = (submission: Submission) => {
    if (!window.confirm('Wycofać ten wniosek? Wpisane statystyki zostaną utracone.')) return;
    cancel.mutate(submission.id);
  };

  return (
    <section className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="kicker text-gold">Moderator</div>
          <h2 className="font-display text-2xl">Moje wnioski o mecze</h2>
          <p className="mt-1 max-w-2xl text-sm text-text-lo">
            Wprowadzasz mecz, który już się odbył: skład, strony i role, a potem statystyki — ręcznie
            albo ze zrzutów ekranu przez AI. Wniosek trafia do kolejki akceptacji administratora i do
            momentu zatwierdzenia możesz go poprawiać dowolnie wiele razy.
          </p>
        </div>
        <Button variant="gold" onClick={() => setView({ mode: 'new' })}>
          Nowy mecz
        </Button>
      </div>

      {pending > 0 && (
        <p className="text-sm text-text-lo">
          {pending === 1 ? 'Jeden wniosek czeka' : `${pending} wnioski czekają`} na decyzję
          administratora. Do tego czasu możesz je edytować.
        </p>
      )}

      {cancel.isError && <ErrorState error={cancel.error} title="Nie udało się wycofać wniosku" />}

      {submissions.isError ? (
        <ErrorState error={submissions.error} />
      ) : submissions.isLoading ? (
        <SectionSkeleton rows={3} />
      ) : list.length === 0 ? (
        <EmptyState
          title="Brak wniosków"
          description="Kliknij „Nowy mecz”, aby wprowadzić rozegrany mecz."
        />
      ) : (
        <div className="space-y-2">
          {list.map((submission) => (
            <div key={submission.id} className="glass flex flex-wrap items-center gap-3 p-3">
              <div className="min-w-48 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  {statusBadge(submission)}
                  <span className="num text-sm text-text-hi">
                    {formatDateTime(submission.playedAt)}
                  </span>
                </div>
                <div className="mt-1 text-xs text-text-lo">
                  {submission.participantCount} graczy
                  {submission.statsEntered ? ' · statystyki wpisane' : ' · statystyki do wpisania'}
                  {submission.submittedAt && ` · wysłano ${formatDateTime(submission.submittedAt)}`}
                </div>
                {submission.status === 'REJECTED' && submission.rejectionReason && (
                  <p className="mt-2 rounded-md border border-[color:var(--loss)]/40 bg-[color:var(--loss)]/10 p-2 text-xs text-loss">
                    Powód odesłania: {submission.rejectionReason}
                  </p>
                )}
              </div>
              {submission.status === 'APPROVED' ? (
                <Link to={`/matches/${submission.id}`} className="text-sm text-gold hover:underline">
                  Zobacz mecz
                </Link>
              ) : (
                <>
                  <Button
                    size="sm"
                    variant="gold"
                    onClick={() => setView({ mode: 'edit', matchId: submission.id })}
                  >
                    {submission.status === 'LIVE' ? 'Wpisz statystyki' : 'Popraw wniosek'}
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={cancel.isPending}
                    onClick={() => withdraw(submission)}
                  >
                    Wycofaj
                  </Button>
                </>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

/** Step one: who played, on which side and role, and when. */
function NewSubmission({
  onCancel,
  onCreated,
}: {
  onCancel: () => void;
  onCreated: (matchId: string) => void;
}) {
  const players = usePlayers({ active: true, size: 100 });
  const create = useCreateSubmission();
  const [playedAt, setPlayedAt] = useState(toLocalInput(null));
  const [teams, setTeams] = useState<ManualSlot[]>([]);

  const ready = teams.length === ROSTER_SIZE && playedAt !== '';

  const submit = () => {
    if (!ready) return;
    create.mutate(
      { playedAt: new Date(playedAt).toISOString(), teams },
      { onSuccess: (match) => onCreated(match.id) },
    );
  };

  if (players.isError) return <ErrorState error={players.error} />;
  if (players.isLoading) return <CardSkeleton lines={6} />;

  return (
    <section className="space-y-5">
      <div>
        <button type="button" onClick={onCancel} className="text-sm text-text-lo hover:text-text">
          ← Moje wnioski
        </button>
        <h2 className="mt-2 font-display text-2xl">Nowy mecz</h2>
        <p className="mt-1 text-sm text-text-lo">
          Ustaw dokładnie {ROSTER_SIZE} graczy w slotach ról obu drużyn i podaj, kiedy mecz się odbył.
          Statystyki wpiszesz w następnym kroku.
        </p>
      </div>

      <div className="panel flex flex-wrap items-end gap-4 p-4">
        <label>
          <span className="kicker">Data i godzina rozegrania</span>
          <input
            type="datetime-local"
            value={playedAt}
            max={toLocalInput(null)}
            onChange={(event) => setPlayedAt(event.target.value)}
            className="form-control mt-1"
          />
        </label>
        <div className="num text-lg">
          <span className={ready ? 'text-win' : 'text-text-hi'}>{teams.length}</span>
          <span className="text-text-lo"> / {ROSTER_SIZE}</span>
        </div>
        <Button variant="gold" disabled={!ready || create.isPending} onClick={submit}>
          {create.isPending ? 'Tworzenie…' : 'Dalej — statystyki'}
        </Button>
      </div>

      {create.isError && <ErrorState error={create.error} title="Nie udało się utworzyć wniosku" />}

      <ManualTeamBuilder players={players.data?.content ?? []} value={teams} onChange={setTeams} />
    </section>
  );
}

/** Step two: statistics (AI or by hand), plus corrections to the date and — before sending — roster. */
function SubmissionEditor({ matchId, onBack }: { matchId: string; onBack: () => void }) {
  const match = useMatch(matchId);
  const players = usePlayers({ active: true, size: 100 });
  const update = useUpdateSubmission(matchId);
  const submit = useSubmitSubmissionResults(matchId);
  const [rosterOpen, setRosterOpen] = useState(false);
  const [playedAt, setPlayedAt] = useState<string | null>(null);
  const [teams, setTeams] = useState<ManualSlot[] | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const current = match.data;
  const rosterFromMatch = useMemo<ManualSlot[]>(
    () => (current?.participants ?? []).map((p) => ({
      playerId: p.playerId, side: p.side, role: p.role,
    })),
    [current],
  );

  if (match.isError) return <ErrorState error={match.error} />;
  if (match.isLoading || !current) return <CardSkeleton lines={8} />;

  const rosterEditable = ROSTER_EDITABLE.includes(current.status);
  const dateValue = playedAt ?? toLocalInput(current.startedAt);
  const rosterValue = teams ?? rosterFromMatch;

  const saveDate = () => {
    setSaved(null);
    update.mutate(
      { playedAt: new Date(dateValue).toISOString() },
      { onSuccess: () => setSaved('Zapisano datę rozegrania.') },
    );
  };

  const saveRoster = () => {
    if (rosterValue.length !== ROSTER_SIZE) return;
    if (!window.confirm(
      'Zmiana składu usuwa statystyki wpisane dla obecnych graczy. Kontynuować?',
    )) return;
    setSaved(null);
    update.mutate(
      { teams: rosterValue },
      {
        onSuccess: () => {
          setTeams(null);
          setRosterOpen(false);
          setSaved('Zapisano skład. Statystyki trzeba wpisać od nowa.');
        },
      },
    );
  };

  return (
    <section className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <button type="button" onClick={onBack} className="text-sm text-text-lo hover:text-text">
            ← Moje wnioski
          </button>
          <h2 className="mt-2 font-display text-2xl">
            {current.status === 'LIVE' ? 'Statystyki meczu' : 'Poprawa wniosku'}
          </h2>
          <p className="mt-1 text-sm text-text-lo">
            {current.status === 'RESULTS_SUBMITTED'
              ? 'Wniosek czeka na administratora. Każde ponowne wysłanie nadpisuje to, co widzi w kolejce.'
              : current.status === 'REJECTED'
                ? 'Administrator odesłał wniosek do poprawy — popraw dane i wyślij ponownie.'
                : 'Wpisz statystyki i wyślij wniosek do akceptacji.'}
          </p>
        </div>
        {current.approval?.rejectionReason && current.status === 'REJECTED' && (
          <p className="max-w-sm rounded-md border border-[color:var(--loss)]/40 bg-[color:var(--loss)]/10 p-3 text-sm text-loss">
            Powód odesłania: {current.approval.rejectionReason}
          </p>
        )}
      </div>

      <div className="panel flex flex-wrap items-end gap-4 p-4">
        <label>
          <span className="kicker">Data i godzina rozegrania</span>
          <input
            type="datetime-local"
            value={dateValue}
            max={toLocalInput(null)}
            onChange={(event) => setPlayedAt(event.target.value)}
            className="form-control mt-1"
          />
        </label>
        <Button variant="ghost" size="sm" disabled={update.isPending} onClick={saveDate}>
          {update.isPending ? 'Zapisywanie…' : 'Zapisz datę'}
        </Button>
        {rosterEditable ? (
          <Button variant="ghost" size="sm" onClick={() => setRosterOpen((open) => !open)}>
            {rosterOpen ? 'Zwiń skład' : 'Zmień skład'}
          </Button>
        ) : (
          <span className="text-xs text-text-lo">
            Skład jest zablokowany, dopóki wniosek jest w kolejce. Aby go zmienić, poproś
            administratora o odesłanie wniosku do poprawy.
          </span>
        )}
        {saved && <span className="text-sm text-win">{saved}</span>}
      </div>

      {update.isError && <ErrorState error={update.error} title="Nie udało się zapisać zmiany" />}

      {rosterOpen && rosterEditable && (
        <div className="space-y-3">
          {players.isLoading ? (
            <CardSkeleton lines={6} />
          ) : (
            <ManualTeamBuilder
              players={players.data?.content ?? []}
              value={rosterValue}
              onChange={setTeams}
            />
          )}
          <div className="flex items-center gap-3">
            <Button
              variant="gold"
              size="sm"
              disabled={rosterValue.length !== ROSTER_SIZE || update.isPending}
              onClick={saveRoster}
            >
              Zapisz skład
            </Button>
            <span className="text-xs text-text-lo">
              Wymagane {ROSTER_SIZE} graczy ({rosterValue.length}/{ROSTER_SIZE}).
            </span>
          </div>
        </div>
      )}

      {submit.isError && <ErrorState error={submit.error} title="Nie udało się wysłać wniosku" />}
      {submit.isSuccess && (
        <p className="rounded-md border border-[color:var(--win)]/40 bg-[color:var(--win)]/10 p-3 text-sm text-win">
          ✓ Wniosek jest w kolejce akceptacji. Możesz go dalej poprawiać do momentu zatwierdzenia.
        </p>
      )}

      <ResultsForm
        match={current}
        ocrScope="moderation"
        submitting={submit.isPending}
        onSubmit={(body) => {
          setSaved(null);
          submit.mutate(body);
        }}
      />
    </section>
  );
}
