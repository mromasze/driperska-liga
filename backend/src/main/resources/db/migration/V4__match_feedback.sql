-- v0.2.6 — optional post-match peer ratings (one upvote + one downvote + note per voter).
CREATE TABLE IF NOT EXISTS match_feedback (
    id uuid PRIMARY KEY,
    match_id uuid NOT NULL,
    voter_player_id uuid NOT NULL,
    upvote_player_id uuid,
    downvote_player_id uuid,
    note text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_match_feedback_voter
    ON match_feedback (match_id, voter_player_id);
