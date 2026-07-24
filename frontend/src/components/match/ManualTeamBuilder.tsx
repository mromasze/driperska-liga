import { useState } from 'react';
import type { ManualSlot, Player, Role, Side } from '../../api/types';
import { Avatar } from '../ui/Avatar';
import { cn } from '../../lib/cn';
import { roleLabel } from '../../lib/format';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const SIDES: Side[] = ['BLUE', 'RED'];
const DND_TYPE = 'application/x-driperska-player';

interface ManualTeamBuilderProps {
  players: Player[];
  value: ManualSlot[];
  onChange: (slots: ManualSlot[]) => void;
}

/**
 * Drag (or click) players from the pool into two teams of five role slots. Dropping onto an
 * occupied slot swaps the previous occupant back to the pool; a player can sit in only one slot.
 */
export function ManualTeamBuilder({ players, value, onChange }: ManualTeamBuilderProps) {
  // Click-to-place fallback for touch / accessibility: the "picked up" player awaits a slot click.
  const [picked, setPicked] = useState<string | null>(null);

  const byId = new Map(players.map((p) => [p.id, p]));
  const slotFor = (side: Side, role: Role) =>
    value.find((s) => s.side === side && s.role === role)?.playerId ?? null;
  const assignedIds = new Set(value.map((s) => s.playerId));
  const pool = players.filter((p) => !assignedIds.has(p.id));

  const assign = (playerId: string, side: Side, role: Role) => {
    const next = value.filter(
      (s) => s.playerId !== playerId && !(s.side === side && s.role === role),
    );
    next.push({ playerId, side, role });
    onChange(next);
    setPicked(null);
  };

  const unassign = (playerId: string) => {
    onChange(value.filter((s) => s.playerId !== playerId));
    setPicked(null);
  };

  const onPoolClick = (playerId: string) =>
    setPicked((prev) => (prev === playerId ? null : playerId));

  const onSlotClick = (side: Side, role: Role) => {
    if (picked) assign(picked, side, role);
  };

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_1.4fr]">
      {/* Pool of unassigned players */}
      <div className="glass p-3">
        <div className="kicker mb-2 text-text-lo">
          Do przypisania · {pool.length}
        </div>
        {pool.length === 0 ? (
          <p className="py-6 text-center text-sm text-text-lo">Wszyscy gracze przypisani.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {pool.map((p) => (
              <button
                key={p.id}
                type="button"
                draggable
                onDragStart={(e) => {
                  e.dataTransfer.setData(DND_TYPE, p.id);
                  e.dataTransfer.effectAllowed = 'move';
                }}
                onClick={() => onPoolClick(p.id)}
                className={cn(
                  'flex cursor-grab items-center gap-3 rounded-lg border p-2 text-left transition-all active:cursor-grabbing',
                  picked === p.id
                    ? 'border-[color:var(--gold)]/60 bg-[color:var(--gold)]/10'
                    : 'border-line bg-[var(--glass)] hover:border-line-strong',
                )}
              >
                <Avatar src={p.avatarUrl} name={p.nickname} size={34} ring={picked === p.id} />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium text-text-hi">{p.nickname}</div>
                  <div className="kicker">{roleLabel(p.mainRole)}</div>
                </div>
              </button>
            ))}
          </div>
        )}
        {picked && (
          <p className="mt-2 text-center text-xs text-gold">
            Kliknij slot, aby przypisać wybranego gracza.
          </p>
        )}
      </div>

      {/* Two team columns */}
      <div className="grid gap-4 sm:grid-cols-2">
        {SIDES.map((side) => {
          const accent = side === 'BLUE' ? 'var(--blue)' : 'var(--red)';
          return (
            <div
              key={side}
              className="glass overflow-hidden"
              style={{ borderColor: `color-mix(in srgb, ${accent} 35%, transparent)` }}
            >
              <div
                className="flex items-center gap-2 px-3 py-2.5"
                style={{ background: `color-mix(in srgb, ${accent} 12%, transparent)` }}
              >
                <span className="h-2.5 w-2.5 rounded-full" style={{ background: accent }} />
                <span className="font-display font-semibold" style={{ color: accent }}>
                  {side === 'BLUE' ? 'Niebiescy' : 'Czerwoni'}
                </span>
              </div>
              <div className="divide-y divide-[color:var(--line)]">
                {ROLES.map((role) => {
                  const playerId = slotFor(side, role);
                  const player = playerId ? byId.get(playerId) : null;
                  return (
                    <div
                      key={role}
                      onDragOver={(e) => {
                        if (e.dataTransfer.types.includes(DND_TYPE)) {
                          e.preventDefault();
                          e.dataTransfer.dropEffect = 'move';
                        }
                      }}
                      onDrop={(e) => {
                        const id = e.dataTransfer.getData(DND_TYPE);
                        if (id) {
                          e.preventDefault();
                          assign(id, side, role);
                        }
                      }}
                      onClick={() => onSlotClick(side, role)}
                      className={cn(
                        'flex items-center gap-3 px-3 py-2.5 transition-colors',
                        picked && !player && 'cursor-pointer bg-[color:var(--gold)]/5',
                      )}
                    >
                      <span className="w-16 shrink-0 text-xs font-semibold uppercase tracking-wider text-text-lo">
                        {roleLabel(role)}
                      </span>
                      {player ? (
                        <div
                          draggable
                          onDragStart={(e) => {
                            e.dataTransfer.setData(DND_TYPE, player.id);
                            e.dataTransfer.effectAllowed = 'move';
                          }}
                          className="flex min-w-0 flex-1 cursor-grab items-center gap-2 active:cursor-grabbing"
                        >
                          <Avatar src={player.avatarUrl} name={player.nickname} size={30} />
                          <span className="min-w-0 flex-1 truncate text-sm font-medium text-text-hi">
                            {player.nickname}
                          </span>
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              unassign(player.id);
                            }}
                            aria-label={`Usuń ${player.nickname} ze slotu`}
                            className="rounded px-1.5 text-text-lo hover:text-loss"
                          >
                            ✕
                          </button>
                        </div>
                      ) : (
                        <span className="flex-1 text-sm text-text-lo/60">
                          Przeciągnij gracza tutaj
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
