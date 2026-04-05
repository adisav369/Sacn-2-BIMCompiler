-- DV038: C_BPartner — manufacturer/supplier identity on M_Product and M_BOM
-- Implementing PREFAB_ARCHITECTURE.md §Three Orthogonal Dimensions
--
-- iDempiere standard: C_BPartner identifies WHO makes/supplies the product.
-- Three orthogonal dimensions:
--   M_Product_Category = WHAT type (RE/CO/IN)
--   C_BPartner = WHO makes it (brand/model line, IFC source accreditation)
--   SpaceSize (AABB) = HOW MUCH
--
-- Applied to ERP.db. C_BPartner.Location = source IFC for accreditation.

CREATE TABLE IF NOT EXISTS C_BPartner (
    C_BPartner_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    Value           TEXT NOT NULL UNIQUE,
    Name            TEXT NOT NULL,
    Location        TEXT,
    is_active       INTEGER DEFAULT 1
);

-- Add FK to M_Product and M_BOM
-- SQLite: ALTER TABLE ADD COLUMN is idempotent if column already exists (catch error)
ALTER TABLE M_Product ADD COLUMN C_BPartner_ID INTEGER REFERENCES C_BPartner(C_BPartner_ID);
ALTER TABLE M_BOM ADD COLUMN C_BPartner_ID INTEGER REFERENCES C_BPartner(C_BPartner_ID);
