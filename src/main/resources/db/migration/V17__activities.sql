CREATE TABLE activities (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  type VARCHAR(16) NOT NULL CHECK (type IN ('CALL', 'EMAIL', 'MEETING', 'NOTE')),
  subject VARCHAR(200) NOT NULL,
  body VARCHAR(5000),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  -- The member's user id, null once they left the workspace (as the owners and tasks.created_by).
  author_id BIGINT,
  company_id BIGINT,
  contact_id BIGINT,
  lead_id BIGINT,
  deal_id BIGINT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  -- An activity is about something: the service fills company_id from the contact or deal, this
  -- is the backstop for the links as a whole.
  CHECK (num_nonnulls(company_id, contact_id, lead_id, deal_id) >= 1),
  -- An activity can only link a company, contact, lead and deal of its own workspace.
  FOREIGN KEY (workspace_id, company_id) REFERENCES companies (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, contact_id) REFERENCES contacts (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, lead_id) REFERENCES leads (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, deal_id) REFERENCES deals (workspace_id, id) ON DELETE RESTRICT,
  -- As the owners of the other records: removing the member leaves the activity without an
  -- author. The column list keeps workspace_id, which is NOT NULL.
  FOREIGN KEY (workspace_id, author_id) REFERENCES workspace_members (workspace_id, user_id)
    ON DELETE SET NULL (author_id)
);
-- The newest-first list and the dashboard's recent activities.
CREATE INDEX idx_activities_occurred_at ON activities(workspace_id, occurred_at DESC);
-- The foreign keys (and the author and link filters, a record's timeline).
CREATE INDEX idx_activities_author ON activities(workspace_id, author_id);
CREATE INDEX idx_activities_company ON activities(workspace_id, company_id);
CREATE INDEX idx_activities_contact ON activities(workspace_id, contact_id);
CREATE INDEX idx_activities_lead ON activities(workspace_id, lead_id);
CREATE INDEX idx_activities_deal ON activities(workspace_id, deal_id);
