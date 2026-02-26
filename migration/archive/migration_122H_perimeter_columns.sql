-- Phase 122H: Perimeter grid columns
-- Terminal reference has 54 ARC columns at 600x300mm (perimeter) vs our 19 (4 CORNER + 15 T_JUNCTION).
-- Grid intersections on the building perimeter need PERIMETER type → RC_600x300.
-- Interior grid intersections stay INTERMEDIATE → RC_800x450.

-- Add PERIMETER rule for Malaysian_Institutional profile
INSERT OR IGNORE INTO ad_column_type_rule
    (context, profile, column_type_id, priority, description, is_active)
VALUES
    ('PERIMETER', 'Malaysian_Institutional', 'RC_600x300', 10,
     'Perimeter grid column — same cross-section as corner/T-junction', 1);

-- Add generic PERIMETER fallback for residential
INSERT OR IGNORE INTO ad_column_type_rule
    (context, profile, column_type_id, priority, description, is_active)
VALUES
    ('PERIMETER', NULL, 'RC_300x300', 100,
     'Default residential perimeter grid column', 1);
