import { useMemo, useState } from 'react';
import type { MatchDetail, Side, SubmitResultsRequest } from '../../api/types';
import { useChampions } from '../../api/hooks/champions';
import { roleLabel } from '../../lib/format';
import { cn } from '../../lib/cn';
import { Button } from '../ui/Button';

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

const NUM_FIELDS: { key: keyof Row; label: string }[] = [
  { key: 'kills', label: 'K' },
  { key: 'deaths', label: 'D' },
  { key: 'assists', label: 'A' },
  { key: 'cs', label: 'CS' },
  { key: 'gold', label: 'Gold' },
  { key: 'damageToChampions', label: 'DMG' },
  { key: 'visionScore', label: 'Vis' },
  { key: 'largestMultiKill', label: 'Multi' },
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
                    {f.label}
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
    </div>
  );
}
