-- §6.12.2 J4: BOM line UOM for variable-length MEP pieces
-- Follows Compiere/iDempiere convention: BOMQty + C_UOM_ID
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 §6 — InterimWorkshop

-- c_uom_id: unit of measure (EA=each, MM=millimetres, M=metres)
-- When UOM is a length unit, qty is the dimension along forward_axis.
-- The walker calls InterimWorkshop instead of using library dimensions.
-- EA is the default — all existing data is unchanged.
ALTER TABLE m_bom_line ADD COLUMN c_uom_id TEXT DEFAULT 'EA'
    CHECK(c_uom_id IN ('EA','MM','M','KG','M2','M3'));
