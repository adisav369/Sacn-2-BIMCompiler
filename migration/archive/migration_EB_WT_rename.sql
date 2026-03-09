-- ============================================================
-- Migration: BOM DocType/Sub Regime — Fix Terminology Drift
--
-- Phase 1 (already applied): EXT_ → EB_, UNIT_ → WT_ bom_id renames
-- Phase 2 (this update): Fix EB_ bom_category and bom_level
--
-- EB_ BOMs had bom_category='EB' (compilation mode name) — wrong.
-- EN-BLOC is a compilation decision, not a structural role.
-- Structural role is UNIT, same as WT_ BOMs.
-- POC disambiguation is via bom_id prefix (EB_/WT_), not bom_category.
--
-- EN-BLOC (EB_): HelloWorld POC — BOM lines already tacked,
--   take as-is when AABB and DocType consistent throughout.
-- WALK THRU (WT_): Production path — recalculates by tacking
--   through each BOM layer (UNIT → FLOOR → SET → BUY).
--
-- Both produce the same result when the data stack is consistent.
--
-- Idempotent: safe to run multiple times.
-- ============================================================

-- ── Phase 1: Rename bom_ids (already applied — idempotent) ───

UPDATE m_bom SET bom_category = 'UN'
  WHERE bom_category = 'EXTRACTED';

-- EXT_SH → EB_SH
UPDATE m_bom_line SET bom_id = 'EB_SH'
  WHERE bom_id = 'EXT_SH';
UPDATE m_bom SET bom_id = 'EB_SH'
  WHERE bom_id = 'EXT_SH';

-- EXT_DX → EB_DX
UPDATE m_bom_line SET bom_id = 'EB_DX'
  WHERE bom_id = 'EXT_DX';
UPDATE m_bom SET bom_id = 'EB_DX'
  WHERE bom_id = 'EXT_DX';

-- UNIT_SH_STD → WT_SH
UPDATE m_bom_line SET child_product_id = 'WT_SH'
  WHERE child_product_id = 'UNIT_SH_STD';
UPDATE m_bom_line SET bom_id = 'WT_SH'
  WHERE bom_id = 'UNIT_SH_STD';
UPDATE m_bom SET bom_id = 'WT_SH'
  WHERE bom_id = 'UNIT_SH_STD';

-- UNIT_DUPLEX_STD → WT_DX
UPDATE m_bom_line SET child_product_id = 'WT_DX'
  WHERE child_product_id = 'UNIT_DUPLEX_STD';
UPDATE m_bom_line SET bom_id = 'WT_DX'
  WHERE bom_id = 'UNIT_DUPLEX_STD';
UPDATE m_bom SET bom_id = 'WT_DX'
  WHERE bom_id = 'UNIT_DUPLEX_STD';

-- ── Phase 2: Fix EB_ structural role ─────────────────────────
-- bom_category=EB was provenance masquerading as structural role.
-- Both EB_ and WT_ are UNIT-level BOMs with bom_category=UN.

UPDATE m_bom SET bom_category = 'RE', bom_level = 'UNIT'
  WHERE bom_id IN ('EB_SH', 'EB_DX');

-- WT_ BOMs should also be RE (Residential), same as EB_
UPDATE m_bom SET bom_category = 'RE'
  WHERE bom_id IN ('WT_SH', 'WT_DX');

-- Remove stale EB row from M_BomCategory if it exists
DELETE FROM M_BomCategory WHERE M_BomCategory_ID = 'EB';

-- ── Verify ──────────────────────────────────────────────────

-- Both should show bom_category=UN
SELECT bom_id, bom_category, bom_level, doc_sub_type FROM m_bom
  WHERE bom_id IN ('EB_SH', 'EB_DX', 'WT_SH', 'WT_DX');

-- Should be zero (no orphan references)
SELECT COUNT(*) as orphan_ext FROM m_bom_line
  WHERE bom_id LIKE 'EXT_%' OR child_product_id LIKE 'EXT_%';
SELECT COUNT(*) as orphan_unit FROM m_bom_line
  WHERE bom_id LIKE 'UNIT_%_STD' OR child_product_id LIKE 'UNIT_%_STD';
