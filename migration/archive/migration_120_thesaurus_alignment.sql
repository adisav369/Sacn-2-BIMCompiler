-- migration_120_thesaurus_alignment.sql
-- Phase 120: Thesaurus → AD Tables alignment
-- Fixes data gaps identified by comparing Thesaurus cross-stone analysis vs AD table contents.
-- All changes are idempotent (INSERT OR IGNORE, UPDATE with WHERE clause).

-- ============================================================
-- 1a. Fix US_Residential window family mappings (Critical #1, #2)
-- Thesaurus evidence: Duplex bedrooms have 2800x2410 fixed windows, not 819x759 casements.
-- Duplex kitchens have 750x2200 narrow windows, not 819x759 casements.
-- ============================================================

UPDATE ad_space_type_opening
SET family_id = 'US_FIXED_2800', sill_height_mm = 300
WHERE space_type_id = 'BEDROOM' AND opening_role = 'NATURAL_LIGHT'
  AND profile = 'US_Residential';

UPDATE ad_space_type_opening
SET family_id = 'US_FIXED_750', sill_height_mm = 0
WHERE space_type_id = 'KITCHEN' AND opening_role = 'NATURAL_LIGHT'
  AND profile = 'US_Residential';

-- ============================================================
-- 1b. Add 54mm furring rule for BATHROOM→BATHROOM interior walls (Critical #3)
-- Thesaurus evidence: 8 bathtub liner walls in Duplex at 54mm (38mm stud + finish).
-- Context: bathroom-to-bathroom partition (e.g. shared wet wall between two baths).
-- INTERIOR_FURRING_38 wall type already exists (54mm total).
-- ============================================================

INSERT OR IGNORE INTO ad_wall_type_rule
  (context, space_type_a, space_type_b, wall_type_id, priority, description, is_active, profile)
VALUES
  ('INTERIOR', 'BATHROOM', 'BATHROOM', 'INTERIOR_FURRING_38', 30,
   'Bathroom-to-bathroom liner wall: 54mm furring (Thesaurus: 8 in Duplex)', 1, 'US_Residential');

-- ============================================================
-- 1c. Add 550mm party wall type + rule (Critical #4)
-- Thesaurus evidence: 2 Duplex party walls at 550mm (thicker CMU variant).
-- ============================================================

INSERT OR IGNORE INTO ad_wall_type
  (wall_type_id, category, construction, total_mm, is_loadbearing, is_exterior,
   material_primary, description, is_active)
VALUES
  ('PARTY_CMU_550', 'PARTY', 'CMU', 550, 1, 0, 'Concrete Masonry',
   'Thick party wall variant — 2 in Duplex IFC (Thesaurus)', 1);

INSERT OR IGNORE INTO ad_wall_type_rule
  (context, space_type_a, space_type_b, wall_type_id, priority, description, is_active, profile)
VALUES
  ('PARTY', 'LIVING', NULL, 'PARTY_CMU_550', 40,
   'US residential party wall at living zones: 550mm CMU (Thesaurus)', 1, 'US_Residential');

-- ============================================================
-- 1d. Add Malaysian wall types (Critical #5, from Terminal Thesaurus)
-- Evidence: 330 ARC walls — 306 at 150mm, 15 at 250mm, 6 at 230mm, 3 at 300mm.
-- 93% of Terminal walls = 150mm BrickPlaster (no insulation cavity in tropics).
-- ============================================================

INSERT OR IGNORE INTO ad_wall_type
  (wall_type_id, category, construction, total_mm, is_loadbearing, is_exterior,
   material_primary, description, is_active)
VALUES
  ('EXTERIOR_MY_BRICK_150', 'EXTERIOR', 'BRICK_BLOCK', 150, 1, 1,
   'Brick', 'MY 93% of terminal walls — BrickPlaster (Thesaurus)', 1),
  ('EXTERIOR_MY_BRICK_250', 'EXTERIOR', 'BRICK_BLOCK', 250, 1, 1,
   'Brick', 'MY thick exterior — 15 terminal walls (Thesaurus)', 1),
  ('INTERIOR_MY_AHU_230', 'INTERIOR', 'BRICK_BLOCK', 230, 0, 0,
   'Brick', 'MY AHU enclosure — 6 terminal walls (Thesaurus)', 1),
  ('COPING_MY_300', 'EXTERIOR', 'CONCRETE', 300, 0, 0,
   'Concrete', 'MY coping/parapet — 3 terminal walls (Thesaurus)', 1);

-- Malaysian Institutional wall type rules
-- Lower priority number = higher priority (checked first).
-- Profile-specific rules (priority 50) beat generic rules (priority 100).

INSERT OR IGNORE INTO ad_wall_type_rule
  (context, space_type_a, space_type_b, wall_type_id, priority, description, is_active, profile)
VALUES
  ('EXTERIOR', NULL, NULL, 'EXTERIOR_MY_BRICK_150', 50,
   'MY institutional exterior: 150mm BrickPlaster (Thesaurus Rule 1)', 1, 'Malaysian_Institutional'),
  ('INTERIOR', NULL, NULL, 'EXTERIOR_MY_BRICK_150', 50,
   'MY institutional interior: 150mm BrickPlaster — same as exterior (Thesaurus)', 1, 'Malaysian_Institutional');

-- ============================================================
-- 1e. Add BEDROOM WARDROBE slot (Moderate #6)
-- Thesaurus evidence: 8 tall cabinets in Duplex bedrooms.
-- ============================================================

INSERT OR IGNORE INTO ad_room_slot
  (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES
  ('BEDROOM', 'WARDROBE', 'WARDROBE_SET', '[1.0,2.0)', 'LEFT', 30, 0);

INSERT OR IGNORE INTO ad_bom
  (bom_id, bom_name, description, group_by, is_active)
VALUES
  ('WARDROBE_SET', 'Wardrobe Set', 'Tall cabinet/wardrobe for bedrooms (Thesaurus)', 'ROOM', 1);

-- ============================================================
-- Verification queries (run manually to check):
-- SELECT * FROM ad_space_type_opening WHERE profile='US_Residential' AND opening_role='NATURAL_LIGHT';
-- SELECT * FROM ad_wall_type WHERE wall_type_id LIKE '%MY%' OR wall_type_id LIKE '%550%';
-- SELECT * FROM ad_wall_type_rule WHERE profile='Malaysian_Institutional' OR profile='US_Residential';
-- SELECT * FROM ad_room_slot WHERE room_type='BEDROOM';
-- SELECT * FROM ad_bom WHERE bom_id='WARDROBE_SET';
-- ============================================================
