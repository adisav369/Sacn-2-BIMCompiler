-- DV003: Element MEP alias table — IFC version-agnostic product resolution
-- Maps real-world IFC class names, PredefinedTypes, and element name patterns
-- to canonical ad_element_mep.element_type values.
--
-- Resolution cascade (Java resolver tries in priority order):
--   priority 1: ifc_class        — exact IFC4 class match (e.g. IfcOutlet)
--   priority 2: predefined_type  — IFC4 PredefinedType enum (e.g. POWEROUTLET)
--   priority 3: type_class       — IFC2x3 Type object class (e.g. IfcOutletType)
--   priority 4: element_name     — LIKE pattern on element_name (last resort)
--
-- Implementing DISC_VALIDATION_DB_SRS.md §2.2 — Witness: W-DV-DB-ALIAS
-- APPEND-ONLY: never modify this migration after first run

CREATE TABLE IF NOT EXISTS ad_element_mep_alias (
    alias_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    canonical_type    TEXT NOT NULL REFERENCES ad_element_mep(element_type),
    match_field       TEXT NOT NULL CHECK(match_field IN (
                        'ifc_class', 'predefined_type', 'type_class', 'element_name')),
    match_value       TEXT NOT NULL,
    priority          INTEGER NOT NULL DEFAULT 10,
    source            TEXT,           -- provenance: 'IFC4_SPEC', 'DX_MINED', 'TE_MINED'
    notes             TEXT,
    is_active         INTEGER DEFAULT 1,
    UNIQUE(canonical_type, match_field, match_value)
);

CREATE INDEX IF NOT EXISTS idx_alias_match
    ON ad_element_mep_alias(match_field, match_value);

CREATE INDEX IF NOT EXISTS idx_alias_canonical
    ON ad_element_mep_alias(canonical_type);

-- ═══════════════════════════════════════════════════════════════════════
-- PRIORITY 1: IFC4 class — direct entity match (most reliable)
-- ═══════════════════════════════════════════════════════════════════════

INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('OUTLET',         'ifc_class', 'IfcOutlet',                    1, 'IFC4_SPEC', 'IFC4 direct subtype of IfcFlowTerminal'),
  ('SWITCH',         'ifc_class', 'IfcSwitchingDevice',           1, 'IFC4_SPEC', 'IFC4 direct subtype of IfcFlowController'),
  ('LIGHT',          'ifc_class', 'IfcLightFixture',              1, 'IFC4_SPEC', 'IFC4 direct subtype of IfcFlowTerminal'),
  ('PANEL',          'ifc_class', 'IfcDistributionBoard',         1, 'IFC4_SPEC', 'IFC4: was IfcElectricDistributionPoint in 2x3'),
  ('PANEL',          'ifc_class', 'IfcElectricDistributionBoard', 1, 'IFC4_SPEC', 'IFC4 alternate name'),
  ('TOILET',         'ifc_class', 'IfcSanitaryTerminal',          1, 'IFC4_SPEC', 'Disambiguated by PredefinedType: TOILETPAN/WCSEAT'),
  ('SINK',           'ifc_class', 'IfcSanitaryTerminal',          1, 'IFC4_SPEC', 'Disambiguated by PredefinedType: SINK/WASHHANDBASIN'),
  ('FLOOR_DRAIN',    'ifc_class', 'IfcSanitaryTerminal',          1, 'IFC4_SPEC', 'Disambiguated by PredefinedType: not yet standard'),
  ('SPRINKLER',      'ifc_class', 'IfcFireSuppressionTerminal',   1, 'IFC4_SPEC', 'IFC4 direct subtype of IfcFlowTerminal'),
  ('SMOKE_DETECTOR', 'ifc_class', 'IfcSensor',                    1, 'IFC4_SPEC', 'Disambiguated by PredefinedType: SMOKESENSOR'),
  ('SMOKE_DETECTOR', 'ifc_class', 'IfcAlarm',                     1, 'IFC4_SPEC', 'IFC4 alarm devices (TE uses this class)'),
  ('DIFFUSER',       'ifc_class', 'IfcAirTerminal',               1, 'IFC4_SPEC', 'IFC4 direct subtype of IfcFlowTerminal'),
  ('EXHAUST_FAN',    'ifc_class', 'IfcFan',                       1, 'IFC4_SPEC', 'IFC4 direct subtype of IfcFlowMovingDevice'),
  ('FP_RISER',       'ifc_class', 'IfcPipeSegment',               1, 'IFC4_SPEC', 'Disambiguated by system/discipline context');

