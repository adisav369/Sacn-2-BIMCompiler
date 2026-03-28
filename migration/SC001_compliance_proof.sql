-- SC001: Compliance proof chain database schema
-- Implementing STANDARDS_COMPLIANCE_SRS.md §4.1 — Witness: W-SC-SCHEMA
--
-- compliance_proof.db is a build artifact created by ComplianceStage (Step 11).
-- Contains signed proof chains linking AD_DocEvent_Rule verdicts to spatial data.
-- APPEND-ONLY: never modify this migration after first run.

-- ── SC_Jurisdiction — registry of supported jurisdictions ────────────

CREATE TABLE IF NOT EXISTS SC_Jurisdiction (
    SC_Jurisdiction_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    JurisdictionCode     TEXT UNIQUE NOT NULL,   -- MY, US, UK, SG, AU, UAE
    FullName             TEXT NOT NULL,
    Authority            TEXT NOT NULL,           -- CIDB, HSE, BCA, ABCB
    PrimaryCode          TEXT NOT NULL,           -- 'UBBL 1984'
    CurrentEdition       TEXT NOT NULL,           -- 'UBBL_1984_AMD2007'
    EffectiveDate        TEXT NOT NULL,           -- ISO date
    RuleCount            INTEGER NOT NULL DEFAULT 0,
    Status               TEXT NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE, DRAFT, PENDING
);

-- Seed MY jurisdiction
INSERT INTO SC_Jurisdiction (JurisdictionCode, FullName, Authority, PrimaryCode,
    CurrentEdition, EffectiveDate, RuleCount, Status)
VALUES ('MY', 'Malaysia', 'CIDB', 'UBBL 1984',
    'UBBL_1984_AMD2007', '2007-01-01', 8, 'ACTIVE');

-- ── SC_Run — one row per compilation + jurisdiction ──────────────────

CREATE TABLE IF NOT EXISTS SC_Run (
    SC_Run_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
    BuildingID        TEXT NOT NULL,
    Jurisdiction      TEXT NOT NULL,
    CodeEdition       TEXT NOT NULL,
    CompiledAt        TEXT NOT NULL,           -- ISO8601
    LibraryHash       TEXT NOT NULL,           -- SHA256 of component_library.db
    SpatialDigest     TEXT NOT NULL,           -- from DigestStage
    TotalRules        INTEGER NOT NULL,
    PassCount         INTEGER NOT NULL,
    BlockCount        INTEGER NOT NULL,
    WarnCount         INTEGER NOT NULL,
    SkipCount         INTEGER NOT NULL,
    OverallResult     TEXT NOT NULL,           -- PASS, BLOCK, PARTIAL
    CertificateID     TEXT UNIQUE              -- SC-{building}-{date} (NULL if BLOCK)
);

-- ── SC_Proof_Line — one row per rule per space ───────────────────────

CREATE TABLE IF NOT EXISTS SC_Proof_Line (
    SC_Proof_Line_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    SC_Run_ID         INTEGER NOT NULL,
    SpaceID           TEXT NOT NULL,           -- element GUID or 'BUILDING'
    AD_DocEvent_Rule_ID INTEGER NOT NULL,
    RuleCode          TEXT NOT NULL,           -- 'UBBL_S43_1_ROOM_AREA'
    StatuteRef        TEXT NOT NULL,           -- 'UBBL 1984 s.43(1)'
    ClauseText        TEXT NOT NULL,
    MeasuredValue     REAL NOT NULL,
    MeasuredUnit      TEXT NOT NULL,           -- mm, m2, count, ratio
    ThresholdValue    REAL NOT NULL,
    ThresholdDir      TEXT NOT NULL,           -- MIN or MAX
    Result            TEXT NOT NULL,           -- PASS, BLOCK, WARN, SKIP
    Margin            REAL NOT NULL,           -- measured - threshold (signed)
    ProofWitness      TEXT NOT NULL,           -- W-SC-* witness ID
    SkipReason        TEXT,                    -- upstream failure reason
    FOREIGN KEY (SC_Run_ID) REFERENCES SC_Run(SC_Run_ID)
);

CREATE INDEX IF NOT EXISTS idx_proof_line_run ON SC_Proof_Line(SC_Run_ID);
CREATE INDEX IF NOT EXISTS idx_proof_line_space ON SC_Proof_Line(SpaceID);
