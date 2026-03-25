-- W009: Add AD_Org_ID FK to C_OrderLine
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5
--
-- AD_Org_ID is the integer FK to AD_Org (in ERP.db). TEXT Discipline column
-- remains for human readability (backward compatibility). New code should
-- prefer AD_Org_ID for joins and filtering; TEXT Discipline is derived.
--
-- Backfill uses ATTACH to resolve AD_Org.Value → AD_Org_ID.
-- C_OrderLine lives in work_output DB; AD_Org lives in ERP.db.

-- ── Add column ────────────────────────────────────────────────────────
ALTER TABLE C_OrderLine ADD COLUMN AD_Org_ID INTEGER;

-- ── Backfill from AD_Org lookup ───────────────────────────────────────
-- NOTE: Caller must ATTACH ERP.db AS erp before running this statement.
-- WorkOutputDAO.initSchema() handles the ATTACH lifecycle.
-- Handle legacy FPR → FP mapping (W003 used 'FPR' for fire protection)
UPDATE C_OrderLine SET AD_Org_ID = (
    SELECT AD_Org_ID FROM erp.AD_Org WHERE erp.AD_Org.Value =
        CASE WHEN C_OrderLine.Discipline = 'FPR' THEN 'FP' ELSE C_OrderLine.Discipline END
) WHERE AD_Org_ID IS NULL;

-- Default: unmapped disciplines → 0 (Shared/*)
UPDATE C_OrderLine SET AD_Org_ID = 0 WHERE AD_Org_ID IS NULL;

-- ── Index ─────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_orderline_ad_org_id ON C_OrderLine(AD_Org_ID);
