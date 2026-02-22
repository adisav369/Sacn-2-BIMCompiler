-- ============================================================
-- Phase 4e — ad_building_registry.doc_status
-- Date: 2026-02-23
-- VIEW_CONTRACTS.md §1.2
--
-- Adds doc_status lifecycle column to ad_building_registry.
-- Values: DR (Draft), IP (In Progress), CO (Confirmed), VO (Voided).
-- No Java behaviour change. No existing test modification.
-- No view filter references doc_status — Rule 5 permanent ban.
--
-- iDempiere analogue: DocStatus on AD_Client / M_Product (lifecycle flag)
-- ============================================================

BEGIN TRANSACTION;

ALTER TABLE ad_building_registry
ADD COLUMN doc_status TEXT NOT NULL DEFAULT 'DR'
    CHECK(doc_status IN ('DR', 'IP', 'CO', 'VO'));

-- CO: all 4 current buildings — proven by 119 tests, all 6 views LIVE.
--     SH: 58 elements. DX: 1197 elements. TB-LKTN: 138 elements. Terminal: ~51088.
UPDATE ad_building_registry
SET doc_status = 'CO';

COMMIT;

-- ── Verification ──────────────────────────────────────────────
SELECT 'ad_building_registry doc_status:';
SELECT building_id, building_type, doc_status FROM ad_building_registry ORDER BY building_id;