-- ═══════════════════════════════════════════════════════════════════════
-- PRIORITY 2: PredefinedType — IFC4 standard enumerations
-- ═══════════════════════════════════════════════════════════════════════

-- IfcOutlet PredefinedTypes
INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('OUTLET',         'predefined_type', 'POWEROUTLET',          2, 'IFC4_SPEC', 'IfcOutletTypeEnum'),
  ('OUTLET',         'predefined_type', 'COMMUNICATIONSOUTLET', 2, 'IFC4_SPEC', 'IfcOutletTypeEnum — data/phone/network'),
  ('OUTLET',         'predefined_type', 'DATAOUTLET',           2, 'IFC4_SPEC', 'IfcOutletTypeEnum'),
  ('OUTLET',         'predefined_type', 'TELEPHONEOUTLET',      2, 'IFC4_SPEC', 'IfcOutletTypeEnum'),
  ('OUTLET',         'predefined_type', 'AUDIOVISUALOUTLET',    2, 'IFC4_SPEC', 'IfcOutletTypeEnum');

-- IfcSanitaryTerminal PredefinedTypes
INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('TOILET',         'predefined_type', 'TOILETPAN',            2, 'IFC4_SPEC', 'IfcSanitaryTerminalTypeEnum'),
  ('TOILET',         'predefined_type', 'WCSEAT',               2, 'IFC4_SPEC', 'IfcSanitaryTerminalTypeEnum'),
  ('TOILET',         'predefined_type', 'CISTERN',              2, 'IFC4_SPEC', 'IfcSanitaryTerminalTypeEnum'),
  ('SINK',           'predefined_type', 'SINK',                 2, 'IFC4_SPEC', 'IfcSanitaryTerminalTypeEnum'),
  ('SINK',           'predefined_type', 'WASHHANDBASIN',        2, 'IFC4_SPEC', 'IfcSanitaryTerminalTypeEnum');

-- IfcSensor PredefinedTypes
INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('SMOKE_DETECTOR', 'predefined_type', 'SMOKESENSOR',          2, 'IFC4_SPEC', 'IfcSensorTypeEnum'),
  ('SMOKE_DETECTOR', 'predefined_type', 'FIRESENSOR',           2, 'IFC4_SPEC', 'IfcSensorTypeEnum'),
  ('SMOKE_DETECTOR', 'predefined_type', 'HEATSENSOR',           2, 'IFC4_SPEC', 'IfcSensorTypeEnum');

-- IfcSwitchingDevice PredefinedTypes
INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('SWITCH',         'predefined_type', 'TOGGLESWITCH',         2, 'IFC4_SPEC', 'IfcSwitchingDeviceTypeEnum'),
  ('SWITCH',         'predefined_type', 'DIMMERSWITCH',         2, 'IFC4_SPEC', 'IfcSwitchingDeviceTypeEnum'),
  ('SWITCH',         'predefined_type', 'KEYPADSWITCH',         2, 'IFC4_SPEC', 'IfcSwitchingDeviceTypeEnum'),
  ('SWITCH',         'predefined_type', 'MOMENTARYSWITCH',      2, 'IFC4_SPEC', 'IfcSwitchingDeviceTypeEnum'),
  ('SWITCH',         'predefined_type', 'SELECTORSWITCH',       2, 'IFC4_SPEC', 'IfcSwitchingDeviceTypeEnum'),
  ('SWITCH',         'predefined_type', 'STARTER',              2, 'IFC4_SPEC', 'IfcSwitchingDeviceTypeEnum');

-- IfcFireSuppressionTerminal PredefinedTypes
INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('SPRINKLER',      'predefined_type', 'SPRINKLER',            2, 'IFC4_SPEC', 'IfcFireSuppressionTerminalTypeEnum'),
  ('SPRINKLER',      'predefined_type', 'SPRINKLERDEFLECTOR',   2, 'IFC4_SPEC', 'IfcFireSuppressionTerminalTypeEnum');

-- IfcAirTerminal PredefinedTypes
INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('DIFFUSER',       'predefined_type', 'DIFFUSER',             2, 'IFC4_SPEC', 'IfcAirTerminalTypeEnum'),
  ('DIFFUSER',       'predefined_type', 'GRILLE',               2, 'IFC4_SPEC', 'IfcAirTerminalTypeEnum'),
  ('DIFFUSER',       'predefined_type', 'REGISTER',             2, 'IFC4_SPEC', 'IfcAirTerminalTypeEnum');

