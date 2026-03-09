-- ============================================================================
-- Migration: c_order → iDempiere CamelCase column naming
-- Target DB: library/BOM.db
-- Date: 2026-03-04
-- Requires: SQLite 3.35.0+ (ALTER TABLE RENAME COLUMN + DROP COLUMN)
-- ============================================================================

-- ── c_order column renames ──────────────────────────────────────────────────

ALTER TABLE c_order RENAME COLUMN building_id             TO C_Order_ID;
ALTER TABLE c_order RENAME COLUMN building_name           TO Name;
-- building_type: DROP (redundant with C_DocType_ID → DocBaseType)
ALTER TABLE c_order RENAME COLUMN dsl_content             TO DSLContent;
ALTER TABLE c_order RENAME COLUMN output_db_path          TO OutputDbPath;
ALTER TABLE c_order RENAME COLUMN reference_db_path       TO ReferenceDbPath;
ALTER TABLE c_order RENAME COLUMN is_active               TO IsActive;
ALTER TABLE c_order RENAME COLUMN seq_no                  TO SeqNo;
ALTER TABLE c_order RENAME COLUMN expected_elements       TO ExpectedElements;
ALTER TABLE c_order RENAME COLUMN spatial_digest          TO SpatialDigest;
ALTER TABLE c_order RENAME COLUMN provenance              TO Provenance;
ALTER TABLE c_order RENAME COLUMN description             TO Description;
ALTER TABLE c_order RENAME COLUMN geometry_fail_threshold TO GeometryFailThreshold;
ALTER TABLE c_order RENAME COLUMN doc_status              TO DocStatus;
ALTER TABLE c_order RENAME COLUMN c_bpartner              TO C_BPartner_ID;
ALTER TABLE c_order RENAME COLUMN aabb_width_mm           TO AabbWidthMm;
ALTER TABLE c_order RENAME COLUMN aabb_depth_mm           TO AabbDepthMm;
ALTER TABLE c_order RENAME COLUMN aabb_height_mm          TO AabbHeightMm;
ALTER TABLE c_order RENAME COLUMN empty_space_checksum    TO EmptySpaceChecksum;
-- C_DocType_ID: already proper — no change

-- DROP building_type (redundant with C_DocType_ID)
ALTER TABLE c_order DROP COLUMN building_type;

-- ── c_orderline: DROP placement/material columns (§11.9 three-concern) ────
-- FIRST PRINCIPLE: c_orderline = WHAT only.
--   HOW (placement)   → PP_Order_Node
--   WITH WHAT (material) → M_Product
-- These columns should never have been on c_orderline.
-- See: docs/COLUMN_MIGRATION_MAP.md for full old→new mapping.

-- Drop dependent view first
DROP VIEW IF EXISTS v_compilable_element_rule;

-- Placement (HOW) — 8 columns
ALTER TABLE c_orderline DROP COLUMN host_type;
ALTER TABLE c_orderline DROP COLUMN host_ref;
ALTER TABLE c_orderline DROP COLUMN position_rule;
ALTER TABLE c_orderline DROP COLUMN position_value;
ALTER TABLE c_orderline DROP COLUMN position_value_2;
ALTER TABLE c_orderline DROP COLUMN position_value_3;
ALTER TABLE c_orderline DROP COLUMN height_mm;
ALTER TABLE c_orderline DROP COLUMN orientation;

-- Material (WITH WHAT) — 6 columns
ALTER TABLE c_orderline DROP COLUMN width_mm;
ALTER TABLE c_orderline DROP COLUMN height_extent_mm;
ALTER TABLE c_orderline DROP COLUMN depth_mm;
ALTER TABLE c_orderline DROP COLUMN geometry_hash;
ALTER TABLE c_orderline DROP COLUMN material_name;
ALTER TABLE c_orderline DROP COLUMN material_rgba;

-- ── c_orderline: RENAME WHAT columns to iDempiere CamelCase ──────────────

ALTER TABLE c_orderline RENAME COLUMN id               TO C_OrderLine_ID;
ALTER TABLE c_orderline RENAME COLUMN building_type    TO C_Order_ID;
ALTER TABLE c_orderline RENAME COLUMN storey           TO Storey;
ALTER TABLE c_orderline RENAME COLUMN element_ref      TO Name;
ALTER TABLE c_orderline RENAME COLUMN ifc_class        TO IfcClass;
ALTER TABLE c_orderline RENAME COLUMN discipline       TO Discipline;
ALTER TABLE c_orderline RENAME COLUMN family_ref       TO M_Product_ID;
ALTER TABLE c_orderline RENAME COLUMN is_active        TO IsActive;
ALTER TABLE c_orderline RENAME COLUMN building_id      TO AD_Building_ID;

-- ── Verification ────────────────────────────────────────────────────────────
-- Run after migration:
--   .schema c_orderline
--   SELECT C_OrderLine_ID, C_Order_ID, Storey, Name, IfcClass, Discipline,
--          M_Product_ID, IsActive, AD_Building_ID FROM c_orderline LIMIT 5;
--   SELECT C_Order_ID, Name, C_DocType_ID FROM c_order;
