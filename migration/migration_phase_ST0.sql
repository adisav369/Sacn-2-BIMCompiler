-- ============================================================================
-- Phase ST-0: Standard Mode Foundation + iDempiere Convention Retrofit
-- ============================================================================
-- Run: sqlite3 library/BOM.db < migration/migration_phase_ST0.sql
--
-- Steps:
--   1. Rename bom_owner → c_bpartner (m_bom, c_order)
--   2. Create C_BPartner lookup table + seed
--   3. Extend M_BomCategory (add Value, C_BPartner_ID columns)
--   4. Create M_BomCategoryLine template table + seed
--   5. Add AABB columns to c_order + backfill
--   6. Insert ST_SH c_order entry (is_active=0, dormant until ST-1)
-- ============================================================================

-- ── Step 1: Rename bom_owner → c_bpartner ──────────────────────────────────

ALTER TABLE m_bom  RENAME COLUMN bom_owner TO c_bpartner;
ALTER TABLE c_order RENAME COLUMN bom_owner TO c_bpartner;

-- ── Step 2: C_BPartner lookup table ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS C_BPartner (
    C_BPartner_ID   TEXT PRIMARY KEY,
    Value           TEXT NOT NULL UNIQUE,
    Name            TEXT NOT NULL,
    Description     TEXT,
    IsActive        INTEGER DEFAULT 1
);

INSERT OR IGNORE INTO C_BPartner VALUES ('SH','SampleHouse','Sample House','Single-storey residential extraction',1);
INSERT OR IGNORE INTO C_BPartner VALUES ('DX','Duplex','Duplex','Two-unit side-by-side extraction',1);
INSERT OR IGNORE INTO C_BPartner VALUES ('TB','TerraceBlock','Terrace Block','Malaysian single-storey terrace',1);
INSERT OR IGNORE INTO C_BPartner VALUES ('TE','Terminal','Terminal','Multi-storey institutional extraction',1);
INSERT OR IGNORE INTO C_BPartner VALUES ('MY','Malaysian','Malaysian Residential','Malaysian residential building pattern',1);
INSERT OR IGNORE INTO C_BPartner VALUES ('ST','Standard','Standard Mode','Owner-agnostic template-driven compilation',1);

-- ── Step 3: Extend M_BomCategory ───────────────────────────────────────────

ALTER TABLE M_BomCategory ADD COLUMN C_BPartner_ID TEXT REFERENCES C_BPartner(C_BPartner_ID);
ALTER TABLE M_BomCategory ADD COLUMN Value TEXT;

-- Backfill Value (iDempiere convention: CamelCase concatenated)
UPDATE M_BomCategory SET Value = 'Living'      WHERE M_BomCategory_ID = 'LI';
UPDATE M_BomCategory SET Value = 'Bedroom'     WHERE M_BomCategory_ID = 'BD';
UPDATE M_BomCategory SET Value = 'Kitchen'     WHERE M_BomCategory_ID = 'KT';
UPDATE M_BomCategory SET Value = 'Bathroom'    WHERE M_BomCategory_ID = 'BT';
UPDATE M_BomCategory SET Value = 'Dining'      WHERE M_BomCategory_ID = 'DN';
UPDATE M_BomCategory SET Value = 'Furniture'   WHERE M_BomCategory_ID = 'FR';
UPDATE M_BomCategory SET Value = 'Space'       WHERE M_BomCategory_ID = 'ST';
UPDATE M_BomCategory SET Value = 'Level1'      WHERE M_BomCategory_ID = 'L1';
UPDATE M_BomCategory SET Value = 'Level2'      WHERE M_BomCategory_ID = 'L2';
UPDATE M_BomCategory SET Value = 'Unit'        WHERE M_BomCategory_ID = 'UN';
UPDATE M_BomCategory SET Value = 'Wall'        WHERE M_BomCategory_ID = 'WL';
UPDATE M_BomCategory SET Value = 'Porch'       WHERE M_BomCategory_ID = 'PH';
UPDATE M_BomCategory SET Value = 'Roof'        WHERE M_BomCategory_ID = 'RF';
UPDATE M_BomCategory SET Value = 'Slab'        WHERE M_BomCategory_ID = 'SL';
UPDATE M_BomCategory SET Value = 'Pair'        WHERE M_BomCategory_ID = 'PR';
UPDATE M_BomCategory SET Value = 'HalfUnit'    WHERE M_BomCategory_ID = 'HU';
UPDATE M_BomCategory SET Value = 'MEP'         WHERE M_BomCategory_ID = 'MP';

-- New categories for ST template
INSERT OR IGNORE INTO M_BomCategory VALUES ('GF','Ground Floor','Habitable body between roof and slab',1,NULL,'GroundFloor');
INSERT OR IGNORE INTO M_BomCategory VALUES ('RE','Residential Template','Standard residential decomposition template',1,'ST','ResidentialTemplate');

-- ── Step 4: M_BomCategoryLine template table ───────────────────────────────