-- ═══════════════════════════════════════════════════════════════════════
-- PRIORITY 3: IFC2x3 Type object classes
-- When element is IfcFlowTerminal but its Type is e.g. IfcOutletType
-- ═══════════════════════════════════════════════════════════════════════

INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('OUTLET',         'type_class', 'IfcOutletType',                    3, 'IFC2X3_SPEC', 'IfcRelDefinesByType → IfcOutletType'),
  ('SWITCH',         'type_class', 'IfcSwitchingDeviceType',           3, 'IFC2X3_SPEC', 'IfcRelDefinesByType → IfcSwitchingDeviceType'),
  ('LIGHT',          'type_class', 'IfcLightFixtureType',              3, 'IFC2X3_SPEC', 'IfcRelDefinesByType → IfcLightFixtureType'),
  ('PANEL',          'type_class', 'IfcElectricDistributionPointType', 3, 'IFC2X3_SPEC', 'IFC2x3 name for distribution board'),
  ('TOILET',         'type_class', 'IfcSanitaryTerminalType',          3, 'IFC2X3_SPEC', 'Needs PredefinedType for disambiguation'),
  ('SINK',           'type_class', 'IfcSanitaryTerminalType',          3, 'IFC2X3_SPEC', 'Needs PredefinedType for disambiguation'),
  ('SPRINKLER',      'type_class', 'IfcFireSuppressionTerminalType',   3, 'IFC2X3_SPEC', 'IfcRelDefinesByType'),
  ('SMOKE_DETECTOR', 'type_class', 'IfcSensorType',                    3, 'IFC2X3_SPEC', 'IfcRelDefinesByType → IfcSensorType'),
  ('SMOKE_DETECTOR', 'type_class', 'IfcAlarmType',                     3, 'IFC2X3_SPEC', 'IfcRelDefinesByType → IfcAlarmType'),
  ('DIFFUSER',       'type_class', 'IfcAirTerminalType',               3, 'IFC2X3_SPEC', 'IfcRelDefinesByType → IfcAirTerminalType'),
  ('EXHAUST_FAN',    'type_class', 'IfcFanType',                       3, 'IFC2X3_SPEC', 'IfcRelDefinesByType → IfcFanType'),
  ('FP_RISER',       'type_class', 'IfcPipeSegmentType',               3, 'IFC2X3_SPEC', 'IfcRelDefinesByType → IfcPipeSegmentType');

-- ═══════════════════════════════════════════════════════════════════════
-- PRIORITY 4: Element name patterns — mined from DX and TE reference
-- LIKE patterns: % = wildcard. Case-insensitive matching in Java.
-- ═══════════════════════════════════════════════════════════════════════

-- ── DX-mined (Ifc2x3 Duplex — Revit residential) ────────────────────

INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('OUTLET',         'element_name', '%Receptacle%',                 4, 'DX_MINED', 'M_Duplex Receptacle (47 in DX)'),
  ('OUTLET',         'element_name', '%Telephone Outlet%',           4, 'DX_MINED', 'M_Telephone Outlet (4 in DX)'),
  ('OUTLET',         'element_name', '%Telephone Terminal%',         4, 'DX_MINED', 'M_Telephone Terminal Board (1 in DX)'),
  ('SWITCH',         'element_name', '%Lighting Switch%',            4, 'DX_MINED', 'M_Lighting Switches (14 in DX)'),
  ('LIGHT',          'element_name', '%Pendant Light%',              4, 'DX_MINED', 'M_Pendant Light - Hemisphere (8 in DX)'),
  ('LIGHT',          'element_name', '%Sconce Light%',               4, 'DX_MINED', 'M_Sconce Light - Sphere (6 in DX)'),
  ('PANEL',          'element_name', '%Panelboard%',                 4, 'DX_MINED', 'M_Lighting and Appliance Panelboard (2 in DX)'),
  ('PANEL',          'element_name', '%Fire Alarm Control Panel%',   4, 'DX_MINED', 'M_Fire Alarm Control Panel (1 in DX)'),
  ('TOILET',         'element_name', '%Water Closet%',               4, 'DX_MINED', 'M_Water Closet - Flush Tank (4 in DX)'),
  ('SINK',           'element_name', '%Lavatory%',                   4, 'DX_MINED', 'M_Lavatory - Oval (6 in DX)'),
  ('SINK',           'element_name', '%Sink%',                       4, 'DX_MINED', 'M_Sink - Island - Single (2 in DX)'),
  ('SMOKE_DETECTOR', 'element_name', '%Smoke Detector%',             4, 'DX_MINED', 'M_Smoke Detector (6 in DX)');

