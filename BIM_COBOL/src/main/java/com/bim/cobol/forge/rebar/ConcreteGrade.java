package com.bim.cobol.forge.rebar;

// Implementing FORGE_SUITE_SRS.md §10 Phase 7 — Witness: W-FORGE-9
public enum ConcreteGrade {
    GRADE_20(20), GRADE_25(25), GRADE_30(30), GRADE_35(35), GRADE_40(40), GRADE_45(45);

    private final int mpa;
    ConcreteGrade(int mpa) { this.mpa = mpa; }
    public int mpa() { return mpa; }

    public static ConcreteGrade fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
