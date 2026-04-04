-- DV035: Verb pattern table for CLUSTER → verb assignment
-- Implementing BBC.md §4.2 Verb Selection — Witness: W-VERB-PATTERN
--
-- Maps (product_type, ifc_class) to an expected placement verb and grouping axis.
-- Mined from [PATRN] VERB CLUSTER fallback diagnostics on SH + DX buildings.
--
-- group_by semantics:
--   wall_axis   — elements arrayed along a wall (1D, X varies)
--   facade_axis — elements arrayed on building envelope (1D, irregular spacing)
--   Z           — elements stacked vertically (curtain panels)
--   axis        — elements along structural grid axis
--   grid        — elements at grid intersections (2D pattern)
--   none        — 3D scatter, no dominant axis (MEP fittings)
--
-- Applied to: ERP.db

CREATE TABLE IF NOT EXISTS ad_verb_pattern (
    pattern_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    product_type   TEXT NOT NULL,
    ifc_class      TEXT,
    expected_verb  TEXT NOT NULL,
    group_by       TEXT DEFAULT 'axis',
    priority       INTEGER DEFAULT 10,
    is_active      INTEGER DEFAULT 1,
    notes          TEXT
);

CREATE INDEX IF NOT EXISTS idx_verb_pattern_type
    ON ad_verb_pattern(product_type, ifc_class);

-- ═══════════════════════════════════════════════════════════════════
-- Seed rows: mined from [PATRN] VERB CLUSTER fallback diagnostics
-- Source buildings: SH (Schnellhaus), DX (Duplex)
-- ═══════════════════════════════════════════════════════════════════

INSERT OR IGNORE INTO ad_verb_pattern (product_type, ifc_class, expected_verb, group_by, priority, notes) VALUES
-- ARC: Furnishing — 1D array along wall
('FURNITURE',    'IfcFurnishingElement', 'LINE',    'wall_axis',   10, 'SH/DX cabinets: Y=1 unique pos, X varies along wall'),
-- ARC: Openings — 1D array on facade
('WINDOW',       'IfcWindow',           'LINE',    'facade_axis', 10, 'SH windows: irregular X on facade, 1D array'),
-- ARC: Envelope — panels stacked in Z
('CURTAIN_WALL', 'IfcCurtainWall',      'LINE',    'Z',           10, 'SH curtain wall: X=2 Y=1, panels stacked in Z'),
-- ARC: Openings — doors along corridor walls
('DOOR',         'IfcDoor',             'LINE',    'wall_axis',   20, 'Doors along corridor walls'),
-- MEP: Fittings — 3D scatter, handled by DISC path
('FITTING',      'IfcFlowFitting',      'CLUSTER', 'none',        50, 'DX bends/elbows: 3D scatter, Z>3m. MEP is DISC not BOM'),
-- MEP: Segments — handled by RouteWalker
('PIPE',         'IfcFlowSegment',      'CLUSTER', 'none',        50, 'DX pipes: handled by RouteWalker in ERP/DISC path'),
-- STR: Walls — tiling along grid lines
('WALL',         'IfcWall',             'TILE',    'axis',        30, 'Walls may tile along grid lines'),
-- STR: Columns — at grid intersections
('COLUMN',       'IfcColumn',           'FRAME',   'grid',        10, 'Columns at grid intersections'),
-- STR: Beams — along structural gridlines
('BEAM',         'IfcBeam',             'ROUTE',   'axis',        20, 'Beams along structural gridlines');
