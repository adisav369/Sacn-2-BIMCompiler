package com.bim.backoffice.federation;

import java.util.Map;

/**
 * IFC Label Mapper — ported from ifc_label_mapper.py.
 *
 * <p>Maps IFC class names to human-friendly labels, and discipline codes
 * to full names. Used by NLP result formatting and report templates.
 *
 * // Porting ifc_label_mapper.py — Witness: W-IFC-LABEL-1
 */
public final class IfcLabelMapper {

    private IfcLabelMapper() {}

    // ── IFC class → friendly label (from _FALLBACK_IFC_LABELS) ──────

    private static final Map<String, String> IFC_LABELS = Map.ofEntries(
            // Structural
            Map.entry("IfcBeam",                 "Beam"),
            Map.entry("IfcColumn",               "Column"),
            Map.entry("IfcFooting",              "Footing"),
            Map.entry("IfcSlab",                 "Slab"),
            Map.entry("IfcStair",                "Stair"),
            Map.entry("IfcStairFlight",          "Stair Flight"),
            Map.entry("IfcRoof",                 "Roof"),
            Map.entry("IfcMember",               "Structural Member"),
            Map.entry("IfcPlate",                "Plate"),
            Map.entry("IfcPile",                 "Pile"),
            Map.entry("IfcRailing",              "Railing"),
            Map.entry("IfcRamp",                 "Ramp"),
            Map.entry("IfcRampFlight",           "Ramp Flight"),
            // Architectural
            Map.entry("IfcWall",                 "Wall"),
            Map.entry("IfcWallStandardCase",     "Wall"),
            Map.entry("IfcDoor",                 "Door"),
            Map.entry("IfcWindow",               "Window"),
            Map.entry("IfcSpace",                "Room/Space"),
            Map.entry("IfcFurniture",            "Furniture"),
            Map.entry("IfcFurnishingElement",    "Furnishing"),
            Map.entry("IfcCovering",             "Covering"),
            Map.entry("IfcCurtainWall",          "Curtain Wall"),
            Map.entry("IfcBuildingElementProxy",  "Building Element"),
            // MEP/HVAC
            Map.entry("IfcDuctSegment",          "Duct"),
            Map.entry("IfcDuctFitting",          "Duct Fitting"),
            Map.entry("IfcAirTerminal",          "Air Terminal/Diffuser"),
            Map.entry("IfcAirTerminalBox",       "VAV Box"),
            Map.entry("IfcBoiler",               "Boiler"),
            Map.entry("IfcChiller",              "Chiller"),
            Map.entry("IfcFan",                  "Fan"),
            Map.entry("IfcCoil",                 "Coil"),
            Map.entry("IfcHeatExchanger",        "Heat Exchanger"),
            Map.entry("IfcUnitaryEquipment",     "AHU/FCU"),
            Map.entry("IfcDamper",               "Damper"),
            Map.entry("IfcFilter",               "Filter"),
            // MEP/Plumbing
            Map.entry("IfcPipeSegment",          "Pipe"),
            Map.entry("IfcPipeFitting",          "Pipe Fitting"),
            Map.entry("IfcValve",                "Valve"),
            Map.entry("IfcPump",                 "Pump"),
            Map.entry("IfcTank",                 "Tank"),
            Map.entry("IfcSanitaryTerminal",     "Sanitary Fixture"),
            Map.entry("IfcFlowMeter",            "Flow Meter"),
            // MEP/Electrical
            Map.entry("IfcCableCarrierSegment",  "Cable Tray"),
            Map.entry("IfcCableCarrierFitting",  "Cable Tray Fitting"),
            Map.entry("IfcCableSegment",         "Cable"),
            Map.entry("IfcElectricDistributionBoard", "Distribution Board"),
            Map.entry("IfcLamp",                 "Lamp"),
            Map.entry("IfcLightFixture",         "Light Fixture"),
            Map.entry("IfcSwitchingDevice",      "Switch"),
            Map.entry("IfcOutlet",               "Outlet"),
            Map.entry("IfcJunctionBox",          "Junction Box"),
            // Fire
            Map.entry("IfcFireSuppressionTerminal", "Sprinkler"),
            Map.entry("IfcAlarm",                "Alarm"),
            // Building
            Map.entry("IfcBuilding",             "Building"),
            Map.entry("IfcBuildingStorey",        "Storey"),
            Map.entry("IfcSite",                 "Site")
    );

