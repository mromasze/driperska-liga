-- v0.2.5 — planned (tentative) matches with attendance RSVPs.
CREATE TABLE IF NOT EXISTS planned_match (
    id uuid PRIMARY KEY,
    scheduled_at timestamptz NOT NULL,
    note text,
    status varchar(16) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS planned_match_rsvp (
    planned_match_id uuid NOT NULL,
    player_id uuid NOT NULL,
    response varchar(8) NOT NULL,
    responded_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_planned_rsvp
    ON planned_match_rsvp (planned_match_id, player_id);
CREATE INDEX IF NOT EXISTS ix_planned_match_status ON planned_match (status, scheduled_at);
