import { useMemo, useRef, useState } from 'react';
import type { MatchDetail, OcrDraft, Side, SubmitResultsRequest } from '../../api/types';
import { useChampions } from '../../api/hooks/champions';
import { useOcrResults } from '../../api/hooks/matches';
import { roleLabel } from '../../lib/format';
import { cn } from '../../lib/cn';
import { Button } from '../ui/Button';
import { ScoringInfo } from '../ScoringInfo';

interface Row {
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

const EMPTY: Row = {
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
  const [shots, setShots] = useState<File[]>([]);

  const applyDraft = (draft: OcrDraft) => {
    if (draft.winningSide) setWinningSide(draft.winningSide);
    if (draft.durationSeconds && draft.durationSeconds > 0) setDuration(draft.durationSeconds);
    setRows((prev) => {
      const next = { ...prev };
      for (const r of draft.rows) {
        if (!next[r.playerId]) continue;
        next[r.playerId] = {
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
    ocr.mutate(all, {
      onSuccess: applyDraft,
      onError: (e) => setOcrNote('⚠ ' + (e as Error).message),
    });
  };
  const clearShots = () => { setShots([]); setOcrNote(null); };

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
          role: p.role,
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
                        <div className="kicker">{roleLabel(p.role)}</div>
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
