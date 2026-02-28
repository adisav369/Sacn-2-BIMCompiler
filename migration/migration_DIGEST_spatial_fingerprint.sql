-- migration_DIGEST_spatial_fingerprint.sql
-- Store computed spatial digests into c_order.spatial_digest.
--
-- Formula (name-agnostic, count-verified per class):
--   For each ifc_class (alphabetical):
--     "CLASS={ifc_class} COUNT={n}"
--     For each element (minX, minY, minZ order):
--       "{minX_mm}|{maxX_mm}|{minY_mm}|{maxY_mm}|{minZ_mm}|{maxZ_mm}"
--   sha256(all lines joined by "\n")
--
-- element_name EXCLUDED: compiled names differ from extracted IFC names.
-- COUNT per class INCLUDED: adding/removing any element changes the hash.
-- All IFC classes included: walls, slabs, roof, doors, windows, MEP, furniture.
--
-- These values are the golden master. BuildingRegistryTest enforces them.
-- Any geometry regression (dimension change, position shift, added/removed element)
-- will change the digest and fail the test gate.
--
-- Computed: 2026-02-28 | Formula version: NORM-DIGEST-1 (name-agnostic + count)

UPDATE c_order SET spatial_digest = 'fd347105d34ceda22d9e6b7873f75997f8f197b138998c7545b533626736603c'
WHERE building_id = 'Ifc4_SampleHouse';

UPDATE c_order SET spatial_digest = 'd65ac3ce0eaed159e91536c8cad5e42322be2f6113791dd0e544f2b2da4ba4b1'
WHERE building_id = 'Ifc2x3_Duplex';

UPDATE c_order SET spatial_digest = '448453742e8731af8b80b98d7a42cf65565aae639006201f10f963d615b4f78b'
WHERE building_id = 'TB_LKTN';

UPDATE c_order SET spatial_digest = 'fed88a1a8802c4c922d4d570a86b9c797db8c59194748595c03ea4739d20de3b'
WHERE building_id = 'SJTII_Terminal';

UPDATE c_order SET spatial_digest = '24d97489ec0b023bb1599be278bd5cf53b5cfe64d52bc88f8ae1788f0b00320e'
WHERE building_id = 'ST_SH';