    // ── Discipline code → full name (from get_discipline_label()) ───

    private static final Map<String, String> DISCIPLINE_LABELS = Map.ofEntries(
            Map.entry("STR",    "Structural"),
            Map.entry("ARC",    "Architecture"),
            Map.entry("MEP",    "Mechanical, Electrical & Plumbing"),
            Map.entry("ACMV",   "HVAC (Air Conditioning & Mechanical Ventilation)"),
            Map.entry("PLUMB",  "Plumbing"),
            Map.entry("PP",     "Plumbing"),
            Map.entry("SP",     "Sanitary & Plumbing"),
            Map.entry("ELEC",   "Electrical"),
            Map.entry("FP",     "Fire Protection"),
            Map.entry("CW",     "Cold Water"),
            Map.entry("ICT",    "Information & Communications Technology"),
            Map.entry("FURN",   "Furniture"),
            Map.entry("GAS",    "Gas"),
            Map.entry("REB",    "Reinforcement"),
            Map.entry("GEO",    "Geotechnical"),
            Map.entry("FAC",    "Facilities"),
            Map.entry("IOT",    "IoT Monitoring")
    );

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Get friendly label for an IFC class.
     * Ported from get_friendly_label().
     *
     * @param ifcClass e.g. "IfcBeam"
     * @param elementName optional element name for context
     * @return friendly label, e.g. "Beam" or "Beam (B-100)"
     */
    public static String getFriendlyLabel(String ifcClass, String elementName) {
        String label = IFC_LABELS.getOrDefault(ifcClass,
                ifcClass != null ? ifcClass.replace("Ifc", "") : "Unknown");
        if (elementName != null && isMeaningfulName(elementName)) {
            String name = elementName.length() > 30
                    ? elementName.substring(0, 30) + "..." : elementName;
            return label + " (" + name + ")";
        }
        return label;
    }

    /** Get friendly label without element name. */
    public static String getFriendlyLabel(String ifcClass) {
        return getFriendlyLabel(ifcClass, null);
    }

    /** Get discipline full name from code. */
    public static String getDisciplineLabel(String code) {
        return DISCIPLINE_LABELS.getOrDefault(code != null ? code.toUpperCase() : "",
                code != null ? code : "Unknown");
    }

    /** All IFC labels (for export/legend). */
    public static Map<String, String> allIfcLabels() { return IFC_LABELS; }

    /** All discipline labels (for export/legend). */
    public static Map<String, String> allDisciplineLabels() { return DISCIPLINE_LABELS; }

    // ── Meaningful name heuristic (from _is_meaningful_name) ────────

    /**
     * Detect if an element name is meaningful vs. a GUID.
     * Ported from _is_meaningful_name() heuristics.
     */
    static boolean isMeaningfulName(String name) {
        if (name == null || name.length() < 3) return false;
        // Reject GUID-like: mostly alphanumeric, long, no spaces
        long alphaNum = name.chars().filter(Character::isLetterOrDigit).count();
        if (alphaNum > name.length() * 0.8 && name.length() > 20
                && !name.contains(" ")) return false;
        // Accept if has separators
        if (name.contains(" ") || name.contains("-") || name.contains("/")) return true;
        // Reject monotone
        if ((name.equals(name.toUpperCase()) || name.equals(name.toLowerCase()))
                && !name.contains(" ")) return false;
        return true;
    }
}
