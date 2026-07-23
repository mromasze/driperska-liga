export type Role = 'TOP' | 'JUNGLE' | 'MID' | 'ADC' | 'SUPPORT';
export type Side = 'BLUE' | 'RED';
export type AccountRole = 'ADMIN' | 'EDITOR' | 'PLAYER';
export type DrawMode = 'PURE_RANDOM' | 'BALANCED' | 'MANUAL';
export type DrawVoteDecision = 'ACCEPT' | 'REJECT';
export type SeasonStatus = 'UPCOMING' | 'ACTIVE' | 'ARCHIVED';
export type MatchStatus = 'DRAFT' | 'TEAMS_DRAWN' | 'DRAFT_READY' | 'DRAFTING' | 'DRAFTED' | 'LOBBY_READY' | 'LIVE' | 'RESULTS_SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type ApprovalDecision = 'PENDING' | 'APPROVED' | 'REJECTED';
export type MatchEventType = 'CREATED' | 'TEAMS_DRAWN' | 'DRAW_CONFIRMED' | 'RIOT_LOBBY_CREATED' | 'PLAYER_REPLACED' | 'MATCH_STARTED' | 'RIOT_CALLBACK_RECEIVED' | 'RIOT_RESULTS_IMPORTED' | 'RIOT_IMPORT_FAILED' | 'RESULTS_SUBMITTED' | 'RESULTS_EDITED' | 'APPROVED' | 'REJECTED' | 'REOPENED' | 'REPLAY_UPLOADED' | 'DISCORD_SHARED' | 'CANCELLED';

export interface HighlightVideo { id: string; url: string; sizeBytes: number; uploadedAt: string; }

export interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface ProblemDetail { type: string; title: string; status: number; detail?: string; instance?: string; errors?: { field: string; message: string }[]; }

export interface Account {
  id: string; username: string; email: string; role: AccountRole; enabled: boolean;
  createdAt: string; lastLoginAt: string | null;
}
export interface LoginRequest { username: string; password: string; turnstileToken?: string | null; }
export interface PublicConfig { turnstileEnabled: boolean; turnstileSiteKey: string | null; }
export interface ChangePasswordRequest { currentPassword: string; newPassword: string; }
export interface AuthTokens { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number; account: Account; }
export type RefreshResponse = AuthTokens;

export interface Champion {
  id: number; slug: string; name: string; title: string | null; tags: string[];
  iconUrl: string; splashUrl: string; loadingUrl: string;
}

export interface Player {
  id: string; nickname: string; realName: string | null; riotId: string | null;
  discordName: string;
  mainRole: Role; secondaryRole: Role | null; avatarUrl: string | null; bio: string | null;
  opggLink: string | null; favoriteChampionIds: number[]; accountProvisioned: boolean; active: boolean; joinedAt: string;
}
export interface CreatePlayerRequest {
  nickname: string; mainRole: Role; secondaryRole?: Role | null; realName?: string | null;
  riotId?: string | null; bio?: string | null; discordName: string;
}
export interface UpdatePlayerRequest {
  nickname?: string; mainRole?: Role; secondaryRole?: Role | null; realName?: string | null;
  riotId?: string | null; bio?: string | null; opggLink?: string | null;
  favoriteChampionIds?: number[]; discordName?: string | null; active?: boolean;
}
export interface SelfUpdatePlayerRequest {
  mainRole: Role; secondaryRole: Role | null; riotId: string | null; bio: string | null;
  opggLink: string | null; favoriteChampionIds: number[];
}
export interface LoginCredentials {
  login: string; temporaryPassword: string; loginUrl: string; messageTemplate: string;
}
export interface DiscordDelivery { sent: boolean; message: string; }
export interface CreatedPlayerResponse { player: Player; credentials: LoginCredentials; discordDelivery: DiscordDelivery; }
export interface PlayersQuery { active?: boolean; role?: Role; search?: string; page?: number; size?: number; }

