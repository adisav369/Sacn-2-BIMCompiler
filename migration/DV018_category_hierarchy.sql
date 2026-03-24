-- DV018: M_Product_Category hierarchy — cascade levels from MANIFESTO
-- Implementing DATA_MODEL.md §7 — M_Product_Category hierarchy
-- Source: DISTINCT m_product_category_id from all BOM databases + MANIFESTO cascade model

-- ============================================================
-- Top-level building types (NULL parent)
-- ============================================================
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('RE', 'Residential',       NULL, 10, 1),
  ('IN', 'Infrastructure',    NULL, 20, 1),
  ('CO', 'Commercial',        NULL, 30, 1),
  ('IP', 'Industrial Plant',  NULL, 40, 1);

-- ============================================================
-- Floor-level categories (parent = RE — residential / generic floors)
-- ============================================================
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('GF',    'Ground Floor',              'RE', 100, 1),
  ('L0',    'Level 0',                   'RE', 101, 1),
  ('L1',    'Level 1',                   'RE', 110, 1),
  ('L1A',   'Level 1A',                  'RE', 111, 1),
  ('L1B',   'Level 1B',                  'RE', 112, 1),
  ('L2',    'Level 2',                   'RE', 120, 1),
  ('L2A',   'Level 2A',                  'RE', 121, 1),
  ('L3',    'Level 3',                   'RE', 130, 1),
  ('L4',    'Level 4',                   'RE', 140, 1),
  ('L5',    'Level 5',                   'RE', 150, 1),
  ('L6',    'Level 6',                   'RE', 160, 1),
  ('RF',    'Roof Floor',               'RE', 200, 1),
  ('ROOF',  'Roof',                      'RE', 201, 1),
  ('FN',    'Penthouse',                 'RE', 210, 1),
  ('FDN',   'Foundation',               'RE', 220, 1),
  ('BSMT',  'Basement',                 'RE', 230, 1),
  ('ATTIC', 'Attic',                    'RE', 240, 1),
  ('FLOO',  'Floor',                    'RE', 250, 1),
  ('CEIL',  'Ceiling',                  'RE', 260, 1),
  ('VUND',  'Lower Ground',            'RE', 270, 1),
  ('OBER',  'Upper Floor',             'RE', 280, 1),
  ('MISC',  'Miscellaneous',           'RE', 900, 1);

-- German floor names (parent = RE — from German residential buildings)
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('EG',    'Ground Floor (Erdgeschoss)',     'RE', 300, 1),
  ('DG',    'Attic Floor (Dachgeschoss)',     'RE', 310, 1),
  ('OG1',   'Upper Floor 1 (Obergeschoss)',   'RE', 320, 1),
  ('OG2',   'Upper Floor 2 (Obergeschoss)',   'RE', 330, 1),
  ('KE',    'Basement (Keller)',              'RE', 340, 1),
  ('U.ET',  'Lower Level (Unteretage)',       'RE', 350, 1);

-- Norwegian floor names (parent = RE — from Norwegian residential buildings)
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('KJEL',  'Basement (Kjeller)',             'RE', 360, 1);

-- Miscellaneous floor-level codes (parent = RE)
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('LM1',   'Mezzanine Level 1',        'RE', 370, 1),
  ('T1',    'Terrace 1',                'RE', 380, 1),
  ('T.O.',  'Top of Structure',         'RE', 390, 1);

-- ============================================================
-- Room-level categories (NULL parent — rooms span any floor)
-- ============================================================
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('LIVING',      'Living Room',        NULL, 500, 1),
  ('KITCHEN',     'Kitchen',            NULL, 510, 1),
  ('KITCHEN_A',   'Kitchen A',          NULL, 511, 1),
  ('KITCHEN_B',   'Kitchen B',          NULL, 512, 1),
  ('BEDROOM',     'Bedroom',            NULL, 520, 1),
  ('BATHROOM',    'Bathroom',           NULL, 530, 1),
  ('DINING',      'Dining Room',        NULL, 540, 1),
  ('MASTER',      'Master Bedroom',     NULL, 550, 1),
  ('CORRIDOR',    'Corridor',           NULL, 560, 1),
  ('OFFICE',      'Office',             NULL, 570, 1),
  ('WARDROBE',    'Wardrobe',           NULL, 580, 1),
  ('WC',          'Water Closet',       NULL, 590, 1),
  ('GALLERY',     'Gallery',            NULL, 600, 1),
  ('LABORATORY',  'Laboratory',         NULL, 610, 1),
  ('MECHANICAL',  'Mechanical Room',    NULL, 620, 1),
  ('MEETING',     'Meeting Room',       NULL, 630, 1),
  ('SEMINAR',     'Seminar Room',       NULL, 640, 1),
  ('PR',          'Pantry',             NULL, 650, 1),
  ('HU',          'Utility Room',       NULL, 660, 1);

-- ============================================================
-- Infrastructure segment categories (parent = IN)
-- ============================================================
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('SUP',   'Support',            'IN', 700, 1),
  ('DCK',   'Deck',               'IN', 710, 1),
  ('ABT',   'Abutment',           'IN', 720, 1),
  ('TRK',   'Track',              'IN', 730, 1),
  ('ROAD',  'Road',               'IN', 740, 1),
  ('RAIL',  'Rail',               'IN', 750, 1),
  ('GEO',   'Geotechnical',       'IN', 760, 1),
  ('APR',   'Approach',           'IN', 770, 1),
  ('PIR',   'Pier',               'IN', 780, 1),
  ('PKG',   'Package',            'IN', 790, 1),
  ('SIGN',  'Signage',            'IN', 800, 1);

-- ============================================================
-- Cross-domain categories (NULL parent — span RE and IN)
-- ============================================================
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('MS',    'Mezzanine Structure',  NULL, 810, 1),
  ('CW',    'Curtain Wall',         NULL, 820, 1);

-- ============================================================
-- Data-quality anomalies (NULL parent — registered for referential integrity)
-- ============================================================
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Parent_Category_ID, SeqNo, IsActive)
VALUES
  ('-1_-',  'Unknown Category (-1)',   NULL, 990, 1),
  ('-1_F',  'Unknown Floor (-1_F)',    NULL, 991, 1),
  ('6_-_',  'Unknown Category (6)',    NULL, 992, 1);
