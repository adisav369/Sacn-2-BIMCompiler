-- Phase RM-5: Flat table becomes computed cache
-- Add geometry failure threshold to building registry (Terminal has 8 known advisory failures)

ALTER TABLE ad_building_registry ADD COLUMN geometry_fail_threshold INTEGER DEFAULT 0;
UPDATE ad_building_registry SET geometry_fail_threshold = 8 WHERE building_id = 'SJTII_Terminal';
-- SH, DX, TB-LKTN keep default 0
