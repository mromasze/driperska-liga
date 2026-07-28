import { useState } from 'react';
import { usePlayers } from '../api/hooks/players';
import { PlayerCard } from '../components/player/PlayerCard';
import { CardGridSkeleton, EmptyState, ErrorState } from '../components/ui/States';
import { cn } from '../lib/cn';
import type { Role } from '../api/types';

const ROLES: { value: Role | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Wszyscy' },
  { value: 'TOP', label: 'Top' },
  { value: 'JUNGLE', label: 'Jungle' },
  { value: 'MID', label: 'Mid' },
  { value: 'ADC', label: 'ADC' },
  { value: 'SUPPORT', label: 'Support' },
];

export function PlayersPage() {
  const [role, setRole] = useState<Role | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const players = usePlayers({
    role: role === 'ALL' ? undefined : role,
    search: search.trim() || undefined,
  });

  const list = players.data?.content ?? [];

  return (
    <div className="space-y-6">
      <div>
        <div className="kicker mb-1 text-gold">Skład ligi</div>
        <h1 className="font-display text-3xl">Gracze</h1>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Szukaj po nicku…"
          className="h-10 flex-1 rounded-md border border-line bg-bg-1 px-3 text-sm text-text-hi placeholder:text-text-lo focus:border-line-strong"
        />
        <div className="flex flex-wrap gap-1">
          {ROLES.map((r) => (
            <button
              key={r.value}
              onClick={() => setRole(r.value)}
              className={cn(
                'rounded-md px-3 py-2 text-sm font-medium transition-colors',
                role === r.value
                  ? 'bg-[var(--glass-strong)] text-text-hi'
                  : 'text-text-lo hover:text-text',
              )}
            >
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {players.isLoading ? (
        <CardGridSkeleton count={6} />
      ) : players.isError ? (
        <ErrorState error={players.error} />
      ) : list.length === 0 ? (
        <EmptyState title="Brak graczy" description="Dodaj graczy w panelu administracyjnym." />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((p) => (
            <PlayerCard key={p.id} player={p} />
          ))}
        </div>
      )}
    </div>
  );
}