-- ── TE-mined (IFC4 Terminal — Revit commercial) ──────────────────────

INSERT OR IGNORE INTO ad_element_mep_alias (canonical_type, match_field, match_value, priority, source, notes) VALUES
  ('SPRINKLER',      'element_name', '%sprinkler%head%',             4, 'TE_MINED', 'jkrME18_spr_sprinkler head (899 in TE)'),
  ('SPRINKLER',      'element_name', '%Hose Reel%',                  4, 'TE_MINED', 'AB_Hose Reel (10 in TE)'),
  ('DIFFUSER',       'element_name', '%diffuser%supply%',            4, 'TE_MINED', 'jkrME_air-tm_diffuser_supply (138 in TE)'),
  ('DIFFUSER',       'element_name', '%diffuser%return%',            4, 'TE_MINED', 'jkrME_air-tm_diffuser_return (29 in TE)'),
  ('DIFFUSER',       'element_name', '%diffuser%exhaust%',           4, 'TE_MINED', 'jkrME_air-tm_diffuser_exhaust (34 in TE)'),
  ('DIFFUSER',       'element_name', '%Grille%',                     4, 'TE_MINED', 'Ceiling Mounted Return Air Grille (31 in TE)'),
  ('DIFFUSER',       'element_name', '%Supply Diffuser%',            4, 'TE_MINED', 'Supply Diffuser with Plenum (27 in TE)'),
  ('DIFFUSER',       'element_name', '%air grill%',                  4, 'TE_MINED', 'M_Exhaust/Fresh air grill (25 in TE)'),
  ('SMOKE_DETECTOR', 'element_name', '%smoke detector%',             4, 'TE_MINED', 'jkrME18_fir-al_smoke detector (20 in TE)'),
  ('SMOKE_DETECTOR', 'element_name', '%heat detector%',              4, 'TE_MINED', 'jkrME18_fir-al_heat detector (3 in TE)'),
  ('SMOKE_DETECTOR', 'element_name', '%alarm bell%',                 4, 'TE_MINED', 'jkrME18_fir-al_alarm bell (17 in TE)'),
  ('SMOKE_DETECTOR', 'element_name', '%break glass%',                4, 'TE_MINED', 'jkrME18_fir-al_emergency break glass (17 in TE)'),
  ('LIGHT',          'element_name', '%E_Light%',                    4, 'TE_MINED', 'E_Light_* fixtures (814 in TE)'),
  ('LIGHT',          'element_name', '%Keluar Emergency%',           4, 'TE_MINED', 'E_Light_Keluar Emergency (38 in TE — exit sign)'),
  ('TOILET',         'element_name', '%WC%',                         4, 'TE_MINED', 'WC Toilet / WCPan (36 in TE)'),
  ('TOILET',         'element_name', '%Toilet%',                     4, 'TE_MINED', 'Toilet-Domestic-3D (16 in TE)'),
  ('TOILET',         'element_name', '%Urinal%',                     4, 'TE_MINED', 'Urinal_Floor_Mounted (22 in TE)'),
  ('SINK',           'element_name', '%sink%',                       4, 'TE_MINED', 'Various sinks (54 in TE)'),
  ('SINK',           'element_name', '%Countertop%',                 4, 'TE_MINED', '006_ADA_Countertop_and_Sink (17 in TE)'),
  ('FLOOR_DRAIN',    'element_name', '%Floor Trap%',                 4, 'TE_MINED', 'Floor Trap (46 in TE)'),
  ('FLOOR_DRAIN',    'element_name', '%Floor Gully%',                4, 'TE_MINED', 'Floor Gully Trap (10 in TE)'),
  ('FLOOR_DRAIN',    'element_name', '%Gully Trap%',                 4, 'TE_MINED', 'jkr15_ME_S_Gully Trap (8 in TE)');

-- ── Schema version update ────────────────────────────────────────────

INSERT OR REPLACE INTO AD_SysConfig (Name, Value, Description)
VALUES ('ALIAS_VERSION', 'DV003', 'Element MEP alias table seed');
