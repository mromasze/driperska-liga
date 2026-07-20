import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePlayers } from '../../api/hooks/players';
import { useCurrentSeason, useSeasons } from '../../api/hooks/seasons';
import { useCreateMatch } from '../../api/hooks/matches';
import type { DrawMode } from '../../api/types';
import { Card, CardBody, CardHeader, CardTitle } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { roleLabel } from '../../lib/format';
import { cn } from '../../lib/cn';

const DRAW_MODES: { value: DrawMode; label: string; hint: string }[] = [
  { value: 'BALANCED', label: 'Zbalansowane', hint: 'Minimalizuje różnicę MMR (domyślne)' },
  { value: 'PURE_RANDOM', label: 'Losowe', hint: 'Czysta losowa permutacja' },
  { value: 'MANUAL', label: 'Ręczne', hint: 'Admin ustawia strony' },
];

export function MatchCreatePage() {
  const navigate = useNavigate();
  const players = usePlayers({ active: true });
  const seasons = useSeasons();
  const currentSeason = useCurrentSeason();
  const createMatch = useCreateMatch();

  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [drawMode, setDrawMode] = useState<DrawMode>('BALANCED');
  const [seasonId, setSeasonId] = useState<string>('');

  const effectiveSeasonId = seasonId || currentSeason.data?.id || '';
  const count = selected.size;
  const canCreate = count === 10 && Boolean(effectiveSeasonId);

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function handleCreate() {
    if (!canCreate) return;
    createMatch.mutate(
      { seasonId: effectiveSeasonId, drawMode, playerIds: [...selected] },
      { onSuccess: (match) => navigate(`/admin/matches/${match.id}/control`) },
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl">Nowy mecz</h1>
        <p className="text-sm text-text-lo">
          Wybierz pulę 10 graczy, tryb losowania i sezon. Po utworzeniu przejdziesz do kontroli
          meczu (losowanie składów).
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
        {/* Player pool */}
        <Card>
          <CardHeader className="flex items-center justify-between">
            <CardTitle>Pula graczy</CardTitle>
            <Badge tone={count === 10 ? 'win' : 'pending'}>{count} / 10</Badge>
          </CardHeader>
          <CardBody className="p-0">
            {players.isLoading ? (
              <LoadingState />
            ) : players.isError ? (
              <div className="p-4">
                <ErrorState error={players.error} />
              </div>
            ) : (
              <ul className="grid gap-px bg-line sm:grid-cols-2">
                {(players.data ?? []).map((p) => {
                  const active = selected.has(p.id);
                  return (
                    <li key={p.id}>
                      <button
                        type="button"
                        onClick={() => toggle(p.id)}
                        className={cn(
                          'flex w-full items-center gap-3 px-4 py-3 text-left transition',
                          active ? 'bg-bg-2' : 'bg-bg-1 hover:bg-bg-2',
                        )}
                      >
                        <input
                          type="checkbox"
                          readOnly
                          checked={active}
                          className="h-4 w-4 accent-[var(--gold)]"
                        />
                        <Avatar src={p.avatarUrl} name={p.nickname} size={32} />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm text-text-hi">{p.nickname}</span>
                          <span className="block text-xs text-text-lo">
                            {roleLabel(p.mainRole)}
                          </span>
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </CardBody>
        </Card>

        {/* Options */}
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Tryb losowania</CardTitle>
            </CardHeader>
            <CardBody className="space-y-2">
              {DRAW_MODES.map((mode) => (
                <label
                  key={mode.value}
                  className={cn(
                    'flex cursor-pointer items-start gap-3 rounded-sm border p-3 transition',
                    drawMode === mode.value
                      ? 'border-[var(--gold)]/50 bg-bg-2'
                      : 'border-line hover:bg-bg-2',
                  )}
                >
                  <input
                    type="radio"
                    name="drawMode"
                    checked={drawMode === mode.value}
                    onChange={() => setDrawMode(mode.value)}
                    className="mt-0.5 accent-[var(--gold)]"
                  />
                  <span>
                    <span className="block text-sm text-text-hi">{mode.label}</span>
                    <span className="block text-xs text-text-lo">{mode.hint}</span>
                  </span>
                </label>
              ))}
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Sezon</CardTitle>
            </CardHeader>
            <CardBody>
              <select
                value={effectiveSeasonId}
                onChange={(e) => setSeasonId(e.target.value)}
                className="w-full rounded-sm border border-line bg-bg-0 px-3 py-2 text-sm text-text-hi outline-none focus:border-[var(--gold)]"
              >
                {(seasons.data ?? []).length === 0 && <option value="">Aktywny sezon</option>}
                {(seasons.data ?? []).map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </CardBody>
          </Card>

          {createMatch.isError && <ErrorState error={createMatch.error} title="Nie utworzono" />}

          <Button
            variant="gold"
            className="w-full"
            disabled={!canCreate || createMatch.isPending}
            onClick={handleCreate}
          >
            {createMatch.isPending ? 'Tworzenie…' : 'Utwórz mecz'}
          </Button>
          {count !== 10 && (
            <p className="text-center text-xs text-text-lo">Wybierz dokładnie 10 graczy.</p>
          )}
        </div>
      </div>
    </div>
  );
}
