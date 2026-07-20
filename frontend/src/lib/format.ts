import type { Role, Side } from '../api/types';

/** Format an ISO-8601 instant into a short PL date-time. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('pl-PL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Format an ISO-8601 date (no time). */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('pl-PL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

/** Match duration in seconds → "mm:ss". */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0) return '—';
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/** KDA ratio: (K + A) / max(1, D). */
export function kdaRatio(kills: number, deaths: number, assists: number): number {
  return (kills + assists) / Math.max(1, deaths);
}

/** Win rate as a percentage (0–100). */
export function winRate(wins: number, games: number): number {
  if (games <= 0) return 0;
  return (wins / games) * 100;
}

/** Round to a fixed number of decimals and return as a string. */
export function fixed(value: number, digits = 1): string {
  return value.toFixed(digits);
}

const ROLE_LABELS: Record<Role, string> = {
  TOP: 'Top',
  JUNGLE: 'Jungle',
  MID: 'Mid',
  ADC: 'ADC',
  SUPPORT: 'Support',
};

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role];
}

export function sideLabel(side: Side): string {
  return side === 'BLUE' ? 'Niebiescy' : 'Czerwoni';
}
