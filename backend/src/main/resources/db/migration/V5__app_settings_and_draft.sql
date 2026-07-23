-- v0.3: runtime admin settings (e.g. Riot API on/off) + internal tournament draft state.

-- Key/value store for admin-editable runtime settings. Kept generic on purpose.
CREATE TABLE app_setting (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(512) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One draft per match. The whole pick/ban/swap state is serialised as JSON in `state`
-- (portable across Postgres/H2); `deadline` is duplicated as a column so the timeout
-- scheduler can find due steps without parsing JSON.
CREATE TABLE match_draft (
    match_id UUID PRIMARY KEY REFERENCES match_game(id) ON DELETE CASCADE,
    state TEXT NOT NULL,
    deadline TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
