-- Deleting an account sets invited_by to NULL on its invitations; without an index that scans.
CREATE INDEX idx_workspace_invitations_invited_by ON workspace_invitations(invited_by);
