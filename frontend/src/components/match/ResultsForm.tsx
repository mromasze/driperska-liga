import { useEffect, useMemo, useRef, useState } from 'react';
import type { MatchDetail, OcrDraft, OcrLogEntry, Role, Side, SubmitResultsRequest } from '../../api/types';
import { useChampions } from '../../api/hooks/champions';
import { useOcrResults, type OcrScope } from '../../api/hooks/matches';
import { roleLabel } from '../../lib/format';
import { cn } from '../../lib/cn';
import { Button } from '../ui/Button';
import { ScoringInfo } from '../ScoringInfo';

interface Row {
  role: Role;
  championId: number | '';
  kills: number;
  deaths: number;
  assists: number;
  cs: number;
  gold: number;
  damageToChampions: number;
  visionScore: number;
  largestMultiKill: number;
}

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

const EMPTY: Row = {
  role: 'MID',
  championId: '',
  kills: 0,
  deaths: 0,
  assists: 0,
  cs: 0,
  gold: 0,
  damageToChampions: 0,
  visionScore: 0,
  largestMultiKill: 0,
};

const NUM_FIELDS: { key: keyof Row; label: string; desc: string }[] = [
  { key: 'kills', label: 'K', desc: 'Zabójstwa (kills)' },
  { key: 'deaths', label: 'D', desc: 'Śmierci (deaths)' },
  { key: 'assists', label: 'A', desc: 'Asysty (assists)' },
  { key: 'cs', label: 'CS', desc: 'Creep Score — zabite stwory i potwory (minony + jungle)' },
  { key: 'gold', label: 'Gold', desc: 'Złoto zdobyte w meczu' },
  { key: 'damageToChampions', label: 'DMG', desc: 'Obrażenia zadane bohaterom przeciwnika' },
  { key: 'visionScore', label: 'Vis', desc: 'Vision Score — wynik wizji (wardy, odsłona i odbieranie wizji)' },
  { key: 'largestMultiKill', label: 'Multi', desc: 'Największy multi-kill (2=double … 5=pentakill) — bonusy LP za pentę/quadrę' },
];

/* ============================================================
   Field locks.

   Every screenshot upload re-sends the whole set of images and the model answers with a fresh read
   of all ten rows, so a value corrected by hand used to be silently clobbered by the next upload.
   A lock pins a field: the OCR merge skips it, and the input goes read-only so it can't be nudged
   by accident either. Locks live only for as long as the form is open — they are a scratch pad for
   one sitting of data entry, not a stored preference.
   ============================================================ */

/** Every field the OCR pass writes, and therefore every field worth locking. */
const LOCKABLE_KEYS: (keyof Row)[] = ['role', 'championId', ...NUM_FIELDS.map((f) => f.key)];

/** `patch` is absent — the model never returns it, so a lock there would be decoration. */
const MATCH_LOCK = { winningSide: 'match:winningSide', duration: 'match:duration' } as const;

const rowLockKey = (playerId: string, key: keyof Row) => `${playerId}:${key}`;

function PadlockIcon({ open }: { open: boolean }) {
  return (
    <svg viewBox="0 0 12 12" width="9" height="9" aria-hidden="true" focusable="false">
      {/* A closed padlock brings the shackle's right leg back down onto the body; an open one doesn't. */}
      <path
        d={open ? 'M4,5.4 V3.9 a2,2 0 0 1 4,0' : 'M4,5.4 V3.9 a2,2 0 0 1 4,0 V5.4'}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinecap="round"
      />
      <rect x="2.4" y="5.4" width="7.2" height="5.1" rx="1.1" fill="currentColor" />
    </svg>
  );
}

/**
 * Tiny padlock riding the top-right corner of a field. Ten of these visible across a table row
 * would drown the numbers, so an unlocked one stays transparent until its cell is hovered or the
 * button itself is focused. A locked one is always gold and always visible — that's the whole point.
 *
 * Must be rendered inside an element carrying `group/field relative`.
 */
