/**
 * Map a Performance Rating (0–100) onto the --pr-* colour scale from docs/06.
 *   low   (grey)   → poniżej średniej
 *   mid   (blue)   → solidna gra (≈ średnia)
 *   high  (violet) → wyróżniająca się
 *   elite (gold)   → dominująca
 */
export type PrTier = 'low' | 'mid' | 'high' | 'elite';

export function prTier(pr: number): PrTier {
  if (pr >= 80) return 'elite';
  if (pr >= 65) return 'high';
  if (pr >= 45) return 'mid';
  return 'low';
}

const TIER_VAR: Record<PrTier, string> = {
  low: 'var(--pr-low)',
  mid: 'var(--pr-mid)',
  high: 'var(--pr-high)',
  elite: 'var(--pr-elite)',
};

/** CSS colour value for a given PR. */
export function prColor(pr: number): string {
  return TIER_VAR[prTier(pr)];
}
