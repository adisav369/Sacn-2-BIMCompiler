-- Migration H0: ERP Dimensions — C_Campaign (Design Theme) + AD_User (SalesRep)
-- Applied to: library/BOM.db
-- Date: 2026-03-09

-- ── C_Campaign (Design Theme) ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS C_Campaign (
    C_Campaign_ID   TEXT PRIMARY KEY,
    Value           TEXT NOT NULL UNIQUE,
    Name            TEXT NOT NULL,
    Description     TEXT,
    IsActive        INTEGER DEFAULT 1,
    entity_type     TEXT DEFAULT 'D'
);

-- Seed: 4 themes derived from SH/DX material palettes
INSERT OR IGNORE INTO C_Campaign VALUES ('SCAN','Scandinavian','Scandinavian','Clean lines, light wood, minimal',1,'D');
INSERT OR IGNORE INTO C_Campaign VALUES ('TROP','Tropical','Tropical','Malaysian/SEA residential, ventilation-first',1,'D');
INSERT OR IGNORE INTO C_Campaign VALUES ('INST','Institutional','Institutional','Large-scale public/commercial',1,'D');
INSERT OR IGNORE INTO C_Campaign VALUES ('INDU','Industrial','Industrial','Exposed structure, utilitarian',1,'D');

-- ── AD_User (SalesRep / Project Handler) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS AD_User (
    AD_User_ID      INTEGER PRIMARY KEY,
    Name            TEXT NOT NULL,
    Value           TEXT NOT NULL UNIQUE,
    Email           TEXT,
    IsActive        INTEGER DEFAULT 1,
    entity_type     TEXT DEFAULT 'D'
);

-- Seed: 1 default user
INSERT OR IGNORE INTO AD_User VALUES (100,'System','System','system@bim-compiler.local',1,'D');

-- ── FK columns on C_DocType (template carries defaults) ──────────────────
-- ALTER TABLE is idempotent-safe: SQLite errors silently if column exists
ALTER TABLE C_DocType ADD COLUMN C_Campaign_ID TEXT REFERENCES C_Campaign(C_Campaign_ID);
ALTER TABLE C_DocType ADD COLUMN SalesRep_ID INTEGER REFERENCES AD_User(AD_User_ID);

-- Assign themes to existing DocTypes
UPDATE C_DocType SET C_Campaign_ID = 'SCAN' WHERE DocSubType = 'SH';
UPDATE C_DocType SET C_Campaign_ID = 'SCAN' WHERE DocSubType = 'DX';
UPDATE C_DocType SET C_Campaign_ID = 'TROP' WHERE DocSubType = 'TB';
UPDATE C_DocType SET C_Campaign_ID = 'INST' WHERE DocSubType = 'TE';
UPDATE C_DocType SET C_Campaign_ID = 'SCAN' WHERE DocSubType = 'ST';
UPDATE C_DocType SET SalesRep_ID = 100;
