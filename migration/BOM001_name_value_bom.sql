-- BOM001: Tier 1 — Add Name/Value (SearchKey) columns to per-building BOM DBs
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3
-- Convention: iDempiere _ID/Name/Value triad. No PK changes, no FK changes.
-- Target: {PREFIX}_BOM.db (SH_BOM.db, DX_BOM.db, etc.)
-- Skips: M_AttributeInstance (already conformant)

-- M_AttributeSetInstance — has Description, add Name + Value
ALTER TABLE M_AttributeSetInstance ADD COLUMN Name TEXT;
ALTER TABLE M_AttributeSetInstance ADD COLUMN Value TEXT;
UPDATE M_AttributeSetInstance SET Name = Description WHERE Name IS NULL;

-- ad_sysconfig — has config_key/config_value
ALTER TABLE ad_sysconfig ADD COLUMN Name TEXT;
ALTER TABLE ad_sysconfig ADD COLUMN Value TEXT;
UPDATE ad_sysconfig SET Name = config_key WHERE Name IS NULL;
UPDATE ad_sysconfig SET Value = config_value WHERE Value IS NULL;

-- m_bom_line — has role, child_name_pattern
ALTER TABLE m_bom_line ADD COLUMN Name TEXT;
ALTER TABLE m_bom_line ADD COLUMN Value TEXT;
UPDATE m_bom_line SET Name = role || CASE WHEN child_name_pattern IS NOT NULL THEN '_' || child_name_pattern ELSE '' END WHERE Name IS NULL;
UPDATE m_bom_line SET Value = bom_id || ':' || sequence WHERE Value IS NULL;
