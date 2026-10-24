CREATE TABLE companies (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  name VARCHAR(200) NOT NULL,
  industry VARCHAR(100),
  website VARCHAR(255),
  phone VARCHAR(50),
  address VARCHAR(500),
  employee_count INT,
  owner_id BIGINT,
  notes VARCHAR(5000),
  archived_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  -- Target of the composite (workspace_id, company_id) foreign keys of the other records.
  UNIQUE (workspace_id, id),
  -- Only a member of the workspace owns a company; removing the member (or deleting the account)
  -- leaves it unassigned. The column list keeps workspace_id, which is NOT NULL.
  FOREIGN KEY (workspace_id, owner_id) REFERENCES workspace_members (workspace_id, user_id)
    ON DELETE SET NULL (owner_id)
);
CREATE INDEX idx_companies_name ON companies(workspace_id, lower(name));
-- The owner filter, and the SET NULL when a member is removed.
CREATE INDEX idx_companies_owner ON companies(workspace_id, owner_id);
