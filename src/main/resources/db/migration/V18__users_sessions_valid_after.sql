-- Access tokens issued before this instant are refused (JwtAuthenticationFilter). Set on an
-- email, role or password change; null for a user who never had one.
ALTER TABLE users ADD COLUMN sessions_valid_after TIMESTAMP WITH TIME ZONE;
