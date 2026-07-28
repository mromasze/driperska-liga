-- v0.4.5: the admin panel can now override any .env value at runtime (API keys, AI models, channel
-- IDs, timers). Overrides are stored as ordinary app_setting rows and replayed onto the
-- configuration beans on boot, so 512 chars is no longer a safe ceiling for a value.
ALTER TABLE app_setting ALTER COLUMN setting_value TYPE VARCHAR(2048);