export interface SeasonAggregate {
  totalLp: number; games: number; wins: number; losses: number; winRate: number;
  avgPerformanceRating: number; mmr: number; mvpCount: number; pentaCount: number;
}
export interface ChampionPoolEntry {
  championId: number; championName: string | null; iconUrl: string | null;
  games: number; wins: number; winRate: number; avgPerformanceRating: number;
}
export interface PlayerStats { playerId: string; seasonId: string | null; season: SeasonAggregate; championPool: ChampionPoolEntry[]; }
export interface PlayerMatchEntry {
  matchId: string; side: Side; role: Role; won: boolean; championId: number | null;
  championName: string | null; championIconUrl: string | null; kills: number; deaths: number;
  assists: number; kda: number; performanceRating: number | null; lpAwarded: number | null;
  mvp: boolean; completedAt: string | null;
}

export interface Season { id: string; name: string; startDate: string | null; endDate: string | null; status: SeasonStatus; }
export interface RankingRow {
  rank: number; playerId: string; nickname: string; avatarUrl: string | null; totalLp: number;
  games: number; wins: number; losses: number; winRate: number; avgPerformanceRating: number;
  mmr: number; mvpCount: number; pentaCount: number;
}

export interface LpComponent { label: string; points: number; }
export interface LpBreakdown { components: LpComponent[]; total: number; formula: string; }
export interface MatchParticipant {
  playerId: string; nickname: string; avatarUrl: string | null; side: Side; role: Role;
  championId: number | null; championName: string | null; championIconUrl: string | null;
  kills: number; deaths: number; assists: number; kda: number; cs: number; gold: number;
  damageToChampions: number; visionScore: number; largestMultiKill: number;
  performanceRating: number | null; lpAwarded: number | null; mvp: boolean;
  lpBreakdown: LpBreakdown | null;
}
export interface Approval {
  decision: ApprovalDecision; submittedBy: string | null; submittedAt: string | null;
  reviewedBy: string | null; reviewedAt: string | null; signatureConfirmed: boolean;
  signatureName: string | null; rejectionReason: string | null;
}
export interface MatchSummary {
  id: string; seasonId: string; status: MatchStatus; winningSide: Side | null;
  durationSeconds: number | null; createdAt: string; completedAt: string | null; participantCount: number;
}
export interface RiotMatchInfo {
  tournamentCode: string | null; gameId: string | null; matchId: string | null;
  lobbyCreatedAt: string | null; callbackReceivedAt: string | null;
  resultsImportedAt: string | null; importError: string | null;
}
export interface MatchDetail {
  id: string; seasonId: string; status: MatchStatus; drawMode: DrawMode; winningSide: Side | null;
  durationSeconds: number | null; patch: string | null; createdAt: string; startedAt: string | null;
  completedAt: string | null; participants: MatchParticipant[]; approval: Approval | null; riot: RiotMatchInfo;
  replayUrl: string | null;
}
export interface MatchEvent { type: MatchEventType; actorAccountId: string | null; payloadJson: string | null; createdAt: string; }
export interface MatchesQuery { status?: MatchStatus; seasonId?: string; page?: number; size?: number; }

export interface CreateMatchRequest { seasonId: string; drawMode: DrawMode; playerIds: string[]; }
export interface DrawSlot { playerId: string; nickname: string; role: Role; mmr: number; }
export interface ReplacePlayerRequest { removedPlayerId: string; addedPlayerId: string; }

export interface OcrRow {
  playerId: string; nickname: string; role: Role | null; championId: number | null; championName: string | null;
  kills: number; deaths: number; assists: number; cs: number; gold: number;
  damageToChampions: number; visionScore: number; largestMultiKill: number;
}
export interface OcrLogEntry { stage: string; message: string; }
export interface OcrDraft {
  winningSide: Side | null; durationSeconds: number | null;
  rows: OcrRow[]; unmatched: string[]; missing: string[]; logs: OcrLogEntry[];
}

