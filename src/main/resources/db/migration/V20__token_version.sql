-- A per-user token version replaces sessions_valid_after (V18): an email, role or password
-- change increments users.token_version; access tokens carry it (the ver claim) and refresh
-- tokens are stamped with it, and a token of another version is refused. Unlike a time
-- comparison it has no same-second gap, and unlike deleting rows it also catches a refresh token
-- that a rotation in flight inserted while the change ran.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
ALTER TABLE refresh_tokens ADD COLUMN token_version INT NOT NULL DEFAULT 0;
ALTER TABLE users DROP COLUMN sessions_valid_after;
