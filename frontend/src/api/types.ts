/**
 * API DTO types — mirror the backend responses exactly (pl.romcio.driperska.*.api).
 * Hand-written for now; will later be generated from the springdoc OpenAPI schema.
 */

// ---- Enums --------------------------------------------------------------
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
  | 'RESULTS_EDITED'
  | 'APPROVED'
  | 'REJECTED'
  | 'REOPENED'
  | 'CANCELLED';

// ---- Envelopes & errors -------------------------------------------------
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
}

// ---- Auth ---------------------------------------------------------------
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

/** POST /auth/login and /auth/refresh both return this. */
export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  account: Account;
}

export type RefreshResponse = AuthTokens;

// ---- Champions ----------------------------------------------------------
export interface Champion {
  id: number;
  slug: string;
  name: string;
  title: string | null;
  tags: string[];
  iconUrl: string;
  splashUrl: string;
  loadingUrl: string;
}

// ---- Players ------------------------------------------------------------
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
}

export interface CreatePlayerRequest {
  nickname: string;
  mainRole: Role;
  secondaryRole?: Role | null;
  realName?: string | null;
  riotId?: string | null;
  bio?: string | null;
}

export interface UpdatePlayerRequest {
  nickname?: string;
  mainRole?: Role;
  secondaryRole?: Role | null;
  realName?: string | null;
  riotId?: string | null;
  bio?: string | null;
  active?: boolean;
}

export interface PlayersQuery {
  active?: boolean;
  role?: Role;
  search?: string;
  page?: number;
  size?: number;
}

/** GET /players/{id}/stats */
export interface SeasonAggregate {
  totalLp: number;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0–1
  avgPerformanceRating: number; // 0–100
  mmr: number;
  mvpCount: number;
  pentaCount: number;
}

export interface ChampionPoolEntry {
  championId: number;
  championName: string | null;
  iconUrl: string | null;
  games: number;
  wins: number;
  winRate: number; // 0–1
  avgPerformanceRating: number; // 0–100
}

export interface PlayerStats {
  playerId: string;
  seasonId: string | null;
  season: SeasonAggregate;
  championPool: ChampionPoolEntry[];
}

/** GET /players/{id}/matches */
export interface PlayerMatchEntry {
  matchId: string;
  side: Side;
  role: Role;
  won: boolean;
  championId: number | null;
  championName: string | null;
  championIconUrl: string | null;
  kills: number;
  deaths: number;
  assists: number;
  kda: number;
  performanceRating: number | null;
  lpAwarded: number | null;
  mvp: boolean;
  completedAt: string | null;
}

// ---- Seasons ------------------------------------------------------------
export interface Season {
  id: string;
  name: string;
  startDate: string | null;
  endDate: string | null;
  status: SeasonStatus;
}

// ---- Ranking ------------------------------------------------------------
export interface RankingRow {
  rank: number;
  playerId: string;
  nickname: string;
  avatarUrl: string | null;
  totalLp: number;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0–1
  avgPerformanceRating: number; // 0–100
  mmr: number;
  mvpCount: number;
  pentaCount: number;
}

// ---- Matches ------------------------------------------------------------
export interface MatchParticipant {
  playerId: string;
  nickname: string;
  avatarUrl: string | null;
  side: Side;
  role: Role;
  championId: number | null;
  championName: string | null;
  championIconUrl: string | null;
  kills: number;
  deaths: number;
  assists: number;
  kda: number;
  cs: number;
  gold: number;
  damageToChampions: number;
  visionScore: number;
  largestMultiKill: number;
  performanceRating: number | null;
  lpAwarded: number | null;
  mvp: boolean;
}

export interface Approval {
  decision: ApprovalDecision;
  submittedBy: string | null;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  signatureConfirmed: boolean;
  signatureName: string | null;
  rejectionReason: string | null;
}

/** GET /matches list item. */
export interface MatchSummary {
  id: string;
  seasonId: string;
  status: MatchStatus;
  winningSide: Side | null;
  durationSeconds: number | null;
  createdAt: string;
  completedAt: string | null;
  participantCount: number;
}

/** GET /matches/{id}. */
export interface MatchDetail {
  id: string;
  seasonId: string;
  status: MatchStatus;
  drawMode: DrawMode;
  winningSide: Side | null;
  durationSeconds: number | null;
  patch: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  participants: MatchParticipant[];
  approval: Approval | null;
}

export interface MatchEvent {
  type: MatchEventType;
  actorAccountId: string | null;
  payloadJson: string | null;
  createdAt: string;
}

export interface MatchesQuery {
  status?: MatchStatus;
  seasonId?: string;
  page?: number;
  size?: number;
}

// ---- Match lifecycle requests ------------------------------------------
export interface CreateMatchRequest {
  seasonId: string;
  drawMode: DrawMode;
  playerIds: string[];
}

export interface DrawSlot {
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

export interface DrawResult {
  matchId: string;
  drawMode: DrawMode;
  blue: DrawSlot[];
  red: DrawSlot[];
  balance: DrawBalance;
}

/** One row of the results form — the backend derives `side` from the draw. */
export interface ResultParticipantInput {
  playerId: string;
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
