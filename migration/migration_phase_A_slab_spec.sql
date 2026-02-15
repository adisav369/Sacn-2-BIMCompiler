-- Phase A: Slab specification metadata (EXTRACTED from Rosetta reference stones)
-- Stores per-building slab extend and thickness values for foundation and ceiling slabs.
-- extend_x_m / extend_y_m = distance from room bounds per side in each axis.
-- [EXTRACTED: Ifc4_SampleHouse reference stone]

CREATE TABLE IF NOT EXISTS ad_slab_spec (
    building_type  TEXT NOT NULL,       -- 'Ifc4_SampleHouse', 'Ifc2x3_Duplex', etc.
    slab_role      TEXT NOT NULL,       -- 'FOUNDATION', 'FLOOR', 'CEILING'
    extend_x_m     REAL NOT NULL DEFAULT 0.2,   -- slab extends room_bounds ± this in X per side
    extend_y_m     REAL NOT NULL DEFAULT 0.2,   -- slab extends room_bounds ± this in Y per side
    thickness_m    REAL,                -- slab thickness (NULL = use profile default)
    slab_name      TEXT,                -- element name for output
    ceiling_z_m    REAL,                -- for CEILING: Z of slab top above storey baseZ
    is_active      INTEGER DEFAULT 1,
    PRIMARY KEY (building_type, slab_role)
);

-- SampleHouse foundation slab: ref 16.8675 x 8.6675 x 0.470
-- Room bounds: X[0, 13.8], Y[0, 5.5]
-- extend_x = (16.8675 - 13.8) / 2 = 1.534, extend_y = (8.6675 - 5.5) / 2 = 1.584
INSERT OR REPLACE INTO ad_slab_spec (building_type, slab_role, extend_x_m, extend_y_m, thickness_m, slab_name)
VALUES ('Ifc4_SampleHouse', 'FOUNDATION', 1.534, 1.584, 0.470, 'Foundation Slab');

-- SampleHouse ceiling slab: ref 13.9675 x 5.7675 x 0.165 at z=2.335-2.500
-- extend_x = (13.9675 - 13.8) / 2 = 0.084, extend_y = (5.7675 - 5.5) / 2 = 0.134
-- ceiling_z_m = 2.5 (slab top position above baseZ)
INSERT OR REPLACE INTO ad_slab_spec (building_type, slab_role, extend_x_m, extend_y_m, thickness_m, slab_name, ceiling_z_m)
VALUES ('Ifc4_SampleHouse', 'CEILING', 0.084, 0.134, 0.165, 'Roof Structural Slab', 2.5);
