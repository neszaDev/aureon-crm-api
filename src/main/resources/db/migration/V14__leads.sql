CREATE TABLE leads (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100),
  company_name VARCHAR(200),
  email VARCHAR(254),
  phone VARCHAR(50),
  job_title VARCHAR(100),
  source_id BIGINT,
  owner_id BIGINT,
  status VARCHAR(16) NOT NULL CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED')),
  priority VARCHAR(8) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
  follow_up_date DATE,
  estimated_value NUMERIC(15,2) CHECK (estimated_value >= 0),
  currency CHAR(3),
  unqualified_reason VARCHAR(500),
  notes VARCHAR(5000),
  converted_at TIMESTAMP WITH TIME ZONE,
  -- The foreign keys of the converted_* columns: company and contact below, deal in V15.
  converted_company_id BIGINT,
  converted_contact_id BIGINT,
  converted_deal_id BIGINT,
  archived_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  -- An amount comes with its currency, and a currency with its amount.
  CHECK ((estimated_value IS NULL) = (currency IS NULL)),
  -- Target of the composite (workspace_id, lead_id) foreign keys of deals, tasks, activities.
  UNIQUE (workspace_id, id),
  -- A lead can only link a source, a company and a contact of its own workspace.
  FOREIGN KEY (workspace_id, source_id) REFERENCES lead_sources (workspace_id, id)
    ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, converted_company_id) REFERENCES companies (workspace_id, id)
    ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, converted_contact_id) REFERENCES contacts (workspace_id, id)
    ON DELETE RESTRICT,
  -- As companies: removing the member leaves their leads unassigned.
  FOREIGN KEY (workspace_id, owner_id) REFERENCES workspace_members (workspace_id, user_id)
    ON DELETE SET NULL (owner_id)
);
-- The foreign keys (and the owner and source filters).
CREATE INDEX idx_leads_source ON leads(workspace_id, source_id);
CREATE INDEX idx_leads_owner ON leads(workspace_id, owner_id);
CREATE INDEX idx_leads_converted_company ON leads(workspace_id, converted_company_id);
CREATE INDEX idx_leads_converted_contact ON leads(workspace_id, converted_contact_id);
CREATE INDEX idx_leads_converted_deal ON leads(workspace_id, converted_deal_id);
CREATE INDEX idx_leads_status ON leads(workspace_id, status);
-- The email duplicate check.
CREATE INDEX idx_leads_email ON leads(workspace_id, lower(email));
