-- Phase 0: user provisioning (/users/me upsert) is not yet implemented.
-- These FKs will be re-added in Phase 1 once the auth/provisioning endpoint exists.
ALTER TABLE workspace        DROP CONSTRAINT IF EXISTS workspace_created_by_fkey;
ALTER TABLE workspace_member DROP CONSTRAINT IF EXISTS workspace_member_user_id_fkey;
