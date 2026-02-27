-- Phase ST-1b: Aspect Columns + DX Template Branch
--
-- Adds 3 aspect columns to M_BomCategoryLine for parametric branching:
--   num_units      — 0=universal, 1=single-household, 2=dual-household
--   storey_count   — informational: how many storeys this subtree spans
--   mirroring_rule — 'NONE' or 'PARTY_WALL_PI' (aspect injection for pair)
--
-- Seeds the DX template branch: RE→PR→HU→{L1,L2}→rooms

-- ─── 1a. Add aspect columns ──────────────────────────────────────────────────
ALTER TABLE M_BomCategoryLine ADD COLUMN num_units      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE M_BomCategoryLine ADD COLUMN storey_count   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE M_BomCategoryLine ADD COLUMN mirroring_rule TEXT    NOT NULL DEFAULT 'NONE';

-- ─── 1b. Tag existing SH lines ──────────────────────────────────────────────
-- SL and RF are universal (num_units=0): apply to all configurations
UPDATE M_BomCategoryLine SET num_units=0
    WHERE M_BomCategory_ID='RE' AND Child_BomCategory_ID IN ('SL','RF');
-- GF is single-household path (num_units=1, 1 storey)
UPDATE M_BomCategoryLine SET num_units=1, storey_count=1
    WHERE M_BomCategory_ID='RE' AND Child_BomCategory_ID='GF';
-- GF children stay num_units=0 (room-level, universal)

-- ─── 1c. Seed DX template branch ────────────────────────────────────────────
-- RE → PR (duplex pair container, num_units=2, 2-storey body)
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (9, 'RE', 'PR', 15, 'DuplexPair', 'Duplex Unit Pair',
   0.0, 0.836, 1, 1, 1, 2, 2, 'NONE');

-- PR → 2× HU (mirrored half-units)
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (10, 'PR', 'HU', 10, 'UnitA', 'Unit A (original)',
   0.0, 1.0, 1, 1, 1, 0, 0, 'NONE');
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (11, 'PR', 'HU', 20, 'UnitB', 'Unit B (mirrored)',
   0.0, 1.0, 1, 1, 1, 0, 0, 'PARTY_WALL_PI');

-- HU → L1 + L2 (two storeys per half-unit)
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (12, 'HU', 'L1', 10, 'GroundLevel', 'Ground Level',
   0.0, 0.5, 1, 1, 1, 0, 1, 'NONE');
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (13, 'HU', 'L2', 20, 'UpperLevel', 'Upper Level',
   0.5, 0.5, 1, 1, 1, 0, 1, 'NONE');

-- L1 → ground floor room categories
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (14, 'L1', 'LI', 10, 'Living', 'Living Room',
   0.0, 0.0, 1, 1, 1, 0, 0, 'NONE');
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (15, 'L1', 'DN', 20, 'Dining', 'Dining Room',
   0.0, 0.0, 1, 0, 1, 0, 0, 'NONE');
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (16, 'L1', 'KT', 30, 'Kitchen', 'Kitchen',
   0.0, 0.0, 1, 0, 1, 0, 0, 'NONE');
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (17, 'L1', 'BT', 40, 'Bathroom', 'Bathroom',
   0.0, 0.0, 1, 0, 1, 0, 0, 'NONE');

-- L2 → upper floor room categories
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (18, 'L2', 'BD', 10, 'Bedroom', 'Bedroom',
   0.0, 0.0, 1, 1, 3, 0, 0, 'NONE');
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (19, 'L2', 'KT', 20, 'KitchenUpper', 'Kitchen (Upper)',
   0.0, 0.0, 1, 0, 1, 0, 0, 'NONE');
INSERT INTO M_BomCategoryLine
  (M_BomCategoryLine_ID, M_BomCategory_ID, Child_BomCategory_ID, Sequence,
   Value, Name, Z_Offset_Ratio, Z_Extent_Ratio, IsActive,
   MinQty, MaxQty, num_units, storey_count, mirroring_rule)
VALUES
  (20, 'L2', 'BT', 30, 'BathroomUpper', 'Bathroom (Upper)',
   0.0, 0.0, 1, 0, 1, 0, 0, 'NONE');
