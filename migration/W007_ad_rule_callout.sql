-- W007: AD_Rule callout table — reactive spatial editing
--
-- Session F (ProjectOrderBlueprint.md §9): DiffVerb + Callout.
--
-- AD_Rule: declarative callout rules that fire when a spatial field changes.
-- iDempiere parallel: AD_Column.Callout — field change triggers rule evaluation.
--
-- When a DiffVerb records a position change on a C_OrderLine, the CalloutEngine
-- queries AD_Rule for matching (source_table, source_column) rows and evaluates
-- them in dependency order (Kahn's topological sort, same as InferenceEngine).
--
-- Implementing ProjectOrderBlueprint.md §9.3 — Witness: W-DIFF-1

CREATE TABLE IF NOT EXISTS AD_Rule (
    ad_rule_id        INTEGER PRIMARY KEY,
    name              TEXT NOT NULL,
    description       TEXT,
    event_type        TEXT NOT NULL DEFAULT 'FIELD_CHANGE'
                      CHECK(event_type IN ('FIELD_CHANGE')),
    source_table      TEXT NOT NULL,           -- 'C_OrderLine'
    source_column     TEXT NOT NULL,           -- 'dx', 'dy', 'dz', 'm_product_id'
    rule_type         TEXT NOT NULL
                      CHECK(rule_type IN ('POSITIONAL','DIMENSIONAL','CONSTRAINT','REROUTE')),
    target_locator    TEXT,                    -- locator_ref of affected element (NULL = same element)
    expression        TEXT,                    -- formula or action: 'ROLLUP_AABB', 'target.dx = source.dx'
    depends_on        INTEGER REFERENCES AD_Rule(ad_rule_id),
    seq_no            INTEGER NOT NULL DEFAULT 10,
    is_active         INTEGER NOT NULL DEFAULT 1,
    pack_id           TEXT                     -- rule pack (MY, US, INTL) — nullable
);

CREATE INDEX IF NOT EXISTS idx_ad_rule_source
    ON AD_Rule(source_table, source_column, is_active);

CREATE INDEX IF NOT EXISTS idx_ad_rule_depends
    ON AD_Rule(depends_on);

-- Seed: Room AABB recalculation — the minimal first callout.
-- When any child element's dx/dy/dz changes, recalculate the parent room's AABB.
INSERT OR IGNORE INTO AD_Rule (ad_rule_id, name, description,
    event_type, source_table, source_column, rule_type,
    target_locator, expression, seq_no)
VALUES
    (1, 'ROOM_AABB_RECALC_DX', 'Recalculate room AABB when child dx changes',
     'FIELD_CHANGE', 'C_OrderLine', 'dx', 'DIMENSIONAL',
     NULL, 'ROLLUP_AABB', 10),
    (2, 'ROOM_AABB_RECALC_DY', 'Recalculate room AABB when child dy changes',
     'FIELD_CHANGE', 'C_OrderLine', 'dy', 'DIMENSIONAL',
     NULL, 'ROLLUP_AABB', 10),
    (3, 'ROOM_AABB_RECALC_DZ', 'Recalculate room AABB when child dz changes',
     'FIELD_CHANGE', 'C_OrderLine', 'dz', 'DIMENSIONAL',
     NULL, 'ROLLUP_AABB', 10);
