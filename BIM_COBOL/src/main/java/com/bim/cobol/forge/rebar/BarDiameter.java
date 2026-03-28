package com.bim.cobol.forge.rebar;

// MS 146 / BS 4449 standard rebar diameters
public enum BarDiameter {
    T6(6), T8(8), T10(10), T12(12), T16(16), T20(20), T25(25), T32(32), T40(40);

    private final int mm;
    BarDiameter(int mm) { this.mm = mm; }
    public int mm() { return mm; }

    /** Cross-sectional area in mm² */
    public double areaMm2() { return Math.PI * (mm / 2.0) * (mm / 2.0); }

    /** Weight in kg per metre (steel density 7850 kg/m³) */
    public double weightKgPerM() { return areaMm2() * 0.00000785 * 1000; }
}
