-- Ranking v2 persists the ACE distinction independently from MVP.
ALTER TABLE match_participant
    ADD COLUMN is_ace BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE player_season_stats
    ADD COLUMN ace_count INTEGER NOT NULL DEFAULT 0;