export interface FeedbackComment { tone: 'POSITIVE' | 'NEGATIVE'; note: string; }
export interface PlayerFeedbackSummary {
  playerId: string; nickname: string; side: Side | null; role: Role | null;
  upvotes: number; downvotes: number; comments: FeedbackComment[];
}
export interface MatchFeedbackSummary { responses: number; players: PlayerFeedbackSummary[]; }

export interface FeedbackParticipant { playerId: string; nickname: string; side: Side; role: Role; }
export interface MyFeedback { upvotePlayerId: string | null; downvotePlayerId: string | null; note: string | null; }
export interface RateableMatch {
  matchId: string; completedAt: string | null; participants: FeedbackParticipant[]; myFeedback: MyFeedback | null;
}

export type RsvpResponse = 'YES' | 'NO' | 'MAYBE';
export interface RsvpEntry { playerId: string; nickname: string; response: RsvpResponse; }
export interface PlannedMatch {
  id: string; scheduledAt: string; note: string | null; status: string; createdAt: string;
  yes: number; no: number; maybe: number; myResponse: RsvpResponse | null; responses: RsvpEntry[];
}
export interface CreatePlannedMatchResult { planned: PlannedMatch; announced: boolean; announceMessage: string; }
export interface ServiceHealth { ok: boolean; configured: boolean; message: string; }
export interface DrawBalance { blueMmrAvg: number; redMmrAvg: number; predictedBlueWinPct: number; }
export interface DrawResult { matchId: string; drawMode: DrawMode; blue: DrawSlot[]; red: DrawSlot[]; balance: DrawBalance; }
export interface ResultParticipantInput {
  playerId: string; role: Role; championId: number; kills: number; deaths: number; assists: number;
  cs: number; gold: number; damageToChampions: number; visionScore: number; largestMultiKill: number;
}
export interface SubmitResultsRequest { winningSide: Side; durationSeconds: number; patch: string; participants: ResultParticipantInput[]; }
export interface ApproveRequest { signatureConfirmed: boolean; signatureName: string; }
export interface RejectRequest { reason: string; }

export interface LobbyPlayer {
  playerId: string; nickname: string; avatarUrl: string | null; role: Role; side: Side;
  championId: number | null; captain: boolean;
}
export type DraftStepType = 'BAN' | 'PICK';
export type SwapType = 'POSITION' | 'CHAMPION';
export interface DraftStepView { side: Side; type: DraftStepType; }
export interface DraftSwapView { id: string; fromPlayerId: string; toPlayerId: string; type: SwapType; }
export interface DraftView {
  status: 'DRAFTING' | 'DONE';
  currentIndex: number;
  deadline: string | null;
  currentSide: Side | null;
  currentType: DraftStepType | null;
  blueCaptain: string | null; redCaptain: string | null;
  currentPlayerId: string | null;
  paused: boolean;
  blueOrder: string[]; redOrder: string[];
  blueBans: number[]; redBans: number[];
  sequence: DraftStepView[];
  swaps: DraftSwapView[];
}
export interface DrawLobby {
  matchId: string; status: MatchStatus; round: number; requiredAccepts: number;
  accepts: number; rejects: number; acceptedPlayerIds: string[]; rejectedPlayerIds: string[];
  blue: LobbyPlayer[]; red: LobbyPlayer[]; updatedAt: string;
  tournamentCode: string | null; riotImportError: string | null;
  voteDeadline: string | null; draft: DraftView | null;
}
export interface RiotLobbyMember {
  playerId: string; nickname: string; puuid: string; joined: boolean;
}
export interface RiotLobbyStatus {
  joinedCount: number; expectedCount: number; gameStarted: boolean; members: RiotLobbyMember[];
  events: { timestamp: string; eventType: string; puuid?: string | null }[];
}
