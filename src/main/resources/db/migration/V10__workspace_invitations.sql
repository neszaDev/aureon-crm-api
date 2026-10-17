CREATE TABLE workspace_invitations (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  -- Lower case; compared exactly with users.email on accept.
  email VARCHAR(254) NOT NULL,
  role VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'SALES_REP')),
  -- SHA-256 hex of the link's token; the token itself is never stored.
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  invited_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  accepted_at TIMESTAMP WITH TIME ZONE,
  revoked_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT
);
-- One open invitation per email and workspace (an expired one is revoked before a new one).
CREATE UNIQUE INDEX uq_workspace_invitations_pending
  ON workspace_invitations(workspace_id, email)
  WHERE accepted_at IS NULL AND revoked_at IS NULL;
