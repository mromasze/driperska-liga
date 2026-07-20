import { useState } from 'react';
import { usePlayers } from '../api/hooks/players';
import { PlayerCard } from '../components/player/PlayerCard';
import { LoadingState, ErrorState, EmptyState } from '../components/ui/States';
import type { Role } from '../api/types';
import { roleLabel } from '../lib/format';
import { cn } from '../lib/cn';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

export function PlayersPage() {
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<Role | undefined>(undefined);
  const players = usePlayers({
    search: search.trim() || undefined,
    role,
    active: true,
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl">Gracze</h1>
        <p className="text-sm text-text-lo">Zawodnicy Driperskiej Ligi.</p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Szukaj po nicku…"
          className="min-w-[12rem] flex-1 rounded-sm border border-line bg-bg-1 px-3 py-2 text-sm text-text-hi outline-none focus:border-[var(--gold)]"
        />
        <div className="flex flex-wrap gap-1">
          <RoleChip active={role === undefined} onClick={() => setRole(undefined)}>
            Wszystkie
          </RoleChip>
          {ROLES.map((r) => (
            <RoleChip key={r} active={role === r} onClick={() => setRole(r)}>
              {roleLabel(r)}
            </RoleChip>
          ))}
        </div>
      </div>

      {players.isLoading ? (
        <LoadingState />
      ) : players.isError ? (
        <ErrorState error={players.error} />
      ) : (players.data ?? []).length === 0 ? (
        <EmptyState title="Brak graczy" description="Nie znaleziono graczy dla tych filtrów." />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {(players.data ?? []).map((player) => (
            <PlayerCard key={player.id} player={player} />
          ))}
        </div>
      )}
    </div>
  );
}

function RoleChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'rounded-sm border px-3 py-1.5 text-xs font-medium transition',
        active
          ? 'border-[var(--gold)]/50 bg-bg-2 text-gold'
          : 'border-line text-text hover:text-text-hi',
      )}
    >
      {children}
    </button>
  );
}
