-- DV026: AD_DocEvent_Rule schema + 8 UBBL seed rules in ERP.db
-- Implementing DISC_VALIDATION_DB_SRS.md §10.4.3 — Witness: W-DOCEVENT-SCHEMA
--
-- 1st-stage DocEvent rules: government standards (NFPA 13, UBBL) fire automatically
-- during BOM walk when AD_Org matches. These live HERE, not in AD_Val_Rule (3rd stage).
-- APPEND-ONLY: never modify this migration after first run.

-- ── Schema ───────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS AD_DocEvent_Rule (
    AD_DocEvent_Rule_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    AD_Org_ID             INTEGER NOT NULL DEFAULT 0,
    Name                  TEXT NOT NULL,
    Description           TEXT,
    RuleType              TEXT NOT NULL,       -- SPACING, CONNECTIVITY, HOST,
                                               -- COMPLETENESS, DIMENSION, STANDARD
    StandardRef           TEXT,                -- 'UBBL 1984 s.43(1)'
    Jurisdiction          TEXT,                -- MY, US, UK, SG, NULL=universal
    CheckMethod           TEXT NOT NULL,       -- MIN_AREA, MIN_DIMENSION, MIN_WIDTH,
                                               -- MIN_HEIGHT, MAX_SPACING, MAX_DISTANCE
    IfcClass              TEXT,                -- target element (NULL=all in discipline)
    M_Product_Category_ID INTEGER,             -- FK to M_Product_Category, NULL=all
    Severity              TEXT NOT NULL DEFAULT 'WARN',
    FiringEvent           TEXT NOT NULL DEFAULT 'BEFORE_PLACE',
    IsActive              INTEGER NOT NULL DEFAULT 1,
    Provenance            TEXT,
    DependsOn             INTEGER DEFAULT 0,   -- FK to AD_DocEvent_Rule_ID (0=root)
    FOREIGN KEY (AD_Org_ID) REFERENCES AD_Org(AD_Org_ID)
);

CREATE TABLE IF NOT EXISTS AD_DocEvent_Rule_Param (
    AD_DocEvent_Rule_Param_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    AD_DocEvent_Rule_ID       INTEGER NOT NULL,
    Name                      TEXT NOT NULL,
    Value                     TEXT NOT NULL,
    ValueType                 TEXT DEFAULT 'NUM',   -- NUM, TEXT, BOOL
    ConditionExpr             TEXT,                  -- 'productCategory IN (BD,LR,DR)'
    FOREIGN KEY (AD_DocEvent_Rule_ID) REFERENCES AD_DocEvent_Rule(AD_DocEvent_Rule_ID)
);

CREATE INDEX IF NOT EXISTS idx_docevent_rule_org ON AD_DocEvent_Rule(AD_Org_ID);
CREATE INDEX IF NOT EXISTS idx_docevent_rule_jurisdiction ON AD_DocEvent_Rule(Jurisdiction);
CREATE INDEX IF NOT EXISTS idx_docevent_rule_param_fk ON AD_DocEvent_Rule_Param(AD_DocEvent_Rule_ID);

-- ── 8 UBBL Rules (EXTRACTED:UBBL_1984) ───────────────────────────────

-- Rule 1: UBBL s.43(1) — habitable room min area 9.3 m²
INSERT INTO AD_DocEvent_Rule (AD_Org_ID, Name, Description, RuleType, StandardRef,
    Jurisdiction, CheckMethod, IfcClass, Severity, FiringEvent, IsActive, Provenance)
VALUES (0, 'UBBL_S43_1_ROOM_AREA',
    'Every habitable room shall have a floor area of not less than 9.3 square metres',
    'DIMENSION', 'UBBL 1984 s.43(1)', 'MY', 'MIN_AREA', 'IfcSpace',
    'BLOCK', 'BEFORE_COMPILE', 1, 'EXTRACTED:UBBL_1984');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType, ConditionExpr)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S43_1_ROOM_AREA'),
    'min_area_m2', '9.3', 'NUM', 'productCategory IN (BD,LR,DR,FR)');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S43_1_ROOM_AREA'),
    'clause_text', 'Habitable room min floor area 9.3 m2', 'TEXT');

-- Rule 2: UBBL s.43(2) — kitchen min dimension 1.8 m
INSERT INTO AD_DocEvent_Rule (AD_Org_ID, Name, Description, RuleType, StandardRef,
    Jurisdiction, CheckMethod, IfcClass, Severity, FiringEvent, IsActive, Provenance)
VALUES (0, 'UBBL_S43_2_KITCHEN_DIM',
    'Every kitchen shall have a minimum dimension of not less than 1.8 metres',
    'DIMENSION', 'UBBL 1984 s.43(2)', 'MY', 'MIN_DIMENSION', 'IfcSpace',
    'BLOCK', 'BEFORE_COMPILE', 1, 'EXTRACTED:UBBL_1984');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType, ConditionExpr)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S43_2_KITCHEN_DIM'),
    'min_dimension_mm', '1800', 'NUM', 'productCategory IN (KT)');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S43_2_KITCHEN_DIM'),
    'clause_text', 'Kitchen min dimension 1.8 m', 'TEXT');

-- Rule 3: UBBL s.38(1) — corridor width 900 mm
INSERT INTO AD_DocEvent_Rule (AD_Org_ID, Name, Description, RuleType, StandardRef,
    Jurisdiction, CheckMethod, IfcClass, Severity, FiringEvent, IsActive, Provenance)
