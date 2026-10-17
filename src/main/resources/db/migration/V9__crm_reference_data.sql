CREATE TABLE pipeline_stages (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  name VARCHAR(60) NOT NULL,
  position INT NOT NULL,
  probability SMALLINT NOT NULL CHECK (probability BETWEEN 0 AND 100),
  kind VARCHAR(8) NOT NULL CHECK (kind IN ('OPEN', 'WON', 'LOST')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  -- Target of the deals' composite (workspace_id, stage_id) foreign keys.
  UNIQUE (workspace_id, id)
);
CREATE UNIQUE INDEX uq_pipeline_stages_name ON pipeline_stages(workspace_id, lower(name));
-- Exactly one Won and one Lost stage per workspace (the app never creates or deletes them).
CREATE UNIQUE INDEX uq_pipeline_stages_won ON pipeline_stages(workspace_id) WHERE kind = 'WON';
CREATE UNIQUE INDEX uq_pipeline_stages_lost ON pipeline_stages(workspace_id) WHERE kind = 'LOST';

CREATE TABLE lead_sources (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
  name VARCHAR(60) NOT NULL,
  archived_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT,
  UNIQUE (workspace_id, id)
);
CREATE UNIQUE INDEX uq_lead_sources_name ON lead_sources(workspace_id, lower(name));
