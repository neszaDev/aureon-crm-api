CREATE TABLE contacts (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100),
  email VARCHAR(254),
  phone VARCHAR(50),
  job_title VARCHAR(100),
  company_id BIGINT,
  owner_id BIGINT,
  source_id BIGINT,
  -- Lower case, sorted, without duplicates: at most 20 of at most 40 characters (the API's rules).
  tags TEXT[] NOT NULL DEFAULT '{}',
  notes VARCHAR(5000),
  archived_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  -- Target of the composite (workspace_id, contact_id) foreign keys of deals, tasks, activities.
  UNIQUE (workspace_id, id),
  -- A contact can only link a company and a source of its own workspace.
  FOREIGN KEY (workspace_id, company_id) REFERENCES companies (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, source_id) REFERENCES lead_sources (workspace_id, id)
    ON DELETE RESTRICT,
  -- As companies: removing the member leaves their contacts unassigned.
  FOREIGN KEY (workspace_id, owner_id) REFERENCES workspace_members (workspace_id, user_id)
    ON DELETE SET NULL (owner_id)
);
-- The foreign keys (and the company, owner and source filters).
CREATE INDEX idx_contacts_company ON contacts(workspace_id, company_id);
CREATE INDEX idx_contacts_owner ON contacts(workspace_id, owner_id);
CREATE INDEX idx_contacts_source ON contacts(workspace_id, source_id);
-- The email duplicate check.
CREATE INDEX idx_contacts_email ON contacts(workspace_id, lower(email));
CREATE INDEX idx_contacts_tags ON contacts USING GIN (tags);
