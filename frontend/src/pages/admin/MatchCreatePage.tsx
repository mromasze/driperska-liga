import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePlayers } from '../../api/hooks/players';
import { useCurrentSeason } from '../../api/hooks/seasons';
import { useCreateMatch } from '../../api/hooks/matches';
import { Avatar } from '../../components/ui/Avatar';
import { Button } from '../../components/ui/Button';
import { CardSkeleton, ErrorState } from '../../components/ui/States';
import { ManualTeamBuilder } from '../../components/match/ManualTeamBuilder';
import { cn } from '../../lib/cn';
import { roleLabel } from '../../lib/format';
import type { DrawMode, ManualSlot } from '../../api/types';

const POOL_SIZE = 10;

export function MatchCreatePage() {
  const navigate = useNavigate();
  const players = usePlayers({ active: true });
  const season = useCurrentSeason();
  const create = useCreateMatch();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [teams, setTeams] = useState<ManualSlot[]>([]);
  const [drawMode, setDrawMode] = useState<DrawMode>('PURE_RANDOM');

  const isManual = drawMode === 'MANUAL';
  const ready = isManual ? teams.length === POOL_SIZE : selected.size === POOL_SIZE;

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else if (next.size < POOL_SIZE) next.add(id);
      return next;
    });
  };

  const start = () => {
    if (!season.data || !ready) return;
    const body = isManual
      ? { seasonId: season.data.id, drawMode, playerIds: teams.map((s) => s.playerId), teams }
      : { seasonId: season.data.id, drawMode, playerIds: [...selected] };
    create.mutate(body, { onSuccess: (m) => navigate(`/admin/matches/${m.id}/control`) });
  };

  if (players.isLoading) return <CardSkeleton lines={6} />;
  if (players.isError) return <ErrorState error={players.error} />;

  const list = players.data?.content ?? [];

  return (
    <div className="space-y-6">
      <div>
        <div className="kicker text-gold">{season.data?.name ?? 'Sezon'}</div>
        <h1 className="font-display text-3xl">Nowy mecz</h1>
        <p className="mt-1 text-sm text-text-lo">
          {isManual
            ? 'Przeciągnij dokładnie 10 graczy do slotów ról w obu drużynach.'
            : 'Wybierz dokładnie 10 graczy do puli losowania.'}
        </p>
      </div>

      <div className="sticky top-0 z-10 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-line bg-[color:var(--bg-1)]/80 p-3 backdrop-blur">
        <div className="num text-lg">
          <span className={cn(ready ? 'text-win' : 'text-text-hi')}>
            {isManual ? teams.length : selected.size}
          </span>
          <span className="text-text-lo"> / {POOL_SIZE}</span>
        </div>
        <select
          value={drawMode}
          onChange={(e) => setDrawMode(e.target.value as DrawMode)}
          className="h-10 rounded-md border border-line bg-bg-1 px-3 text-sm text-text-hi"
        >
          <option value="PURE_RANDOM">Losowanie czysto losowe (domyślne)</option>
          <option value="BALANCED">Losowanie zbalansowane (MMR)</option>
          <option value="MANUAL">Ręcznie</option>
        </select>
        <Button variant="gold" disabled={!ready || create.isPending} onClick={start}>
          {create.isPending ? 'Tworzenie…' : isManual ? 'Utwórz mecz' : 'Rozpocznij i losuj'}
        </Button>
      </div>

      {isManual ? (
        <ManualTeamBuilder players={list} value={teams} onChange={setTeams} />
      ) : (
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((p) => {
            const active = selected.has(p.id);
            return (
              <button
                key={p.id}
                onClick={() => toggle(p.id)}
                className={cn(
                  'flex items-center gap-3 rounded-lg border p-3 text-left transition-all',
                  active
                    ? 'border-[color:var(--gold)]/60 bg-[color:var(--gold)]/10'
                    : 'border-line bg-[var(--glass)] hover:border-line-strong',
                )}
              >
                <Avatar src={p.avatarUrl} name={p.nickname} size={40} ring={active} />
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium text-text-hi">{p.nickname}</div>
                  <div className="kicker">{roleLabel(p.mainRole)}</div>
                </div>
                {active && <span className="text-gold">✓</span>}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
