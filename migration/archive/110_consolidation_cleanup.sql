-- Phase 110: Consolidation cleanup — remove orphaned sanity check entries
-- Date: 2026-02-10
-- Purpose: Remove 3 check entries from ad_check_applicability that have no
--          Java implementation (fire_lift, protected_stairs, stairwell_pressurization).
--          These are regulatory requirements beyond current compiler scope.
--          Also remove corresponding threshold entries.

-- Remove orphaned check entries (no Java class exists)
DELETE FROM ad_check_applicability WHERE check_id = 'fire_lift';
DELETE FROM ad_check_applicability WHERE check_id = 'protected_stairs';
DELETE FROM ad_check_applicability WHERE check_id = 'stairwell_pressurization';

-- Remove corresponding threshold entries
DELETE FROM ad_check_threshold WHERE check_id = 'fire_lift';
DELETE FROM ad_check_threshold WHERE check_id = 'protected_stairs';
DELETE FROM ad_check_threshold WHERE check_id = 'stairwell_pressurization';
