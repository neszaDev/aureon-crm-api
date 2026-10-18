-- Refresh-token families (RefreshTokenService): a sign-in starts a family; a rotation adds the new
-- token to it and marks the old one used instead of deleting it, so a replay can be detected.
ALTER TABLE refresh_tokens
  ADD COLUMN family_id UUID,
  ADD COLUMN family_created_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN used_at TIMESTAMP WITH TIME ZONE;
-- Tokens from before families: each is its own family, started when the token was issued.
UPDATE refresh_tokens SET family_id = gen_random_uuid(), family_created_at = created_at;
ALTER TABLE refresh_tokens
  ALTER COLUMN family_id SET NOT NULL,
  ALTER COLUMN family_created_at SET NOT NULL;
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
