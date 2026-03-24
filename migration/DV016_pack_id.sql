-- DV016: Add pack_id column to AD rule tables for jurisdiction-based rule packs.
-- Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
--
-- pack_id tags which rule pack a row belongs to:
--   BASE       — generic rules (no jurisdiction)
--   NFPA-13    — fire protection (international)
--   UBBL-2024  — Malaysia (UBBL, MS standards)
--   IBC-2021   — USA (IBC, NEC, IPC, IMC, TIA)
--
-- Append-only: no existing columns or rows modified, only new column + UPDATE.

-- ── ad_space_type_mep_bom (186 rows) ──────────────────────────────

ALTER TABLE ad_space_type_mep_bom ADD COLUMN pack_id TEXT DEFAULT 'BASE';

UPDATE ad_space_type_mep_bom SET pack_id = 'NFPA-13'
  WHERE building_code IN ('NFPA 13', 'NFPA 101');

UPDATE ad_space_type_mep_bom SET pack_id = 'UBBL-2024'
  WHERE building_code IN ('UBBL 291', 'MS 1228', 'MS IEC 60364', 'MS1525');

UPDATE ad_space_type_mep_bom SET pack_id = 'IBC-2021'
  WHERE building_code IN ('NEC 2020', 'IPC 2021', 'IMC 2021', 'TIA-568');

-- ── ad_fp_trigger (12 rows) ───────────────────────────────────────

ALTER TABLE ad_fp_trigger ADD COLUMN pack_id TEXT DEFAULT 'BASE';

UPDATE ad_fp_trigger SET pack_id = 'UBBL-2024'
  WHERE jurisdiction = 'MALAYSIA';

UPDATE ad_fp_trigger SET pack_id = 'NFPA-13'
  WHERE jurisdiction = 'INTERNATIONAL';

-- ── ad_fp_coverage (4 rows) ───────────────────────────────────────

ALTER TABLE ad_fp_coverage ADD COLUMN pack_id TEXT DEFAULT 'NFPA-13';

-- All 4 rows reference NFPA 13 — default handles it.

-- ── ad_code_requirement (23 rows) ─────────────────────────────────

ALTER TABLE ad_code_requirement ADD COLUMN pack_id TEXT DEFAULT 'BASE';

UPDATE ad_code_requirement SET pack_id = 'NFPA-13'
  WHERE code_id LIKE 'NFPA%';

UPDATE ad_code_requirement SET pack_id = 'UBBL-2024'
  WHERE code_id LIKE 'MS%';

UPDATE ad_code_requirement SET pack_id = 'IBC-2021'
  WHERE code_id IN ('NEC_2020', 'IPC_2021', 'IMC_2021');
