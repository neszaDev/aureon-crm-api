CREATE TABLE tasks (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  title VARCHAR(200) NOT NULL,
  description VARCHAR(5000),
  due_at TIMESTAMP WITH TIME ZONE,
  priority VARCHAR(8) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
  status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
  assignee_id BIGINT,
  -- The member's user id, null once they left the workspace (as assignee_id and the owners).
  created_by BIGINT,
  completed_at TIMESTAMP WITH TIME ZONE,
  company_id BIGINT,
  contact_id BIGINT,
  lead_id BIGINT,
  deal_id BIGINT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  -- Only a completed task has a completion time.
  CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
  -- A task can only link a company, contact, lead and deal of its own workspace.
  FOREIGN KEY (workspace_id, company_id) REFERENCES companies (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, contact_id) REFERENCES contacts (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, lead_id) REFERENCES leads (workspace_id, id) ON DELETE RESTRICT,
  FOREIGN KEY (workspace_id, deal_id) REFERENCES deals (workspace_id, id) ON DELETE RESTRICT,
  -- As the owners of the other records: removing the member leaves the task unassigned, and
  -- without a creator. The column lists keep workspace_id, which is NOT NULL.
  FOREIGN KEY (workspace_id, assignee_id) REFERENCES workspace_members (workspace_id, user_id)
    ON DELETE SET NULL (assignee_id),
  FOREIGN KEY (workspace_id, created_by) REFERENCES workspace_members (workspace_id, user_id)
    ON DELETE SET NULL (created_by)
);
-- The assignee's tasks by status and due time (the reminders and the "mine" list), and the
-- assignee foreign key.
CREATE INDEX idx_tasks_assignee ON tasks(workspace_id, assignee_id, status, due_at);
-- The other foreign keys (and the link filters).
CREATE INDEX idx_tasks_created_by ON tasks(workspace_id, created_by);
CREATE INDEX idx_tasks_company ON tasks(workspace_id, company_id);
CREATE INDEX idx_tasks_contact ON tasks(workspace_id, contact_id);
CREATE INDEX idx_tasks_lead ON tasks(workspace_id, lead_id);
CREATE INDEX idx_tasks_deal ON tasks(workspace_id, deal_id);
