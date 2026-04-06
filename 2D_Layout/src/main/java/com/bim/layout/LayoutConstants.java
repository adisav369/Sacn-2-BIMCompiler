package com.bim.layout;

/**
 * Style constants for architectural drawings — line weights, colours, scales.
 *
 * Profile: JKR Malaysian (TB-LKTN standard drawing practice).
 * Future: these become rows in ad_drawing_style metadata table.
 */
public final class LayoutConstants {
    private LayoutConstants() {}

    // ── Scale ────────────────────────────────────────────────────────────────
    public static final int    SCALE        = 100;             // 1:100
    public static final double PAPER_FACTOR = 1000.0 / SCALE; // world_m → paper_mm

    // ── Line weights (mm on paper) — ISO 128 / TB-LKTN ──────────────────────
    public static final double LW_GROUND     = 0.70; // ground line, section-cut walls
    public static final double LW_WALL_EXT   = 0.50; // exterior walls, eave, roof silhouette
    public static final double LW_WALL_INT   = 0.35; // interior partitions
    public static final double LW_WALL_GLASS = 0.25; // curtain wall / glazing
    public static final double LW_OPENING    = 0.35; // door/window outlines
    public static final double LW_DIMENSION  = 0.18; // dimension lines
    public static final double LW_GRID       = 0.18; // grid lines (dashed)
    public static final double LW_FURNITURE  = 0.18; // furniture outlines

    // ── Text sizes (mm on paper) ─────────────────────────────────────────────
    public static final double TXT_DIM   = 2.5;
    public static final double TXT_ROOM  = 3.5;
    public static final double TXT_GRID  = 3.0;
    public static final double TXT_TITLE = 5.0;

    // ── Grid bubbles ─────────────────────────────────────────────────────────
    public static final double GRID_CIRCLE_R = 4.0;  // mm radius
    public static final double GRID_EXTEND   = 15.0; // mm extension beyond building

    // ── Dimension offsets (mm on paper) ──────────────────────────────────────
    public static final double DIM_GAP       = 2.0;
    public static final double DIM_EXTEND_PT = 2.0;
    public static final double DIM_OFFSET_1  = 10.0; // first row from building
    public static final double DIM_OFFSET_2  = 18.0; // second (overall) row
    public static final double DIM_TICK_SIZE = 1.5;  // 45° tick half-length

    // ── Colours ──────────────────────────────────────────────────────────────
    public static final String COL_WALL       = "#000000";
    public static final String COL_GLASS      = "#4488CC";
    public static final String COL_OPENING    = "#000000";
    public static final String COL_DIM        = "#000000";
    public static final String COL_GRID       = "#888888";
    public static final String COL_FURNITURE  = "#AAAAAA";
    public static final String COL_FURN_FILL  = "#F0F0F0";
    public static final String COL_BACKGROUND = "#FFFFFF";
    public static final String COL_WALL_FILL  = "#C8C8C8"; // wall fill in elevation
    public static final String COL_FACADE     = "#D0D0D0"; // building silhouette base
    public static final String COL_APRON      = "#E0E0E0"; // concrete apron

    // ── Drawing margins (mm from content boundary to building) ───────────────
    public static final double MARGIN_LEFT   = 35.0;
    public static final double MARGIN_RIGHT  = 15.0;
    public static final double MARGIN_TOP    = 25.0;
    public static final double MARGIN_BOTTOM = 25.0;

    // ── Snap module for clean dimension rounding ──────────────────────────────
    public static final int SNAP_MODULE = 100; // mm — nearest 100mm module

    // ── Section cut ───────────────────────────────────────────────────────────
    public static final double DEFAULT_CUT_Z = 1.0;   // m above floor level

    // ── Ground plane (m, relative to FFL = 0) — drawn as geometry, no marker ─
    public static final double APRON_Z = -0.100; // concrete apron step
    public static final double GRD_Z   = -0.250; // natural ground

    // ── Sheet defaults (fallback when metadata DB absent) ────────────────────
    public static final int    SHEET_W           = 420; // A3 width mm
    public static final int    SHEET_H           = 297; // A3 height mm
    public static final int    SHEET_ML          = 25;
    public static final int    SHEET_MT          = 10;
    public static final int    SHEET_MR          = 10;
    public static final int    SHEET_MB          = 10;
    public static final int    TITLE_BLOCK_W     = 120;
    public static final double BORDER_WEIGHT     = 0.7;
}
