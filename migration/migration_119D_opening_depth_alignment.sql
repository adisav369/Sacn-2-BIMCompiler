-- Migration 119D: Opening Depth Alignment
-- From Rosetta Thesaurus analysis:
--   Duplex windows have thin_dim = 417mm (fill full wall void)
--   UK window thin_dim = 352-353mm (sub-frame inset)
--   Generic windows/doors default to 100mm (too thin)
-- Grammar rule: OPENING.thin = frame_depth from family, NOT panel mesh depth

-- US Residential windows: thin = wall thickness (417mm) — windows fill full void
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'US_CASEMENT_819';
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'US_FIXED_2800';
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'US_FIXED_4835';
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'US_FIXED_750';
UPDATE ad_opening_family SET depth_mm = 178 WHERE family_id = 'US_SKYLIGHT_1180';  -- from reference: 178mm

-- Duplex-named door families
UPDATE ad_opening_family SET depth_mm = 467 WHERE family_id = 'DUPLEX_ENTRY';
UPDATE ad_opening_family SET depth_mm = 467 WHERE family_id = 'DUPLEX_GLASS';
UPDATE ad_opening_family SET depth_mm = 174 WHERE family_id = 'DUPLEX_INTERIOR_762';
UPDATE ad_opening_family SET depth_mm = 174 WHERE family_id = 'DUPLEX_INTERIOR_864';

-- Duplex-named window families (legacy, should also match)
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'DUPLEX_CASEMENT';
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'DUPLEX_BEDROOM_WINDOW';
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'DUPLEX_LIVING_WINDOW';
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'DUPLEX_NARROW_WINDOW';
UPDATE ad_opening_family SET depth_mm = 417 WHERE family_id = 'DUPLEX_SMALL_FIXED';
UPDATE ad_opening_family SET depth_mm = 178 WHERE family_id = 'DUPLEX_SKYLIGHT';

-- Generic residential families: reasonable defaults
-- RES_ENTRY: standard residential entry door frame ~150mm
UPDATE ad_opening_family SET depth_mm = 150 WHERE family_id = 'RES_ENTRY';
-- RES_INTERNAL: standard internal door frame ~150mm
UPDATE ad_opening_family SET depth_mm = 150 WHERE family_id = 'RES_INTERNAL';
-- BATHROOM_DOOR: same as internal
UPDATE ad_opening_family SET depth_mm = 150 WHERE family_id = 'BATHROOM_DOOR';
-- STD_WINDOW: standard casement frame ~150mm
UPDATE ad_opening_family SET depth_mm = 150 WHERE family_id = 'STD_WINDOW';
-- LARGE_WINDOW: standard frame
UPDATE ad_opening_family SET depth_mm = 150 WHERE family_id = 'LARGE_WINDOW';
-- SMALL_WINDOW: standard frame
UPDATE ad_opening_family SET depth_mm = 150 WHERE family_id = 'SMALL_WINDOW';
-- CLASSROOM_WINDOW: institutional frame ~200mm
UPDATE ad_opening_family SET depth_mm = 200 WHERE family_id = 'CLASSROOM_WINDOW';
-- FIRE_DOOR: fire-rated frame ~200mm
UPDATE ad_opening_family SET depth_mm = 200 WHERE family_id = 'FIRE_DOOR';
UPDATE ad_opening_family SET depth_mm = 200 WHERE family_id = 'FIRE_DOOR_WIDE';
-- DOUBLE_LEAF: fire-rated double frame ~200mm
UPDATE ad_opening_family SET depth_mm = 200 WHERE family_id = 'DOUBLE_LEAF';

-- Verify
-- SELECT family_id, depth_mm FROM ad_opening_family WHERE is_active=1 ORDER BY family_id;
