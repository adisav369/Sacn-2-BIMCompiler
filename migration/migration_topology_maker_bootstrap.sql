-- =============================================================================
-- TopologyMaker Bootstrap Migration
-- Phase T0: tables + atoms-to-rooms catalog seeds
-- ADD-ONLY. Never modifies existing rows. SpatialDigests unchanged.
-- =============================================================================

-- PART 1: New tables

CREATE TABLE IF NOT EXISTS ad_typology_template (
    typology_id   TEXT PRIMARY KEY,
    typology_name TEXT NOT NULL,
    grid_strategy TEXT NOT NULL CHECK(grid_strategy IN ('STRIP_ZONES','COURTYARD','LINEAR')),
    ubbl_class    TEXT NOT NULL,
    description   TEXT,
    base_width_mm INTEGER NOT NULL,
    base_depth_mm INTEGER NOT NULL,
    zone_json     TEXT NOT NULL,
    is_active     INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS ad_ubbl_rule (
    rule_id        TEXT PRIMARY KEY,
    rule_name      TEXT NOT NULL,
    room_type      TEXT,
    constraint_key TEXT NOT NULL CHECK(constraint_key IN ('AREA','MIN_DIM','CEILING_MM','NOT_ADJACENT')),
    min_value_mm   REAL,
    ubbl_ref       TEXT,
    is_active      INTEGER NOT NULL DEFAULT 1
);

-- PART 2: Seed TERRACE_MY_1S typology (extracted fractions from TB-LKTN grid)

INSERT OR IGNORE INTO ad_typology_template VALUES (
    'TERRACE_MY_1S',
    'Single Storey Terrace — Malaysia',
    'STRIP_ZONES',
    'RESIDENTIAL_TYPE_C',
    '3BR terrace. Wet strip left (13%), spine centre (37%), sleeping right (50%). Porch front (27%).',
    9900,
    8500,
    '[{"zone":"PORCH",    "depth_frac":0.271, "x_from":0.132, "x_to":0.687, "room_type":"PORCH"},
      {"zone":"WET_BATH", "x_to":0.132, "y_from":0.824, "y_to":1.0,  "room_type":"BATHROOM"},
      {"zone":"WET_TOI",  "x_to":0.132, "y_from":0.635, "y_to":0.824,"room_type":"TOILET"},
      {"zone":"BED_L",    "x_to":0.313, "y_from":0.271, "y_to":1.0,  "room_type":"BEDROOM"},
      {"zone":"COMMON",   "x_from":0.313,"x_to":0.687,"y_from":0.271,"y_to":1.0,"room_type":"COMMON"},
      {"zone":"BED_R1",   "x_from":0.687,"y_from":0.271,"y_to":0.635,"room_type":"BEDROOM"},
      {"zone":"BED_R2",   "x_from":0.687,"y_from":0.635,"y_to":1.0,  "room_type":"BEDROOM"}]',
    1
);

-- PART 3: UBBL constraints (Malaysian UBBL 1984)

INSERT OR IGNORE INTO ad_ubbl_rule VALUES
  ('UBBL_BED_AREA',  'Bedroom min area',    'BEDROOM',  'AREA',    9290, 'UBBL_1984_S51', 1),
  ('UBBL_BATH_AREA', 'Bathroom min area',   'BATHROOM', 'AREA',    2500, 'UBBL_1984_S56', 1),
  ('UBBL_TOI_AREA',  'Toilet min area',     'TOILET',   'AREA',    1500, 'UBBL_1984_S56', 1),
  ('UBBL_BED_DIM',   'Bedroom min dim',     'BEDROOM',  'MIN_DIM', 2700, 'UBBL_1984_S51', 1),
  ('UBBL_CEIL',      'Min ceiling height',  NULL,       'CEILING_MM',2400,NULL,            1);

-- PART 4: Wall prefab BOMs (Layer 2 — standard MY exterior wall panels)

INSERT OR IGNORE INTO ad_bom(bom_id,bom_name,description,bom_type,group_by,is_active) VALUES
  ('WALL_EXT_MY_150_SOLID',
   'Ext Wall 150mm Solid (MY)',
   'No openings — party/utility faces.',
   'SET','BUILDING',1),
  ('WALL_EXT_MY_150_WIN_STD',
   'Ext Wall 150mm + Std Window (MY)',
   'WINDOW_W1 centred, 900mm sill height.',
   'SET','BUILDING',1),
  ('WALL_EXT_MY_150_WIN_WIDE',
   'Ext Wall 150mm + Wide Window (MY)',
   'WINDOW_W2 centred, 900mm sill height.',
   'SET','BUILDING',1);

-- Wall children: SOLID
INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dz,fit_priority,is_active)
VALUES ('WALL_EXT_MY_150_SOLID','WALL_BODY',10,NULL,'PARALLEL_TO_WALL',0,10,1);

-- Wall children: WIN_STD (WINDOW_W1 = 1.2×0.1×1.2m)
INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dz,fit_priority,is_active)
VALUES
  ('WALL_EXT_MY_150_WIN_STD','WALL_BODY', 10,NULL,        'PARALLEL_TO_WALL',0,   0,  10,1),
  ('WALL_EXT_MY_150_WIN_STD','WIN_CENTRE',20,'WINDOW_W1','FACE_OUTSIDE',     0, 900,  20,1);

