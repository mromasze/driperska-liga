import { useEffect, useMemo, useRef, useState } from 'react';
import type { MatchDetail, OcrDraft, OcrLogEntry, Role, Side, SubmitResultsRequest } from '../../api/types';
import { useChampions } from '../../api/hooks/champions';
import { useOcrResults } from '../../api/hooks/matches';
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

export function ResultsForm({
  match,
  submitting,
  onSubmit,
}: {
  match: MatchDetail;
  submitting?: boolean;
  onSubmit: (req: SubmitResultsRequest) => void;
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

  const ocr = useOcrResults(match.id);
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
    if (draft.winningSide) setWinningSide(draft.winningSide);
    if (draft.durationSeconds && draft.durationSeconds > 0) setDuration(draft.durationSeconds);
    setRows((prev) => {
      const next = { ...prev };
      for (const r of draft.rows) {
        if (!next[r.playerId]) continue;
        next[r.playerId] = {
          role: r.role ?? next[r.playerId].role,
          championId: r.championId ?? next[r.playerId].championId,
          kills: r.kills, deaths: r.deaths, assists: r.assists, cs: r.cs, gold: r.gold,
          damageToChampions: r.damageToChampions, visionScore: r.visionScore,
          largestMultiKill: r.largestMultiKill,
        };
      }
      return next;
    });
    const parts: string[] = [`Uzupełniono ${draft.rows.length}/${match.participants.length} graczy.`];
    if (draft.missing.length) parts.push(`Bez danych: ${draft.missing.join(', ')}.`);
    if (draft.unmatched.length) parts.push(`Niedopasowani ze screena: ${draft.unmatched.join(', ')}.`);
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
        <div>
          <span className="kicker">Zwycięska strona</span>
          <div className="mt-1 flex gap-2">
            {(['BLUE', 'RED'] as Side[]).map((s) => (
              <button
                key={s}
                onClick={() => setWinningSide(s)}
                className={cn(
                  'h-10 rounded-md border px-4 text-sm font-semibold',
                  winningSide === s
                    ? 'text-text-hi'
                    : 'border-line text-text-lo hover:text-text',
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
        </div>
        <label>
          <span className="kicker">Czas gry (s)</span>
          <input
            type="number"
            value={duration}
            onChange={(e) => setDuration(Number(e.target.value))}
            className="mt-1 h-10 w-28 rounded-md border border-line bg-bg-1 px-3 text-text-hi"
          />
        </label>
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
                        <select
                          value={r.role}
                          onChange={(e) => setRows((prev) => ({
                            ...prev, [p.playerId]: { ...prev[p.playerId], role: e.target.value as Role },
                          }))}
                          title="Pozycja, na której gracz faktycznie grał w tym meczu"
                          className="mt-0.5 h-7 rounded border border-line bg-bg-1 px-1 text-xs text-text-lo"
                        >
                          {ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}
                        </select>
                      </td>
                      <td className="px-2 py-1.5">
                        <select
                          value={r.championId}
                          onChange={(e) =>
                            setField(p.playerId, 'championId', e.target.value ? Number(e.target.value) : '')
                          }
                          className="h-8 w-36 rounded border border-line bg-bg-1 px-2 text-text-hi"
                        >
                          <option value="">—</option>
                          {championOptions.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      </td>
                      {NUM_FIELDS.map((f) => (
                        <td key={f.key} className="px-1 py-1.5">
                          <input
                            type="number"
                            min={0}
                            value={r[f.key] as number}
                            onChange={(e) => setField(p.playerId, f.key, Math.max(0, Number(e.target.value)))}
                            className="num h-8 w-16 rounded border border-line bg-bg-1 px-2 text-center text-text-hi"
                          />
                        </td>
                      ))}
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
        Najedź na nagłówki kolumn (K / D / A / CS / …), aby zobaczyć, co oznaczają. Poniżej wyjaśnienie punktacji.
      </p>
      <ScoringInfo />
    </div>
  );
}
