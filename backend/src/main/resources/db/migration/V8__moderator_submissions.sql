-- v0.5.0: moderators. A moderator is an ordinary PLAYER account with one extra permission — it may
-- record a past match (roster + statistics) and send it to the admin approval queue. The permission
-- is a flag rather than an AccountRole because such a person stays a player: they keep the draft,
-- their profile and the rating tab, and the submission panel is added on top of that.
ALTER TABLE account ADD COLUMN IF NOT EXISTS moderator boolean NOT NULL DEFAULT false;

-- "My submissions" lists a moderator's own matches, and ownership of a submission is decided by
-- match_game.created_by, which had no index.
CREATE INDEX IF NOT EXISTS ix_match_created_by ON match_game (created_by);
