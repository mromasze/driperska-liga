import type { MatchesQuery, PlayersQuery } from './types';

export const queryKeys = {
  me: ['me'] as const,
  myPlayer: ['player', 'me'] as const,
  players: (query?: PlayersQuery) => ['players', query ?? {}] as const,
  player: (id: string) => ['player', id] as const,
  playerStats: (id: string, season?: string) => ['player', id, 'stats', season ?? null] as const,
  playerMatches: (id: string, page?: number) => ['player', id, 'matches', page ?? 0] as const,
  ranking: (season?: string) => ['ranking', season ?? null] as const,
  seasons: ['seasons'] as const,
  currentSeason: ['season', 'current'] as const,
  seasonRanking: (id: string) => ['season', id, 'ranking'] as const,
  matches: (query?: MatchesQuery) => ['matches', query ?? {}] as const,
  match: (id: string) => ['match', id] as const,
  matchEvents: (id: string) => ['match', id, 'events'] as const,
  matchMaintenance: ['admin', 'matches', 'maintenance'] as const,
  champions: ['champions'] as const,
};