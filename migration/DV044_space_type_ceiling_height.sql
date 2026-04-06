-- DV044: Default ceiling height per space type — fixes Z-axis breach in generative placement
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-DEVICE-PLACE
--
-- The generative walker uses BOM AABB height for room Z extent, but SET BOMs store
-- furniture extent (812mm sofa, 820mm vanity), NOT room ceiling height (2700mm).
-- Placement offsets (SWITCH 1200mm, SINK 850mm) exceed furniture height → Z breach.
--
-- Fix: metadata-driven ceiling height. The walker reads this when bomAABB height
-- is below threshold (2400mm), overriding with actual room height.

ALTER TABLE ad_space_type ADD COLUMN default_ceiling_height_mm INTEGER DEFAULT 2700;

-- Standard residential ceiling heights (NCC/BCA Vol 2, Part 3.8.2)
UPDATE ad_space_type SET default_ceiling_height_mm = 2700 WHERE Value IN (
    'BATHROOM', 'BEDROOM', 'KITCHEN', 'LIVING', 'DINING', 'STUDY',
    'HALLWAY', 'LAUNDRY', 'UTILITY', 'WET_KITCHEN', 'ENTRY',
    'WALK_IN_ROBE', 'ENSUITE', 'POWDER_ROOM', 'PANTRY'
);

-- Garage / carport — lower ceiling
UPDATE ad_space_type SET default_ceiling_height_mm = 2400 WHERE Value IN (
    'GARAGE', 'CARPORT', 'STORE'
);

-- Commercial / industrial
UPDATE ad_space_type SET default_ceiling_height_mm = 3000 WHERE Value IN (
    'OFFICE', 'RETAIL', 'CONFERENCE', 'RECEPTION'
);
