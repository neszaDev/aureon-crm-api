CREATE TABLE deals (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  name VARCHAR(200) NOT NULL,
  amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
  currency CHAR(3) NOT NULL,
  stage_id BIGINT NOT NULL,
  -- The deal's own probability; null means its stage's.
  probability SMALLINT CHECK (probability BETWEEN 0 AND 100),
  expected_close_date DATE,
  owner_id BIGINT,
  company_id BIGINT,
  contact_id BIGINT,
  source_id BIGINT,
  lead_id BIGINT,
  lost_reason VARCHAR(500),
  closed_at TIMESTAMP WITH TIME ZONE,
  notes VARCHAR(5000),
  archived_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  -- Target of the composite (workspace_id, deal_id) foreign keys of the history, leads, tasks,
  -- activities.
  UNIQUE (workspace_id, id),
  -- One deal per converted lead (the conversion's backstop); also the lead foreign key's index.
  UNIQUE (workspace_id, lead_id),
  -- A deal can only link a stage, company, contact, source and lead of its own workspace.
  FOREIGN KEY (workspace_id, stage_id) REFERENCES pipeline_stages (workspace_id, id)
    ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, company_id) REFERENCES companies (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, contact_id) REFERENCES contacts (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, source_id) REFERENCES lead_sources (workspace_id, id)
    ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, lead_id) REFERENCES leads (workspace_id, id) ON DELETE RESTRICT,
  -- As companies: removing the member leaves their deals unassigned.
  FOREIGN KEY (workspace_id, owner_id) REFERENCES workspace_members (workspace_id, user_id)
    ON DELETE SET NULL (owner_id)
);
-- The foreign keys (and the stage, owner, company and contact filters).
CREATE INDEX idx_deals_stage ON deals(workspace_id, stage_id);
CREATE INDEX idx_deals_owner ON deals(workspace_id, owner_id);
CREATE INDEX idx_deals_company ON deals(workspace_id, company_id);
CREATE INDEX idx_deals_contact ON deals(workspace_id, contact_id);
CREATE INDEX idx_deals_source ON deals(workspace_id, source_id);
CREATE INDEX idx_deals_closed_at ON deals(workspace_id, closed_at);

-- Every stage a deal entered, the first one included (from_* null). Stage names are snapshots, as
-- they were at the change; the ids go null when a stage is deleted (SET NULL updates these rows, so
-- never put an append-only trigger on this table).
CREATE TABLE deal_stage_changes (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  deal_id BIGINT NOT NULL,
  from_stage_id BIGINT,
  from_stage_name VARCHAR(60),
  to_stage_id BIGINT,
  to_stage_name VARCHAR(60) NOT NULL,
  -- The member's user id, without a foreign key: the history outlives memberships and accounts
  -- (as audit_events.actor_id). The API shows null once the user is no member.
  changed_by BIGINT,
  changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  FOREIGN KEY (workspace_id, deal_id) REFERENCES deals (workspace_id, id) ON DELETE RESTRICT,
  -- Deleting a stage keeps its history rows, by name. The column lists matter: a plain SET NULL
  -- would null workspace_id too.
  FOREIGN KEY (workspace_id, from_stage_id) REFERENCES pipeline_stages (workspace_id, id)
    ON DELETE SET NULL (from_stage_id),
  FOREIGN KEY (workspace_id, to_stage_id) REFERENCES pipeline_stages (workspace_id, id)
    ON DELETE SET NULL (to_stage_id)
);
-- A deal's history, newest first, and the stage foreign keys.
CREATE INDEX idx_deal_stage_changes_deal ON deal_stage_changes(workspace_id, deal_id, changed_at);
CREATE INDEX idx_deal_stage_changes_from ON deal_stage_changes(workspace_id, from_stage_id);
CREATE INDEX idx_deal_stage_changes_to ON deal_stage_changes(workspace_id, to_stage_id);

-- The converted deal of a lead (V14 already indexed the column).
ALTER TABLE leads ADD CONSTRAINT leads_workspace_id_converted_deal_id_fkey
  FOREIGN KEY (workspace_id, converted_deal_id) REFERENCES deals (workspace_id, id)
  ON DELETE RESTRICT;
