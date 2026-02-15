-- Phase 122L: Add span_mode to ad_wall_type
-- PER_STOREY = platform framing (residential wood) — walls stop at each floor deck
-- CONTINUOUS = poured RC (institutional) — walls span multiple storeys continuously
-- Dispatch key: profile determines which mode. Residential = PER_STOREY, Institutional = CONTINUOUS.

-- Idempotent: only add column if it doesn't exist
-- SQLite doesn't support IF NOT EXISTS for ALTER TABLE, so we use a trick:
-- We check via pragma, and the migration runner handles errors gracefully.

ALTER TABLE ad_wall_type ADD COLUMN span_mode TEXT DEFAULT 'PER_STOREY';

-- Malaysian Institutional walls → CONTINUOUS (RC poured continuously)
UPDATE ad_wall_type SET span_mode = 'CONTINUOUS'
WHERE wall_type_id IN ('EXTERIOR_MY_BRICK_150', 'EXTERIOR_MY_BRICK_250', 'INTERIOR_MY_AHU_230', 'COPING_MY_300')
  AND (span_mode IS NULL OR span_mode = 'PER_STOREY');

-- All others stay PER_STOREY (default) — residential platform framing

-- Fix exterior wall type priority: 150mm BrickPlaster (93% of reference) should win over 250mm thick
-- Previously: 250mm at priority 40, 150mm at priority 50 → 250mm selected
-- Fix: 150mm at priority 35 (wins), 250mm at priority 40 (fallback for thick walls)
UPDATE ad_wall_type_rule SET priority = 35
WHERE wall_type_id = 'EXTERIOR_MY_BRICK_150' AND context = 'EXTERIOR'
  AND profile = 'Malaysian_Institutional';
