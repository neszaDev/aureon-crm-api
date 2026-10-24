CREATE TABLE workspaces (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  default_currency CHAR(3) NOT NULL,
  timezone VARCHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT
);

CREATE TABLE workspace_members (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'SALES_REP')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  -- Also the target of the CRM tables' (workspace_id, owner_id) foreign keys.
  UNIQUE (workspace_id, user_id)
);
CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);
