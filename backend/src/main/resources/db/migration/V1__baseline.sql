-- Slice 0 establishes migration ownership and PostgreSQL extensions only.
-- Business tables are introduced by their owning vertical slices.
CREATE EXTENSION IF NOT EXISTS btree_gist;
