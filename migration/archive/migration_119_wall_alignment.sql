-- Phase 119: Wall thickness alignment for Rosetta Stone convergence
-- Adds profile column to ad_wall_type_rule for profile-specific wall resolution
-- Adds UK residential wall types for SampleHouse reference matching

-- Step 1: Add profile column (nullable = wildcard applies to all profiles)
-- Will error if column already exists — safe to ignore
ALTER TABLE ad_wall_type_rule ADD COLUMN profile TEXT DEFAULT NULL;

-- Step 2: New wall types for SampleHouse (UK residential)
INSERT OR IGNORE INTO ad_wall_type (wall_type_id, category, construction, stud_mm, total_mm,
  fire_rating, is_loadbearing, is_exterior, material_primary, is_active, description)
VALUES
  ('EXTERIOR_UK_BRICK_290', 'EXTERIOR', 'BRICK_BLOCK', 0, 290, NULL, 1, 1, 'Brick', 1, '102Bwk+75Ins+100LBlk+12P'),
  ('INTERIOR_UK_PARTITION_95', 'INTERIOR', 'MASONRY', 70, 95, NULL, 0, 0, 'Plasterboard', 1, '12P+70MStd+12P');

-- Step 3: Profile-specific rules (priority 50 beats generic priority 100)
-- Use INSERT OR REPLACE with explicit check to avoid NULL-comparison duplicates
INSERT OR REPLACE INTO ad_wall_type_rule (rule_id, context, space_type_a, space_type_b, wall_type_id, priority, is_active, description, profile)
SELECT COALESCE(
    (SELECT rule_id FROM ad_wall_type_rule WHERE context='EXTERIOR' AND space_type_a IS NULL AND space_type_b IS NULL AND profile='UK_Residential'),
    (SELECT MAX(rule_id)+1 FROM ad_wall_type_rule)),
  'EXTERIOR', NULL, NULL, 'EXTERIOR_UK_BRICK_290', 50, 1, 'UK residential exterior: 102Bwk+75Ins+100LBlk+12P', 'UK_Residential';

INSERT OR REPLACE INTO ad_wall_type_rule (rule_id, context, space_type_a, space_type_b, wall_type_id, priority, is_active, description, profile)
SELECT COALESCE(
    (SELECT rule_id FROM ad_wall_type_rule WHERE context='INTERIOR' AND space_type_a IS NULL AND space_type_b IS NULL AND profile='UK_Residential'),
    (SELECT MAX(rule_id)+1 FROM ad_wall_type_rule)),
  'INTERIOR', NULL, NULL, 'INTERIOR_UK_PARTITION_95', 50, 1, 'UK residential partition: 12P+70MStd+12P', 'UK_Residential';

INSERT OR REPLACE INTO ad_wall_type_rule (rule_id, context, space_type_a, space_type_b, wall_type_id, priority, is_active, description, profile)
SELECT COALESCE(
    (SELECT rule_id FROM ad_wall_type_rule WHERE context='EXTERIOR' AND space_type_a IS NULL AND space_type_b IS NULL AND profile='Malaysian_Residential'),
    (SELECT MAX(rule_id)+1 FROM ad_wall_type_rule)),
  'EXTERIOR', NULL, NULL, 'EXTERIOR_BRICK', 50, 1, 'Malaysian residential exterior: brick on block', 'Malaysian_Residential';
