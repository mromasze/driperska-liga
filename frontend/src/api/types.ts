/**
 * API DTO types — hand-written to match docs/05-rest-api.md and docs/02-domain.
 *
 * NOTE: these are a stopgap. Per docs/05 §5.7 and docs/08 §8.7, CI will later
 * generate `api-types.ts` from the springdoc OpenAPI schema
 * (`openapi-typescript`), and these hand-written shapes will be replaced so a
 * contract drift is caught at build time rather than at runtime.
 */

// ---------------------------------------------------------------------------
// Enums (docs/02 §2.3)
// ---------------------------------------------------------------------------
export type Role = 'TOP' | 'JUNGLE' | 'MID' | 'ADC' | 'SUPPORT';
export type Side = 'BLUE' | 'RED';
export type AccountRole = 'ADMIN' | 'EDITOR';
export type DrawMode = 'PURE_RANDOM' | 'BALANCED' | 'MANUAL';
export type SeasonStatus = 'UPCOMING' | 'ACTIVE' | 'ARCHIVED';
export type MatchStatus =
  | 'DRAFT'
  | 'TEAMS_DRAWN'
  | 'LIVE'
  | 'RESULTS_SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';
export type ApprovalDecision = 'PENDING' | 'APPROVED' | 'REJECTED';
export type MatchEventType =
  | 'CREATED'
  | 'TEAMS_DRAWN'
  | 'DRAW_CONFIRMED'
  | 'RESULTS_SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

// ---------------------------------------------------------------------------
// Envelopes & errors (docs/05 §5.2)
// ---------------------------------------------------------------------------
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: { field: string; message: string }[];
  traceId?: string;
}

// ---------------------------------------------------------------------------
// Auth (docs/05 §5.3 /auth)
// ---------------------------------------------------------------------------
export interface Account {
  id: string;
  username: string;
  email: string;
  role: AccountRole;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  account: Account;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}

// ---------------------------------------------------------------------------
// Champions (docs/02 §2.2, docs/07)
// ---------------------------------------------------------------------------
export interface Champion {
  id: number; // Riot numeric key (e.g. 266 = Aatrox)
  slug: string; // Riot id (e.g. "Aatrox")
  name: string;
  title: string;
  tags: string[];
  ddragonVersion: string;
  iconUrl: string;
  splashUrl: string;
  loadingUrl: string;
}

// ---------------------------------------------------------------------------
// Players (docs/02 §2.2)
// ---------------------------------------------------------------------------
export interface Player {
  id: string;
  nickname: string;
  realName: string | null;
  riotId: string | null;
  mainRole: Role;
  secondaryRole: Role | null;
  avatarUrl: string | null;
  bio: string | null;
  active: boolean;
  joinedAt: string;
  // Convenience aggregates the list endpoint may include for card display.
  lp?: number;
  mmr?: number;
}

export interface ChampionPoolEntry {
  championId: number;
  championName: string;
  championSlug: string;
  iconUrl: string;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0–100
  avgPerformanceRating: number; // 0–100
}

export interface PrHistoryPoint {
  matchId: string;
  playedAt: string;
  performanceRating: number;
}

/** Aggregated season (or all-time) stats — GET /players/{id}/stats */
export interface PlayerStats {
  playerId: string;
  seasonId: string | null;
  totalLp: number;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0–100
  avgPerformanceRating: number; // 0–100
  mmr: number;
  mvpCount: number;
  pentaCount: number;
  championPool: ChampionPoolEntry[];
  prHistory: PrHistoryPoint[];
}

export interface CreatePlayerRequest {
  nickname: string;
  realName?: string | null;
  riotId?: string | null;
  mainRole: Role;
  secondaryRole?: Role | null;
  bio?: string | null;
}

export interface PlayersQuery {
  active?: boolean;
  role?: Role;
  search?: string;
}

// ---------------------------------------------------------------------------
// Seasons (docs/02 §2.2, docs/05)
// ---------------------------------------------------------------------------
export interface Season {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  status: SeasonStatus;
}

// ---------------------------------------------------------------------------
// Ranking (docs/04 §4.7, docs/05)
// ---------------------------------------------------------------------------
export interface RankingRow {
  rank: number;
  player: Player;
  totalLp: number;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0–100
  avgPerformanceRating: number; // 0–100
  mvpCount: number;
  mmr: number | null;
  /** Recent-form sparkline: PR of last N matches (oldest → newest). */
  form: number[];
}

// ---------------------------------------------------------------------------
// Matches (docs/02 §2.2, docs/03, docs/05)
// ---------------------------------------------------------------------------
export interface MatchParticipant {
  id: string;
  playerId: string;
  nickname: string;
  avatarUrl: string | null;
  side: Side;
  role: Role;
  championId: number;
  championName: string;
  championSlug: string;
  championIconUrl: string;
  kills: number;
  deaths: number;
  assists: number;
  cs: number;
  gold: number;
  damageToChampions: number;
  visionScore: number;
  largestMultiKill: number;
  // Computed by the points engine (present once APPROVED).
  kda: number | null;
  performanceRating: number | null;
  lpAwarded: number | null;
  mmrDelta: number | null;
  isMvp: boolean;
}

/** List item — GET /matches */
export interface MatchSummary {
  id: string;
  seasonId: string;
  status: MatchStatus;
  drawMode: DrawMode;
  winningSide: Side | null;
  durationSeconds: number | null;
  patch: string | null;
  createdAt: string;
  completedAt: string | null;
  blueScore: number; // team kills, for the card headline
  redScore: number;
  mvp: {
    playerId: string;
    nickname: string;
    championSlug: string;
    championIconUrl: string;
    performanceRating: number;
  } | null;
}

/** Full detail — GET /matches/{id} */
export interface MatchDetail extends MatchSummary {
  notes: string | null;
  startedAt: string | null;
  balance: DrawBalance | null;
  participants: MatchParticipant[];
}

export interface MatchEvent {
  id: string;
  matchId: string;
  type: MatchEventType;
  actorAccountId: string;
  actorUsername: string;
  createdAt: string;
}

export interface MatchesQuery {
  status?: MatchStatus;
  season?: string;
  page?: number;
  size?: number;
}

// --- Match lifecycle request/response shapes (docs/05 §5.4) ---
export interface CreateMatchRequest {
  seasonId: string;
  drawMode: DrawMode;
  playerIds: string[];
}

export interface DrawPlayer {
  playerId: string;
  nickname: string;
  role: Role;
  mmr: number;
}

export interface DrawBalance {
  blueMmrAvg: number;
  redMmrAvg: number;
  predictedBlueWinPct: number; // 0–100
}

/** Response of POST /matches/{id}/draw */
export interface DrawResult {
  matchId: string;
  drawMode: DrawMode;
  blue: DrawPlayer[];
  red: DrawPlayer[];
  balance: DrawBalance;
}

export interface ResultParticipantInput {
  playerId: string;
  side: Side;
  role: Role;
  championId: number;
  kills: number;
  deaths: number;
  assists: number;
  cs: number;
  gold: number;
  damageToChampions: number;
  visionScore: number;
  largestMultiKill: number;
}

export interface SubmitResultsRequest {
  winningSide: Side;
  durationSeconds: number;
  patch: string;
  participants: ResultParticipantInput[];
}

export interface ApproveRequest {
  signatureConfirmed: boolean;
  signatureName: string;
}

export interface RejectRequest {
  reason: string;
}