CREATE TABLE IF NOT EXISTS M_BomCategoryLine (
    M_BomCategoryLine_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    M_BomCategory_ID      TEXT NOT NULL REFERENCES M_BomCategory(M_BomCategory_ID),
    Child_BomCategory_ID  TEXT NOT NULL REFERENCES M_BomCategory(M_BomCategory_ID),
    Sequence              INTEGER NOT NULL DEFAULT 10,
    Value                 TEXT NOT NULL,
    Name                  TEXT NOT NULL,
    Z_Offset_Ratio        REAL DEFAULT 0.0,
    Z_Extent_Ratio        REAL DEFAULT 0.0,
    IsActive              INTEGER DEFAULT 1
);

-- Level 1: RE → {SL, GF, RF}
-- SH reference: total=3945mm, body≈3300mm (0.836), roof≈645mm (0.164)
INSERT INTO M_BomCategoryLine (M_BomCategory_ID, Child_BomCategory_ID, Sequence, Value, Name, Z_Offset_Ratio, Z_Extent_Ratio)
VALUES ('RE','SL',10,'FloorSlab','Floor Slab',0.0,0.0);

INSERT INTO M_BomCategoryLine (M_BomCategory_ID, Child_BomCategory_ID, Sequence, Value, Name, Z_Offset_Ratio, Z_Extent_Ratio)
VALUES ('RE','GF',20,'GroundFloor','Ground Floor Body',0.0,0.836);

INSERT INTO M_BomCategoryLine (M_BomCategory_ID, Child_BomCategory_ID, Sequence, Value, Name, Z_Offset_Ratio, Z_Extent_Ratio)
VALUES ('RE','RF',30,'Roof','Roof Assembly',0.836,0.164);

-- Level 2: GF → {LI, BD, DN, KT, BT}
-- Room Z = full body height, XY from BOM SpaceSize fit
INSERT INTO M_BomCategoryLine (M_BomCategory_ID, Child_BomCategory_ID, Sequence, Value, Name)
VALUES ('GF','LI',10,'Living','Living Room');

INSERT INTO M_BomCategoryLine (M_BomCategory_ID, Child_BomCategory_ID, Sequence, Value, Name)
VALUES ('GF','BD',20,'Bedroom','Bedroom');

INSERT INTO M_BomCategoryLine (M_BomCategory_ID, Child_BomCategory_ID, Sequence, Value, Name)
VALUES ('GF','DN',30,'Dining','Dining Room');

INSERT INTO M_BomCategoryLine (M_BomCategory_ID, Child_BomCategory_ID, Sequence, Value, Name)
VALUES ('GF','KT',40,'Kitchen','Kitchen');

INSERT INTO M_BomCategoryLine (M_BomCategory_ID, Child_BomCategory_ID, Sequence, Value, Name)
VALUES ('GF','BT',50,'Bathroom','Bathroom');

-- ── Step 5: AABB columns on c_order ────────────────────────────────────────

ALTER TABLE c_order ADD COLUMN aabb_width_mm  REAL;
ALTER TABLE c_order ADD COLUMN aabb_depth_mm  REAL;
ALTER TABLE c_order ADD COLUMN aabb_height_mm REAL;

-- Backfill from compiled output co_empty_space headers
UPDATE c_order SET aabb_width_mm = 16867.5, aabb_depth_mm = 8667.5,  aabb_height_mm = 3945.2
WHERE building_id = 'Ifc4_SampleHouse';

UPDATE c_order SET aabb_width_mm = 12372.7, aabb_depth_mm = 26730.8, aabb_height_mm = 7884.8
WHERE building_id = 'Ifc2x3_Duplex';

UPDATE c_order SET aabb_width_mm = 11530.0, aabb_depth_mm = 10360.0, aabb_height_mm = 5500.0
WHERE building_id = 'TB_LKTN';

-- TE (SJTII_Terminal) has no co_empty_space yet — leave NULL

-- ── Step 6: ST_SH c_order entry (dormant) ──────────────────────────────────

INSERT OR IGNORE INTO c_order (
    building_id, building_name, building_type,
    dsl_content, output_db_path,
    is_active, seq_no, c_bpartner,
    provenance, description,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm
) VALUES (
    'ST_SH', 'Standard Mode SH POC', 'RESIDENTIAL',
    '// ST_SH — Standard Mode POC using SH AABB
// Template-driven compilation: walks M_BomCategoryLine tree
// Must reproduce SH spatial digest via best-fit BOM selection
BUILDING "ST_SH" type:SINGLE_UNIT profile:"UK_Residential" {
    GRID {
        axes: A, B, C / 1, 2, 3
        spacing: 9.3, 4.5 / 2.0, 3.5
    }
    STOREY "Ground Floor" level:0 height:3.3m {
        LIVING "living" bounds:A1-B3 {
            exterior: west, north;
            WINDOW north; WINDOW north; WINDOW north
        }
        CORRIDOR "entrance" bounds:B1-C2 {
            adjacent: living; exterior: south;
            DOOR south type:D_EXT_DBL
        }
        BEDROOM "bedroom" bounds:B2-C3 {
            adjacent: entrance; exterior: north, east;
            WINDOW north
        }
    }
    ROOF pitch:0deg overhang:600mm
}',
    'DAGCompiler/lib/output/st_sh.db',
    0, 50, 'ST',
    'GENERATIVE', 'POC: ST mode reproducing SH via template-driven selection',
    16867.5, 8667.5, 3945.2
);
