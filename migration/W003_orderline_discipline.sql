-- W003: Add Discipline to C_OrderLine + Jurisdiction/OccupancyClass to C_Order
--
-- S60 ERP Model Alignment (S60_ERP_ALIGNMENT.md #1, BIM_Designer_SRS.md §30.6)
--
-- Discipline on C_OrderLine enables:
--   - FP/MEP/ELEC discipline lines in the order (U2)
--   - Cross-discipline clash validation (AD_Val_Rule.discipline filter)
--   - Correct NFPA 13 / UBBL rule selection per element
--
-- Jurisdiction + OccupancyClass on C_Order enable:
--   - Per-order building code selection: MY (UBBL), US (IRC), UK (NDSS) (U3)
--   - NFPA 13 occupancy-based sprinkler spacing: LH/OH1/OH2 (U4)
--
-- iDempiere parallel: C_OrderLine carries discipline context from
-- M_Product_Category. C_Order carries project-level regulatory context
-- (jurisdiction = tax region, occupancy = warehouse class).

-- ── C_OrderLine: Discipline ──────────────────────────────────────────
ALTER TABLE C_OrderLine ADD COLUMN Discipline TEXT DEFAULT 'ARC';

-- Backfill: derive from bom_category where possible
-- RF/STR categories → STR discipline, FP → FPR, MEP → MEP, default ARC
UPDATE C_OrderLine SET Discipline = CASE
    WHEN bom_category IN ('RF', 'STR', 'SL') THEN 'STR'
    WHEN bom_category = 'FP' THEN 'FPR'
    WHEN bom_category IN ('MEP', 'ELEC', 'PLB', 'ACMV') THEN bom_category
    ELSE 'ARC'
END WHERE Discipline = 'ARC' OR Discipline IS NULL;

CREATE INDEX IF NOT EXISTS idx_orderline_discipline ON C_OrderLine(Discipline);

-- ── C_Order: Jurisdiction + OccupancyClass ───────────────────────────
ALTER TABLE C_Order ADD COLUMN Jurisdiction TEXT DEFAULT 'MY';
ALTER TABLE C_Order ADD COLUMN OccupancyClass TEXT;
