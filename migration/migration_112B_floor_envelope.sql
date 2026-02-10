-- Phase 112B: Add floor plate envelope to TYPICAL_CONDO_FLOOR
-- Tower footprint: B→E = [8m, 20m] = 12m wide
-- Constrains fill_remaining to resolve units within tower, not full 30m grid
-- Idempotent: INSERT OR IGNORE

-- CORE child (bom_child_id=50) carries the envelope params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
VALUES (50, 'envelope_x_start', 'B'),
       (50, 'envelope_x_end', 'E');