-- Wall children: WIN_WIDE (WINDOW_W2 — bedroom scale)
INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dz,fit_priority,is_active)
VALUES
  ('WALL_EXT_MY_150_WIN_WIDE','WALL_BODY', 10,NULL,        'PARALLEL_TO_WALL',0,  0,  10,1),
  ('WALL_EXT_MY_150_WIN_WIDE','WIN_CENTRE',20,'WINDOW_W2','FACE_OUTSIDE',     0,900,  20,1);

-- PART 5: WARDROBE_SET children (fill existing empty BOM)

INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dz,fit_priority,is_active)
VALUES
  ('WARDROBE_SET','WARDROBE_A',10,'FURN_WARDROBE','FACE_INTO_ROOM', 0,   0, 10, 1),
  ('WARDROBE_SET','WARDROBE_B',20,'FURN_WARDROBE','FACE_INTO_ROOM', 1.3, 0, 20, 1);

-- PART 6: Room prefab BOMs (Layer 3 — room modules)

INSERT OR IGNORE INTO ad_bom(bom_id,bom_name,description,bom_type,group_by,is_active) VALUES
  ('BEDROOM_PREFAB_MY_3100',
   'Bedroom 3.1×3.1m — MY Terrace',
   'Exterior window wall (N face) + bedroom door (W face) + king bed set + ceiling lamp. UBBL-compliant 9.61m².',
   'ROOM','ROOM',1),
  ('LIVING_PREFAB_MY',
   'Living/Common — MY Terrace',
   'Window wall + living set + 2 ceiling lamps. Common zone for terrace units.',
   'ROOM','ROOM',1),
  ('BATHROOM_PREFAB_MY',
   'Bathroom — MY Terrace',
   'Sanitary block + vanity + ceiling lamp. UBBL-compliant 2.5m².',
   'ROOM','ROOM',1),
  ('PORCH_MODULE_MY',
   'Porch Module — MY Terrace',
   'Solid front wall + exterior ceiling lamp.',
   'ROOM','ROOM',1);

-- BEDROOM_PREFAB_MY_3100 children
INSERT OR IGNORE INTO ad_bom_child(bom_id,child_bom_id,role,sequence,rotation_rule,fit_priority,min_space_mm,is_active)
VALUES('BEDROOM_PREFAB_MY_3100','WALL_EXT_MY_150_WIN_STD','WALL_EXT',10,'FACE_OUTSIDE',10,0,1);

INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dz,fit_priority,is_active)
VALUES('BEDROOM_PREFAB_MY_3100','DOOR_ENTRY',20,'DOOR_D2','FACE_INTO_ROOM',0.4,0,10,1);

INSERT OR IGNORE INTO ad_bom_child(bom_id,child_bom_id,role,sequence,rotation_rule,fit_priority,min_space_mm,is_active)
VALUES('BEDROOM_PREFAB_MY_3100','BED_SET_MASTER','FURNITURE',30,'FACE_AWAY_FROM_WALL',20,2000,1);

INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dy,dz,fit_priority,is_active)
VALUES('BEDROOM_PREFAB_MY_3100','LIGHT',40,'ELEC_LIGHT','0',1.55,1.55,-0.3,30,1);

-- LIVING_PREFAB_MY children
INSERT OR IGNORE INTO ad_bom_child(bom_id,child_bom_id,role,sequence,rotation_rule,fit_priority,min_space_mm,is_active)
VALUES('LIVING_PREFAB_MY','WALL_EXT_MY_150_WIN_STD','WALL_EXT',10,'FACE_OUTSIDE',10,0,1);

INSERT OR IGNORE INTO ad_bom_child(bom_id,child_bom_id,role,sequence,rotation_rule,fit_priority,min_space_mm,is_active)
VALUES('LIVING_PREFAB_MY','LIVING_SET','FURNITURE',20,'FACE_AWAY_FROM_WALL',20,3000,1);

INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dy,dz,fit_priority,is_active)
VALUES('LIVING_PREFAB_MY','LIGHT_1',30,'ELEC_LIGHT','0',1.2,1.2,-0.3,30,1);

INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dy,dz,fit_priority,is_active)
VALUES('LIVING_PREFAB_MY','LIGHT_2',40,'ELEC_LIGHT','0',2.5,1.2,-0.3,30,1);

-- BATHROOM_PREFAB_MY children
INSERT OR IGNORE INTO ad_bom_child(bom_id,child_bom_id,role,sequence,rotation_rule,fit_priority,min_space_mm,is_active)
VALUES('BATHROOM_PREFAB_MY','TOILET_BLOCK_FIXTURES','SANITARY',10,'FACE_AWAY_FROM_WALL',10,1200,1);

INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dz,fit_priority,is_active)
VALUES('BATHROOM_PREFAB_MY','LIGHT',20,'ELEC_LIGHT','0',0.75,-0.3,30,1);

-- PORCH_MODULE_MY children
INSERT OR IGNORE INTO ad_bom_child(bom_id,child_bom_id,role,sequence,rotation_rule,fit_priority,is_active)
VALUES('PORCH_MODULE_MY','WALL_EXT_MY_150_SOLID','WALL_SOUTH',10,'FACE_OUTSIDE',10,1);

INSERT OR IGNORE INTO ad_bom_child(bom_id,role,sequence,product_ref,rotation_rule,dx,dy,dz,fit_priority,is_active)
VALUES('PORCH_MODULE_MY','LIGHT',20,'ELEC_LIGHT','0',2.75,1.15,-0.3,20,1);
