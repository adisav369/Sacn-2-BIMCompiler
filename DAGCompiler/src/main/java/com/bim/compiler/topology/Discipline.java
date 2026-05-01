/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.topology;

/**
 * Construction disciplines extracted from enhanced_federation_GI.db
 * Query: SELECT DISTINCT discipline, COUNT(*) FROM elements_meta GROUP BY 1 ORDER BY 2 DESC;
 *
 * <p>AD_Org_ID values match ERP.db AD_Org table (DV013 migration).
 *
 * EXTRACTED - DO NOT INVENT ADDITIONAL DISCIPLINES
 */
// Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5 — Witness: W-DV-DISC-ORG
public enum Discipline {
    ARC (1, 35338),   // Architecture: Plates, Walls, Doors, Windows, Furniture
    FP  (3, 6884),    // Fire Protection: Pipes, Sprinklers, Alarms
    REB (9, 2660),    // Reinforcement: Reinforcing Bars
    ACMV(5, 1621),    // HVAC: Ducts, Air Terminals
    CW  (6, 1431),    // Cold Water (potable)
    STR (2, 1429),    // Structural: Beams, Columns, Slabs
    ELEC(4, 1172),    // Electrical: Light Fixtures, Appliances
    SP  (7, 979),     // Sanitary/Plumbing
    LPG (8, 209);     // Gas piping

    private final int adOrgId;
    private final int elementCount;

    Discipline(int adOrgId, int elementCount) {
        this.adOrgId = adOrgId;
        this.elementCount = elementCount;
    }

    /**
     * AD_Org_ID from ERP.db AD_Org table (DV013).
     * Use this for integer FK writes instead of TEXT name().
     */
    public int getAD_Org_ID() {
        return adOrgId;
    }

    /**
     * Element count from DB extraction (for reference/validation)
     */
    public int getElementCount() {
        return elementCount;
    }

    /**
     * Check if this is an MEP discipline (mechanical/electrical/plumbing)
     */
    public boolean isMEP() {
        return this == ACMV || this == FP || this == SP || this == CW || this == LPG || this == ELEC;
    }

    /**
     * Check if this is a structural discipline
     */
    public boolean isStructural() {
        return this == STR || this == REB;
    }

    /**
     * Convert from string to enum (backward-compatible TEXT accessor)
     * @param discipline discipline code string (e.g., "ARC", "FP")
     * @return corresponding enum value, or null if not found
     */
    public static Discipline fromString(String discipline) {
        if (discipline == null) return null;
        try {
            return valueOf(discipline.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Resolve AD_Org_ID integer to enum.
     * @param adOrgId AD_Org_ID from database
     * @return corresponding enum value, or null if not a known discipline
     */
    public static Discipline fromAD_Org_ID(int adOrgId) {
        for (Discipline d : values()) {
            if (d.adOrgId == adOrgId) return d;
        }
        return null;
    }
}
