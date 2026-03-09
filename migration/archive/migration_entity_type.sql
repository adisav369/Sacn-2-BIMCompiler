-- Migration: Add entity_type to m_bom and m_bom_line
-- iDempiere EntityType: D=Dictionary (shipped, read-only), U=User (verb-created), A=Application (custom industry)
-- Existing rows default to 'D' — they are dictionary catalog data, immutable through PO layer.
-- BIM COBOL verbs set entity_type='U' on new records.
--
-- Idempotent: ALTER TABLE ADD COLUMN is no-op if column already exists (SQLite ignores).

ALTER TABLE m_bom ADD COLUMN entity_type TEXT DEFAULT 'D';
ALTER TABLE m_bom_line ADD COLUMN entity_type TEXT DEFAULT 'D';
