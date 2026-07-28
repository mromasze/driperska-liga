/**
 * Group names as the backend spells them (see `RuntimeConfigRegistry`). They arrive as plain
 * strings on the wire, so the two sides have to agree literally — keep this file in sync.
 */
export const RuntimeConfigGroups = {
  AI: 'AI (Ollama)',
  RIOT: 'Riot API',
  DISCORD: 'Discord',
  TURNSTILE: 'Cloudflare Turnstile',
  GAMEPLAY: 'Draft i losowanie',
  APP: 'Aplikacja',
  STARTUP: 'Tylko .env (wymaga restartu)',
} as const;
