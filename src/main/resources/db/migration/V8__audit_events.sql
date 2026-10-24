CREATE TABLE audit_events (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  -- No foreign key to users: the history outlives accounts.
  actor_id BIGINT,
  actor_email VARCHAR(254),
  -- No CHECK on action/entity_type: the sets of values grow with the CRM.
  action VARCHAR(32) NOT NULL,
  entity_type VARCHAR(32) NOT NULL,
  entity_id BIGINT NOT NULL,
  entity_label VARCHAR(200),
  changes JSONB NOT NULL,
  request_id VARCHAR(64)
);
CREATE INDEX idx_audit_events_workspace_time ON audit_events(workspace_id, occurred_at DESC);
CREATE INDEX idx_audit_events_entity
  ON audit_events(workspace_id, entity_type, entity_id, occurred_at DESC);

CREATE FUNCTION audit_events_append_only() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_append_only
  BEFORE UPDATE OR DELETE ON audit_events
  FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();
