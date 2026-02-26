-- Phase 122D: Profile-aware column sizing
-- Adds RC_600x300 and RC_800x450 column types + rule table for profile dispatch.
-- Idempotent: INSERT OR IGNORE for types, CREATE TABLE IF NOT EXISTS for rules.

-- New column types for institutional buildings
INSERT OR IGNORE INTO ad_column_type (column_type_id, construction, width_mm, depth_mm, ifc_class, description, is_active)
VALUES ('RC_600x300', 'REINFORCED_CONCRETE', 600, 300, 'IfcColumn', 'RC 600x300 institutional perimeter column', 1);

INSERT OR IGNORE INTO ad_column_type (column_type_id, construction, width_mm, depth_mm, ifc_class, description, is_active)
VALUES ('RC_800x450', 'REINFORCED_CONCRETE', 800, 450, 'IfcColumn', 'RC 800x450 institutional intermediate column', 1);

-- Rule table mirrors ad_beam_type_rule pattern
CREATE TABLE IF NOT EXISTS ad_column_type_rule (
    rule_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    context         TEXT NOT NULL,       -- CORNER, T_JUNCTION, INTERMEDIATE
    profile         TEXT DEFAULT NULL,   -- building profile (NULL=generic)
    column_type_id  TEXT NOT NULL REFERENCES ad_column_type,
    priority        INTEGER DEFAULT 100,
    description     TEXT,
    is_active       INTEGER DEFAULT 1,
    UNIQUE(context, profile)
);

-- Profile-specific rules for Malaysian Institutional (priority 10 = most specific)
INSERT OR IGNORE INTO ad_column_type_rule (context, profile, column_type_id, priority, description, is_active)
VALUES ('CORNER', 'Malaysian_Institutional', 'RC_600x300', 10, 'Institutional perimeter corner column', 1);

INSERT OR IGNORE INTO ad_column_type_rule (context, profile, column_type_id, priority, description, is_active)
VALUES ('T_JUNCTION', 'Malaysian_Institutional', 'RC_600x300', 10, 'Institutional perimeter T-junction column', 1);

INSERT OR IGNORE INTO ad_column_type_rule (context, profile, column_type_id, priority, description, is_active)
VALUES ('INTERMEDIATE', 'Malaysian_Institutional', 'RC_800x450', 10, 'Institutional grid intermediate column', 1);

-- Generic fallback rules (priority 100 = least specific, used when no profile match)
INSERT OR IGNORE INTO ad_column_type_rule (context, profile, column_type_id, priority, description, is_active)
VALUES ('CORNER', NULL, 'RC_300x300', 100, 'Default residential corner column', 1);

INSERT OR IGNORE INTO ad_column_type_rule (context, profile, column_type_id, priority, description, is_active)
VALUES ('T_JUNCTION', NULL, 'RC_300x300', 100, 'Default residential T-junction column', 1);

INSERT OR IGNORE INTO ad_column_type_rule (context, profile, column_type_id, priority, description, is_active)
VALUES ('INTERMEDIATE', NULL, 'RC_300x300', 100, 'Default residential grid column', 1);
