-- ============================================================================
-- Migration: c_order → C_DocType (absorb type-level config)
-- Target DB: library/BOM.db
-- Date: 2026-03-04
-- Principle: c_order in BOM.db is C_DocType domain config, not transactional.
--            Real C_Order is transactional → output.db only.
--            c_orderline in BOM.db is redundant → M_BOM + M_Product + component_library.
-- See: ConstructionAsERP.md §2, ERD (bim_architecture_viz.html)
-- ============================================================================

-- ── Step 1: Extend C_DocType with type-level config from c_order ────────────
-- These are constant per building type, not transactional.

ALTER TABLE C_DocType ADD COLUMN ProjectName      TEXT;           -- building instance name (was C_Order_ID)
ALTER TABLE C_DocType ADD COLUMN DSLContent        TEXT;           -- DSL template text
ALTER TABLE C_DocType ADD COLUMN OutputDbPath      TEXT;           -- output DB path
ALTER TABLE C_DocType ADD COLUMN ReferenceDbPath   TEXT;           -- reference DB for verification
ALTER TABLE C_DocType ADD COLUMN AabbWidthMm       REAL;           -- reference AABB envelope
ALTER TABLE C_DocType ADD COLUMN AabbDepthMm       REAL;
ALTER TABLE C_DocType ADD COLUMN AabbHeightMm      REAL;
ALTER TABLE C_DocType ADD COLUMN ExpectedElements  INTEGER;        -- expected element count
ALTER TABLE C_DocType ADD COLUMN Provenance        TEXT DEFAULT 'EXTRACTED';  -- EXTRACTED | GENERATIVE
ALTER TABLE C_DocType ADD COLUMN GeometryFailThreshold INTEGER DEFAULT 0;
ALTER TABLE C_DocType ADD COLUMN SeqNo             INTEGER DEFAULT 10;

-- ── Step 2: Backfill from c_order ───────────────────────────────────────────
-- c_order.C_DocType_ID = C_DocType.C_DocType_ID (1:1 mapping)

UPDATE C_DocType SET
    ProjectName     = (SELECT C_Order_ID        FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    DSLContent      = (SELECT DSLContent         FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    OutputDbPath    = (SELECT OutputDbPath        FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    ReferenceDbPath = (SELECT ReferenceDbPath     FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    AabbWidthMm     = (SELECT AabbWidthMm         FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    AabbDepthMm     = (SELECT AabbDepthMm         FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    AabbHeightMm    = (SELECT AabbHeightMm        FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    ExpectedElements= (SELECT ExpectedElements    FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    Provenance      = (SELECT Provenance           FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    GeometryFailThreshold = (SELECT GeometryFailThreshold FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID),
    SeqNo           = (SELECT SeqNo                FROM c_order WHERE c_order.C_DocType_ID = C_DocType.C_DocType_ID);

-- ── Step 3: Drop c_order (transactional C_Order → output.db only) ──────────
-- Transactional columns (DocStatus, SpatialDigest, EmptySpaceChecksum, C_BPartner_ID)
-- belong on C_Order in output.db, created fresh each compile.

DROP TABLE c_order;

-- ── Step 4: Drop c_orderline (redundant with M_BOM + M_Product) ────────────
-- Element definitions derivable from BOM explosion + component_library.
-- C_OrderLine generated at compile time in output.db.
-- See: Rosetta Stone §C_ORDERLINE SEPARATION

DROP TABLE c_orderline;

-- ── Verification ────────────────────────────────────────────────────────────
-- SELECT * FROM C_DocType;
-- .tables  (c_order and c_orderline should be gone)
