-- Driperska Liga v0.2 (merged baseline; the database is intentionally empty pre-release)
-- Idempotent baseline: safe both for a fresh database and for the existing
-- Hibernate-managed schema. Flyway baselines an existing schema at version 0,
-- then applies this migration without dropping or rewriting user data.

CREATE TABLE IF NOT EXISTS account (
    id uuid PRIMARY KEY,
    username varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    role varchar(32) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    last_login_at timestamptz
);

CREATE TABLE IF NOT EXISTS player (
    id uuid PRIMARY KEY,
    nickname varchar(255) NOT NULL,
    real_name varchar(255),
    riot_id varchar(255),
    riot_puuid varchar(128),
    riot_summoner_id varchar(128),
    discord_name varchar(80) NOT NULL,
    discord_user_id varchar(32),
    main_role varchar(32),
    secondary_role varchar(32),
    avatar_url varchar(255),
    bio text,
    opgg_link varchar(500),
    account_id uuid,
    active boolean NOT NULL DEFAULT true,
    joined_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS champion (
    id integer PRIMARY KEY,
    slug varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    title varchar(255),
    tags varchar(200),
    ddragon_version varchar(255),
    icon_url varchar(500),
    splash_url varchar(500),
    loading_url varchar(500)
);

CREATE TABLE IF NOT EXISTS season (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    start_date date,
    end_date date,
    status varchar(32) NOT NULL,
    scoring_config_json text
);

CREATE TABLE IF NOT EXISTS match_game (
    id uuid PRIMARY KEY,
    season_id uuid NOT NULL,
    status varchar(32) NOT NULL,
    draw_mode varchar(32) NOT NULL,
    draw_round integer NOT NULL DEFAULT 0,
    winning_side varchar(16),
    duration_seconds integer,
    patch varchar(255),
    notes text,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    teams_drawn_at timestamptz,
    riot_tournament_code varchar(255),
    riot_game_id varchar(128),
    riot_match_id varchar(128),
    riot_metadata_token varchar(64),
    riot_lobby_created_at timestamptz,
    riot_callback_received_at timestamptz,
    riot_results_imported_at timestamptz,
    riot_import_error text
);

CREATE TABLE IF NOT EXISTS riot_tournament_registration (
    id uuid PRIMARY KEY,
    key_fingerprint varchar(64) NOT NULL,
    platform varchar(16) NOT NULL,
    callback_url varchar(500) NOT NULL,
    provider_id bigint NOT NULL,
    tournament_id bigint NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS match_pool (
    match_id uuid NOT NULL,
    player_id uuid
);

CREATE TABLE IF NOT EXISTS match_participant (
    id uuid PRIMARY KEY,
    match_id uuid NOT NULL,
    player_id uuid NOT NULL,
    side varchar(16) NOT NULL,
    role varchar(32) NOT NULL,
    champion_id integer,
    kills integer NOT NULL DEFAULT 0,
    deaths integer NOT NULL DEFAULT 0,
    assists integer NOT NULL DEFAULT 0,
    cs integer NOT NULL DEFAULT 0,
    gold integer NOT NULL DEFAULT 0,
    damage_to_champions integer DEFAULT 0,
    vision_score integer DEFAULT 0,
    largest_multi_kill integer DEFAULT 0,
    performance_rating double precision,
    lp_awarded integer,
    mmr_delta double precision,
    is_mvp boolean DEFAULT false
);

CREATE TABLE IF NOT EXISTS match_approval (
    id uuid PRIMARY KEY,
    match_id uuid NOT NULL,
    submitted_by uuid NOT NULL,
    submitted_at timestamptz NOT NULL,
    decision varchar(32) NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamptz,
    signature_confirmed boolean NOT NULL DEFAULT false,
    signature_name varchar(255),
    rejection_reason text
);

CREATE TABLE IF NOT EXISTS match_event (
    id uuid PRIMARY KEY,
    match_id uuid NOT NULL,
    type varchar(64) NOT NULL,
    actor_account_id uuid,
    payload_json text,
    created_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS player_season_stats (
    id uuid PRIMARY KEY,
    player_id uuid NOT NULL,
    season_id uuid NOT NULL,
    total_lp integer NOT NULL DEFAULT 0,
    games integer NOT NULL DEFAULT 0,
    wins integer NOT NULL DEFAULT 0,
    losses integer NOT NULL DEFAULT 0,
    sum_pr double precision NOT NULL DEFAULT 0,
    mmr double precision NOT NULL DEFAULT 0,
    mvp_count integer NOT NULL DEFAULT 0,
    penta_count integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL
);

ALTER TABLE player ADD COLUMN IF NOT EXISTS opgg_link varchar(500);
ALTER TABLE match_game ADD COLUMN IF NOT EXISTS draw_round integer NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS player_favorite_champion (
    player_id uuid NOT NULL,
    champion_id integer NOT NULL
);

CREATE TABLE IF NOT EXISTS match_draw_vote (
    id uuid PRIMARY KEY,
    match_id uuid NOT NULL,
    draw_round integer NOT NULL,
    player_id uuid NOT NULL,
    account_id uuid NOT NULL,
    decision varchar(16) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_account_username ON account (username);
CREATE UNIQUE INDEX IF NOT EXISTS ux_account_email ON account (email);
CREATE UNIQUE INDEX IF NOT EXISTS ux_player_nickname ON player (nickname);
CREATE UNIQUE INDEX IF NOT EXISTS ux_player_account ON player (account_id) WHERE account_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_player_discord_user ON player (discord_user_id) WHERE discord_user_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_match_riot_code ON match_game (riot_tournament_code) WHERE riot_tournament_code IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_riot_registration_key
    ON riot_tournament_registration (key_fingerprint, platform, callback_url);
CREATE UNIQUE INDEX IF NOT EXISTS ux_match_approval_match ON match_approval (match_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_player_season ON player_season_stats (player_id, season_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_draw_vote_round_player
    ON match_draw_vote (match_id, draw_round, player_id);

CREATE INDEX IF NOT EXISTS ix_match_status ON match_game (status);
CREATE INDEX IF NOT EXISTS ix_match_season_status ON match_game (season_id, status);
CREATE INDEX IF NOT EXISTS ix_match_pool_player ON match_pool (player_id);
CREATE INDEX IF NOT EXISTS ix_participant_match ON match_participant (match_id);
CREATE INDEX IF NOT EXISTS ix_participant_player ON match_participant (player_id);
CREATE INDEX IF NOT EXISTS ix_match_event_match ON match_event (match_id, created_at);
CREATE INDEX IF NOT EXISTS ix_stats_ranking ON player_season_stats (season_id, total_lp DESC);
CREATE INDEX IF NOT EXISTS ix_draw_vote_match_round ON match_draw_vote (match_id, draw_round);