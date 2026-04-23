package com.bim.compiler.drawing2d.model;

/**
 * One page in a drawing manifest — auto-generated from scope detection.
 *
 * @param drawingCode  type code from 2d_drawing_type ("FLOOR_PLAN", "FRONT_ELEV", "PLUMBING_PLAN")
 * @param viewType     rendering category ("PLAN", "ELEVATION", "ROOF_PLAN", "SECTION", "SCHEDULE")
 * @param discipline   discipline code ("ARC", "PLMB", "ELEC", "HVAC", "STR")
 * @param storeyName   storey name from DB ("Level 1", "Ground Floor") — null for non-storey pages
 * @param floorZ       floor elevation in metres — NaN for non-storey pages
 * @param sheetNo      sheet number for title block ("A-01-GF", "M-01-FF")
 * @param title        drawing title for title block ("GROUND FLOOR PLAN", "FRONT ELEVATION")
 */
public record PageEntry(
    String drawingCode,
    String viewType,
    String discipline,
    String storeyName,
    double floorZ,
    String sheetNo,
    String title
) {

    /** True if this page is storey-specific (floor plans, MEP plans). */
    public boolean isStoreyPage() {
        return storeyName != null && !Double.isNaN(floorZ);
    }

    /** File key for output naming: FLOOR_GF, FRONT, PLUMBING_FF, etc. */
    public String fileKey() {
        String base = switch (drawingCode) {
            case "FLOOR_PLAN"     -> "FLOOR";
            case "ROOF_PLAN"      -> "ROOF";
            case "FRONT_ELEV"     -> "FRONT";
            case "REAR_ELEV"      -> "REAR";
            case "LEFT_ELEV"      -> "LEFT";
            case "RIGHT_ELEV"     -> "RIGHT";
            case "PLUMBING_PLAN"  -> "PLUMBING";
            case "ELECT_PLAN"     -> "ELECTRICAL";
            case "REFL_CEILING"   -> "CEILING";
            case "SECTION_AA"     -> "SECTION";
            case "OPENING_SCHED"  -> "SCHEDULE";
            default               -> drawingCode;
        };
        // Multi-storey pages: sheetNo has storey code suffix (e.g. "A-01-GF")
        // Only append if the suffix is a storey code (letters), not a number
        if (isStoreyPage()) {
            int lastDash = sheetNo.lastIndexOf('-');
            if (lastDash > 0) {
                String suffix = sheetNo.substring(lastDash + 1);
                if (!suffix.isEmpty() && !suffix.chars().allMatch(Character::isDigit)) {
                    return base + "_" + suffix;
                }
            }
        }
        return base;
    }
}
