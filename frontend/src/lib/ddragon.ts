/**
 * Data Dragon image URL helpers (docs/07). The backend normally supplies full
 * CDN URLs on each DTO; these helpers reconstruct a splash URL from a champion
 * slug when only the slug is available (e.g. the MVP on a match summary), so
 * the hero can show splash art without an extra request.
 */
const DDRAGON_BASE = 'https://ddragon.leagueoflegends.com/cdn';

export function championSplashUrl(slug: string | null | undefined): string | null {
  if (!slug) return null;
  return `${DDRAGON_BASE}/img/champion/splash/${slug}_0.jpg`;
}
