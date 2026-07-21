-- v0.2.1 — store the League replay (.rofl) location per match.
-- Added as a separate migration because V1 is already applied on the live database.
ALTER TABLE match_game ADD COLUMN IF NOT EXISTS replay_url varchar(500);