function LockToggle({ locked, onToggle, label }: { locked: boolean; onToggle: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={locked}
      aria-label={locked ? `Odblokuj: ${label}` : `Zablokuj: ${label}`}
      title={
        locked
          ? `${label}\nZablokowane — AI tego nie nadpisze. Kliknij, aby odblokować.`
          : `${label}\nKliknij, aby zablokować przed nadpisaniem przy kolejnym wgraniu screenów.`
      }
      className={cn(
        'absolute -right-1.5 -top-1.5 z-10 grid h-4 w-4 place-items-center rounded-full border transition',
        locked
          ? 'border-[color:var(--gold)] bg-gold text-[#1a1205]'
          : 'border-line bg-bg-2 text-text-lo opacity-0 group-hover/field:opacity-70 hover:!opacity-100 focus-visible:opacity-100',
      )}
    >
      <PadlockIcon open={!locked} />
    </button>
  );
}

export function ResultsForm({
  match,
  submitting,
  onSubmit,
  ocrScope = 'admin',
}: {
  match: MatchDetail;
  submitting?: boolean;
  onSubmit: (req: SubmitResultsRequest) => void;
  /** Which endpoint reads the screenshots — see {@link useOcrResults}. */
  ocrScope?: OcrScope;
}) {
  const champions = useChampions();
  const [winningSide, setWinningSide] = useState<Side | ''>(match.winningSide ?? '');
  const [duration, setDuration] = useState<number>(match.durationSeconds ?? 1800);
  const [patch, setPatch] = useState<string>(match.patch ?? '');
  const [rows, setRows] = useState<Record<string, Row>>(() =>
    Object.fromEntries(
      match.participants.map((p) => [
        p.playerId,
        {
          ...EMPTY,
          role: p.role,
          championId: p.championId ?? '',
          kills: p.kills,
          deaths: p.deaths,
          assists: p.assists,
          cs: p.cs,
          gold: p.gold,
          damageToChampions: p.damageToChampions,
          visionScore: p.visionScore,
          largestMultiKill: p.largestMultiKill,
        },
      ]),
    ),
  );

  const championOptions = useMemo(
    () => (champions.data ?? []).slice().sort((a, b) => a.name.localeCompare(b.name)),
    [champions.data],
  );

  const setField = (playerId: string, key: keyof Row, value: number | '') => {
    setRows((prev) => ({ ...prev, [playerId]: { ...prev[playerId], [key]: value } }));
  };

  const [locks, setLocks] = useState<ReadonlySet<string>>(() => new Set());

  const toggleLock = (key: string) => {
    setLocks((prev) => {
      const next = new Set(prev);
      if (!next.delete(key)) next.add(key);
      return next;
    });
  };

  const lockEverything = () => {
    setLocks(new Set([
      ...Object.values(MATCH_LOCK),
      ...match.participants.flatMap((p) => LOCKABLE_KEYS.map((key) => rowLockKey(p.playerId, key))),
    ]));
  };

  const ocr = useOcrResults(match.id, ocrScope);
  const shotInput = useRef<HTMLInputElement>(null);
  const [ocrNote, setOcrNote] = useState<string | null>(null);
  const [ocrLogs, setOcrLogs] = useState<OcrLogEntry[]>([]);
  const [shots, setShots] = useState<File[]>([]);
  const ocrLogTimers = useRef<number[]>([]);

  const stopLogTimers = () => {
    ocrLogTimers.current.forEach((timer) => window.clearTimeout(timer));
    ocrLogTimers.current = [];
  };

  const startUploadLog = (fileCount: number) => {
    stopLogTimers();
    setOcrLogs([{ stage: 'UPLOAD', message: `Wysyłam ${fileCount} screenshot(y) do backendu…` }]);
    const schedule = (delay: number, stage: string, message: string) => {
      ocrLogTimers.current.push(window.setTimeout(() => {
        setOcrLogs((current) => [...current, { stage, message }]);
      }, delay));
    };
    schedule(400, 'PREPROCESS', 'Skalowanie i kompresja obrazów do formatu dla modelu…');
    schedule(1_200, 'CONTEXT', 'Przygotowywanie atlasu portretów championów z Data Dragon…');
    schedule(2_200, 'REQUEST', 'Budowanie kontekstu meczu i wiadomości systemowej…');
    schedule(3_500, 'OLLAMA', 'Ollama analizuje screenshoty oraz obrazy referencyjne…');
    schedule(12_000, 'OLLAMA', 'Model nadal pracuje — analiza kilku zakładek może potrwać chwilę.');
  };

  useEffect(() => () => {
    ocrLogTimers.current.forEach((timer) => window.clearTimeout(timer));
  }, []);

  const applyDraft = (draft: OcrDraft) => {
    const sideLocked = locks.has(MATCH_LOCK.winningSide);
    const durationLocked = locks.has(MATCH_LOCK.duration);
    if (draft.winningSide && !sideLocked) setWinningSide(draft.winningSide);
    if (draft.durationSeconds && draft.durationSeconds > 0 && !durationLocked) setDuration(draft.durationSeconds);
    setRows((prev) => {
      const next = { ...prev };
      for (const r of draft.rows) {
        const current = next[r.playerId];
        if (!current) continue;
        // A locked field keeps what's already in the form; everything else takes the model's read.
        const keep = (key: keyof Row) => locks.has(rowLockKey(r.playerId, key));
        next[r.playerId] = {
          role: keep('role') ? current.role : r.role ?? current.role,
          championId: keep('championId') ? current.championId : r.championId ?? current.championId,
          kills: keep('kills') ? current.kills : r.kills,
          deaths: keep('deaths') ? current.deaths : r.deaths,
          assists: keep('assists') ? current.assists : r.assists,
          cs: keep('cs') ? current.cs : r.cs,
          gold: keep('gold') ? current.gold : r.gold,
          damageToChampions: keep('damageToChampions') ? current.damageToChampions : r.damageToChampions,
          visionScore: keep('visionScore') ? current.visionScore : r.visionScore,
          largestMultiKill: keep('largestMultiKill') ? current.largestMultiKill : r.largestMultiKill,
        };
      }
      return next;
    });
    // Counted outside the updater above: React may run that callback more than once (it does in
    // StrictMode), which would double the tally.
    const skipped = draft.rows.reduce(
      (sum, r) => sum + LOCKABLE_KEYS.filter((key) => locks.has(rowLockKey(r.playerId, key))).length,
      (sideLocked && draft.winningSide ? 1 : 0)
        + (durationLocked && draft.durationSeconds ? 1 : 0),
    );
    const withChampion = draft.rows.filter((r) => r.championId != null).length;
    const parts: string[] = [`Uzupełniono ${draft.rows.length}/${match.participants.length} graczy.`];
    parts.push(`Postacie: ${withChampion}/${draft.rows.length}.`);
    if (draft.missing.length) parts.push(`Bez danych: ${draft.missing.join(', ')}.`);
    if (draft.unmatched.length) parts.push(`Niedopasowani ze screena: ${draft.unmatched.join(', ')}.`);
    // Without this the model's champion guesses vanished silently and it looked like it had read
    // nothing at all, when in fact the name just did not map to a champion in the database.
    if (draft.unmatchedChampions?.length) {
      parts.push(`Nierozpoznane postacie: ${draft.unmatchedChampions.join(', ')}.`);
    }
    if (skipped > 0) parts.push(`Zachowano ${skipped} zablokowanych pól.`);
    parts.push('Sprawdź i popraw ręcznie przed wysłaniem.');
    setOcrNote(parts.join(' '));
  };

  // Accumulate screenshots and re-send the whole set each time, so adding tabs one-by-one
  // still merges correctly (the model combines all images).
  const addShots = (files: FileList | null) => {
    const added = files ? Array.from(files) : [];
    if (added.length === 0) return;
    const all = [...shots, ...added];
    setShots(all);
    setOcrNote(null);
    startUploadLog(all.length);
    ocr.mutate(all, {
      onSuccess: (draft) => {
        stopLogTimers();
        setOcrLogs(draft.logs ?? []);
        applyDraft(draft);
      },
      onError: (e) => {
        stopLogTimers();
        const message = (e as Error).message;
        setOcrLogs((current) => [...current, { stage: 'ERROR', message }]);
        setOcrNote('⚠ ' + message);
      },
    });
  };
  const clearShots = () => {
    stopLogTimers();
    setShots([]);
    setOcrNote(null);
    setOcrLogs([]);
  };

  const allChampionsPicked = match.participants.every((p) => rows[p.playerId]?.championId !== '');
  const canSubmit = winningSide !== '' && duration > 0 && allChampionsPicked;

  const submit = () => {
    if (!canSubmit) return;
    onSubmit({
      winningSide: winningSide as Side,
      durationSeconds: duration,
      patch,
      participants: match.participants.map((p) => {
        const r = rows[p.playerId];
        return {
          playerId: p.playerId,
          role: r.role,
          championId: r.championId as number,
          kills: r.kills,
          deaths: r.deaths,
          assists: r.assists,
          cs: r.cs,
          gold: r.gold,
          damageToChampions: r.damageToChampions,
          visionScore: r.visionScore,
          largestMultiKill: r.largestMultiKill,
        };
      }),
    });
  };

  return (
    <div className="space-y-5">
      <div className="rounded-lg border border-[color:var(--cyan)]/40 bg-[color:var(--cyan)]/10 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="font-semibold text-text-hi">🤖 Uzupełnij ze screenshotów (AI)</div>
            <p className="text-xs text-text-lo">
              Wgraj zrzut(y) ekranu podsumowania z LoL — AI odczyta statystyki i wypełni tabelę.
              Możesz dodać kilka naraz (np. osobne zakładki: KDA, obrażenia, wizja).
            </p>
          </div>
          <div className="flex items-center gap-2">
            <input ref={shotInput} type="file" accept="image/*" multiple className="hidden"
              onChange={(e) => { addShots(e.target.files); e.currentTarget.value = ''; }} />
            <Button variant="ghost" size="sm" disabled={ocr.isPending} onClick={() => shotInput.current?.click()}>
              {ocr.isPending ? 'Analizuję…' : shots.length ? `Dodaj kolejny (${shots.length})` : 'Wgraj screenshoty'}
            </Button>
            {shots.length > 0 && !ocr.isPending && (
              <Button variant="ghost" size="sm" onClick={clearShots}>Wyczyść</Button>
            )}
          </div>
        </div>
        {ocrNote && <p className="mt-2 text-sm text-text">{ocrNote}</p>}

        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 border-t border-line pt-3 text-xs text-text-lo">
          <span className="inline-flex items-center gap-1.5">
            <span className="grid h-4 w-4 shrink-0 place-items-center rounded-full border border-[color:var(--gold)] bg-gold text-[#1a1205]">
              <PadlockIcon open={false} />
            </span>
            Kłódka przy polu chroni jego wartość przed nadpisaniem przy kolejnym wgraniu screenów.
          </span>
          {locks.size > 0 && (
            <span className="chip border-[color:var(--gold)]/30 text-gold">{locks.size} zablokowanych</span>
          )}
          <span className="ml-auto flex items-center gap-2">
            <button type="button" onClick={lockEverything} className="rounded border border-line px-2 py-1 hover:text-text-hi">
              Zablokuj wszystko
            </button>
            <button
              type="button"
              onClick={() => setLocks(new Set())}
              disabled={locks.size === 0}
              className="rounded border border-line px-2 py-1 hover:text-text-hi disabled:opacity-40"
            >
              Odblokuj wszystko
            </button>
          </span>
        </div>

        {ocrLogs.length > 0 && (
          <div className="mt-3 overflow-hidden rounded-md border border-line bg-bg-0/80">
            <div className="flex items-center justify-between border-b border-line px-3 py-2">
              <span className="font-mono text-[11px] font-semibold uppercase tracking-wider text-text-lo">
                Dziennik analizy OCR
              </span>
              {ocr.isPending && <span className="animate-pulse text-xs text-[color:var(--cyan)]">pracuję…</span>}
            </div>
            <div className="max-h-72 space-y-1 overflow-y-auto p-3 font-mono text-xs">
              {ocrLogs.map((entry, index) => (
                <div key={`${entry.stage}-${index}`} className="grid grid-cols-[5.5rem_1fr] gap-2">
                  <span className={cn(
                    'font-semibold',
                    entry.stage === 'ERROR' ? 'text-[color:var(--red)]'
                      : entry.stage === 'MODEL' ? 'text-[color:var(--gold)]'
                        : 'text-[color:var(--cyan)]',
                  )}>
                    [{entry.stage}]
                  </span>
                  <pre className="whitespace-pre-wrap break-words font-mono text-text">{entry.message}</pre>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="panel flex flex-wrap items-end gap-4 p-4">
        <div className="group/field relative">
          <span className="kicker">Zwycięska strona</span>
          <div className="mt-1 flex gap-2">
            {(['BLUE', 'RED'] as Side[]).map((s) => (
              <button
                key={s}
                onClick={() => setWinningSide(s)}
                disabled={locks.has(MATCH_LOCK.winningSide)}
                className={cn(
                  'h-10 rounded-md border px-4 text-sm font-semibold disabled:cursor-not-allowed',
                  winningSide === s
                    ? 'text-text-hi'
                    : 'border-line text-text-lo hover:text-text disabled:hover:text-text-lo',
                )}
                style={
                  winningSide === s
                    ? {
                        borderColor: s === 'BLUE' ? 'var(--blue)' : 'var(--red)',
                        background: s === 'BLUE' ? 'var(--blue-bg)' : 'var(--red-bg)',
                      }
                    : undefined
                }
              >
                {s === 'BLUE' ? 'Niebiescy' : 'Czerwoni'}
              </button>
            ))}
          </div>
          <LockToggle
            locked={locks.has(MATCH_LOCK.winningSide)}
            onToggle={() => toggleLock(MATCH_LOCK.winningSide)}
            label="Zwycięska strona"
          />
        </div>
        {/* The lock sits outside the <label> on purpose — nested inside, clicking it would also
            activate the label and steal focus into the input. */}
        <div className="group/field relative">
          <label>
            <span className="kicker">Czas gry (s)</span>
            <input
              type="number"
              value={duration}
              readOnly={locks.has(MATCH_LOCK.duration)}
              onChange={(e) => setDuration(Number(e.target.value))}
              className={cn(
                'mt-1 h-10 w-28 rounded-md border bg-bg-1 px-3',
                locks.has(MATCH_LOCK.duration)
                  ? 'border-[color:var(--gold)]/70 text-gold'
                  : 'border-line text-text-hi',
              )}
            />
          </label>
          <LockToggle
            locked={locks.has(MATCH_LOCK.duration)}
            onToggle={() => toggleLock(MATCH_LOCK.duration)}
            label="Czas gry"
          />
        </div>
        <label>
          <span className="kicker">Patch</span>
          <input
            value={patch}
            onChange={(e) => setPatch(e.target.value)}
            placeholder="14.13"
            className="mt-1 h-10 w-24 rounded-md border border-line bg-bg-1 px-3 text-text-hi placeholder:text-text-lo"
          />
        </label>
      </div>

      {(['BLUE', 'RED'] as Side[]).map((side) => (
        <div key={side} className="glass overflow-x-auto p-3">
          <div
            className="mb-2 font-display font-semibold"
            style={{ color: side === 'BLUE' ? 'var(--blue)' : 'var(--red)' }}
          >
            {side === 'BLUE' ? 'Niebiescy' : 'Czerwoni'}
          </div>
          <table className="w-full min-w-[720px] text-sm">
            <thead>
              <tr className="kicker text-left">
                <th className="px-2 py-1">Gracz</th>
                <th className="px-2 py-1">Champion</th>
                {NUM_FIELDS.map((f) => (
                  <th key={f.key} className="px-1 py-1 text-center">
                    <abbr title={f.desc} className="cursor-help no-underline decoration-dotted underline-offset-2 hover:underline">
                      {f.label}
                    </abbr>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {match.participants
                .filter((p) => p.side === side)
                .map((p) => {
                  const r = rows[p.playerId];
                  return (
                    <tr key={p.playerId} className="border-t border-line">
                      <td className="px-2 py-1.5">
                        <div className="font-medium text-text-hi">{p.nickname}</div>
                        <div className="group/field relative mt-0.5 inline-block">
                          <select
                            value={r.role}
                            disabled={locks.has(rowLockKey(p.playerId, 'role'))}
                            onChange={(e) => setRows((prev) => ({
                              ...prev, [p.playerId]: { ...prev[p.playerId], role: e.target.value as Role },
                            }))}
                            title="Pozycja, na której gracz faktycznie grał w tym meczu"
                            className={cn(
                              'h-7 rounded border bg-bg-1 px-1 text-xs disabled:opacity-100',
                              locks.has(rowLockKey(p.playerId, 'role'))
                                ? 'border-[color:var(--gold)]/70 text-gold'
                                : 'border-line text-text-lo',
                            )}
                          >
                            {ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}
                          </select>
                          <LockToggle
                            locked={locks.has(rowLockKey(p.playerId, 'role'))}
                            onToggle={() => toggleLock(rowLockKey(p.playerId, 'role'))}
                            label={`${p.nickname} — pozycja`}
                          />
                        </div>
                      </td>
                      <td className="px-2 py-1.5">
                        <div className="group/field relative inline-block">
                          <select
                            value={r.championId}
                            disabled={locks.has(rowLockKey(p.playerId, 'championId'))}
                            onChange={(e) =>
                              setField(p.playerId, 'championId', e.target.value ? Number(e.target.value) : '')
                            }
                            className={cn(
                              'h-8 w-36 rounded border bg-bg-1 px-2 disabled:opacity-100',
                              locks.has(rowLockKey(p.playerId, 'championId'))
                                ? 'border-[color:var(--gold)]/70 text-gold'
                                : 'border-line text-text-hi',
                            )}
                          >
                            <option value="">—</option>
                            {championOptions.map((c) => (
                              <option key={c.id} value={c.id}>
                                {c.name}
                              </option>
                            ))}
                          </select>
                          <LockToggle
                            locked={locks.has(rowLockKey(p.playerId, 'championId'))}
                            onToggle={() => toggleLock(rowLockKey(p.playerId, 'championId'))}
                            label={`${p.nickname} — champion`}
                          />
                        </div>
                      </td>
                      {NUM_FIELDS.map((f) => {
                        const lockKey = rowLockKey(p.playerId, f.key);
                        const locked = locks.has(lockKey);
                        return (
                          <td key={f.key} className="px-1 py-1.5">
                            <div className="group/field relative inline-block">
                              <input
                                type="number"
                                min={0}
                                value={r[f.key] as number}
                                readOnly={locked}
                                onChange={(e) => setField(p.playerId, f.key, Math.max(0, Number(e.target.value)))}
                                className={cn(
                                  'num h-8 w-16 rounded border bg-bg-1 px-2 text-center',
                                  locked ? 'border-[color:var(--gold)]/70 text-gold' : 'border-line text-text-hi',
                                )}
                              />
                              <LockToggle
                                locked={locked}
                                onToggle={() => toggleLock(lockKey)}
                                label={`${p.nickname} — ${f.desc}`}
                              />
                            </div>
                          </td>
                        );
                      })}
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>
      ))}

      <div className="flex items-center gap-3">
        <Button variant="gold" disabled={!canSubmit || submitting} onClick={submit}>
          {submitting ? 'Wysyłanie…' : 'Wyślij do akceptacji'}
        </Button>
        {!canSubmit && (
          <span className="text-xs text-text-lo">
            Uzupełnij zwycięską stronę i wszystkich championów.
          </span>
        )}
      </div>

      <p className="text-xs text-text-lo">
        Najedź na nagłówki kolumn (K / D / A / CS / …), aby zobaczyć, co oznaczają. Najedź na pole,
        aby odsłonić kłódkę — zablokowanego pola AI nie nadpisze przy kolejnym wgraniu screenów.
        Poniżej wyjaśnienie punktacji.
      </p>
      <ScoringInfo />
    </div>
  );
}
