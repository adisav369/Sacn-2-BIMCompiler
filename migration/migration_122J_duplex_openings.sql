-- Phase 122J: Duplex opening fixes from Rosetta Stone reading
-- Stone says: LIVING rooms have exterior glass door (813x2420mm) on south wall
-- Stone says: BATHROOM 819x759 windows (casement) on bathroom exterior walls

-- LIVING exterior glass door (patio door)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, profile, priority)
VALUES
    ('LIVING', 'EGRESS', 'US_GLASS_DOOR_813', 'FIXED', 1, 'exterior', 'US_Residential', 50);

-- LIVING-KITCHEN adjacency also needs a glass door — Stone shows kitchens may have glass entry
-- But the glass doors are on LIVING south walls, not KITCHEN