VALUES (0, 'UBBL_S38_1_CORRIDOR_WIDTH',
    'Every corridor shall be not less than 900 millimetres in width',
    'DIMENSION', 'UBBL 1984 s.38(1)', 'MY', 'MIN_WIDTH', 'IfcSpace',
    'BLOCK', 'BEFORE_COMPILE', 1, 'EXTRACTED:UBBL_1984');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType, ConditionExpr)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S38_1_CORRIDOR_WIDTH'),
    'min_width_mm', '900', 'NUM', 'productCategory IN (CR)');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S38_1_CORRIDOR_WIDTH'),
    'clause_text', 'Corridor min width 900 mm', 'TEXT');

-- Rule 4: UBBL s.38(2) — staircase width 900 mm
INSERT INTO AD_DocEvent_Rule (AD_Org_ID, Name, Description, RuleType, StandardRef,
    Jurisdiction, CheckMethod, IfcClass, Severity, FiringEvent, IsActive, Provenance)
VALUES (0, 'UBBL_S38_2_STAIRCASE_WIDTH',
    'Every staircase shall be not less than 900 millimetres in width',
    'DIMENSION', 'UBBL 1984 s.38(2)', 'MY', 'MIN_WIDTH', 'IfcSpace',
    'BLOCK', 'BEFORE_COMPILE', 1, 'EXTRACTED:UBBL_1984');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType, ConditionExpr)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S38_2_STAIRCASE_WIDTH'),
    'min_width_mm', '900', 'NUM', 'productCategory IN (ST)');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S38_2_STAIRCASE_WIDTH'),
    'clause_text', 'Staircase min width 900 mm', 'TEXT');

-- Rule 5: UBBL s.31 — ceiling height habitable room 2600 mm
INSERT INTO AD_DocEvent_Rule (AD_Org_ID, Name, Description, RuleType, StandardRef,
    Jurisdiction, CheckMethod, IfcClass, Severity, FiringEvent, IsActive, Provenance)
VALUES (0, 'UBBL_S31_CEILING_HEIGHT',
    'The height of every habitable room shall be not less than 2.6 metres',
    'DIMENSION', 'UBBL 1984 s.31', 'MY', 'MIN_HEIGHT', 'IfcSpace',
    'BLOCK', 'BEFORE_COMPILE', 1, 'EXTRACTED:UBBL_1984');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType, ConditionExpr)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S31_CEILING_HEIGHT'),
    'min_height_mm', '2600', 'NUM', 'productCategory IN (BD,LR,DR,FR,KT)');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S31_CEILING_HEIGHT'),
    'clause_text', 'Habitable room min ceiling height 2.6 m', 'TEXT');

-- Rule 6: UBBL s.39 — bathroom area min 1.5 m²
INSERT INTO AD_DocEvent_Rule (AD_Org_ID, Name, Description, RuleType, StandardRef,
    Jurisdiction, CheckMethod, IfcClass, Severity, FiringEvent, IsActive, Provenance)
VALUES (0, 'UBBL_S39_BATHROOM_AREA',
    'Every bathroom shall have a floor area of not less than 1.5 square metres',
    'DIMENSION', 'UBBL 1984 s.39', 'MY', 'MIN_AREA', 'IfcSpace',
    'BLOCK', 'BEFORE_COMPILE', 1, 'EXTRACTED:UBBL_1984');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType, ConditionExpr)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S39_BATHROOM_AREA'),
    'min_area_m2', '1.5', 'NUM', 'productCategory IN (BT)');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S39_BATHROOM_AREA'),
    'clause_text', 'Bathroom min floor area 1.5 m2', 'TEXT');

-- Rule 7: UBBL Part VII — sprinkler spacing 4600 mm max (FP discipline)
INSERT INTO AD_DocEvent_Rule (AD_Org_ID, Name, Description, RuleType, StandardRef,
    Jurisdiction, CheckMethod, IfcClass, Severity, FiringEvent, IsActive, Provenance)
VALUES (3, 'UBBL_PVII_SPRINKLER_SPACING',
    'Maximum sprinkler spacing shall not exceed 4.6 metres',
    'SPACING', 'UBBL 1984 Part VII', 'MY', 'MAX_SPACING', 'IfcFireSuppressionTerminal',
    'BLOCK', 'BEFORE_COMPILE', 1, 'EXTRACTED:UBBL_1984');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_PVII_SPRINKLER_SPACING'),
    'max_spacing_mm', '4600', 'NUM');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_PVII_SPRINKLER_SPACING'),
    'clause_text', 'Sprinkler max spacing 4.6 m', 'TEXT');

-- Rule 8: UBBL s.162 — egress travel distance 30 m
INSERT INTO AD_DocEvent_Rule (AD_Org_ID, Name, Description, RuleType, StandardRef,
    Jurisdiction, CheckMethod, IfcClass, Severity, FiringEvent, IsActive, Provenance)
VALUES (0, 'UBBL_S162_EGRESS_DISTANCE',
    'Maximum travel distance to nearest exit shall not exceed 30 metres',
    'STANDARD', 'UBBL 1984 s.162', 'MY', 'MAX_DISTANCE', NULL,
    'BLOCK', 'BEFORE_COMPILE', 1, 'EXTRACTED:UBBL_1984');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S162_EGRESS_DISTANCE'),
    'max_distance_mm', '30000', 'NUM');
INSERT INTO AD_DocEvent_Rule_Param (AD_DocEvent_Rule_ID, Name, Value, ValueType)
VALUES ((SELECT AD_DocEvent_Rule_ID FROM AD_DocEvent_Rule WHERE Name = 'UBBL_S162_EGRESS_DISTANCE'),
    'clause_text', 'Max travel distance to exit 30 m', 'TEXT');

-- ── Schema version ───────────────────────────────────────────────────
INSERT OR REPLACE INTO AD_SysConfig (Value, Name, Description, updated)
VALUES ('DOCEVENT_RULES_VERSION', 'DV026', 'AD_DocEvent_Rule schema + 8 UBBL rules', datetime('now'));
