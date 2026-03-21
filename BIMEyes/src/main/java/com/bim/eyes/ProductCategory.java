package com.bim.eyes;

import java.util.Map;

/**
 * Semantic product category — replaces IFC class switches in runtime code.
 * // Implementing BBC.md §2.2.1 — Witness: W-PRODUCT-CATEGORY
 *
 * <p>Maps IFC class strings to domain-independent product categories.
 * Authority: component_types.product_category in component_library.db (CP4_002 migration).
 * This static map mirrors the DB for zero-dependency runtime resolution.
 */
public final class ProductCategory {

    public static final String STRUCTURAL_LINEAR = "STRUCTURAL_LINEAR";
    public static final String STRUCTURAL_PLANAR = "STRUCTURAL_PLANAR";
    public static final String MEP_ROUTING       = "MEP_ROUTING";
    public static final String MEP_TERMINAL      = "MEP_TERMINAL";
    public static final String OPENING           = "OPENING";
    public static final String FURNISHING        = "FURNISHING";
    public static final String ENVELOPE          = "ENVELOPE";
    public static final String CIRCULATION       = "CIRCULATION";
    public static final String SITE              = "SITE";
    public static final String INFRASTRUCTURE    = "INFRASTRUCTURE";

    private static final Map<String, String> IFC_TO_CATEGORY = Map.ofEntries(
        Map.entry("IfcBeam",               STRUCTURAL_LINEAR),
        Map.entry("IfcColumn",             STRUCTURAL_LINEAR),
        Map.entry("IfcMember",             STRUCTURAL_LINEAR),
        Map.entry("IfcReinforcingBar",     STRUCTURAL_LINEAR),
        Map.entry("IfcDiscreteAccessory",  STRUCTURAL_LINEAR),
        Map.entry("IfcElementAssembly",    STRUCTURAL_LINEAR),
        Map.entry("IfcSlab",              STRUCTURAL_PLANAR),
        Map.entry("IfcWall",              STRUCTURAL_PLANAR),
        Map.entry("IfcWallStandardCase",  STRUCTURAL_PLANAR),
        Map.entry("IfcFooting",           STRUCTURAL_PLANAR),
        Map.entry("IfcPile",              STRUCTURAL_PLANAR),
        Map.entry("IfcPlate",             STRUCTURAL_PLANAR),
        Map.entry("IfcPipeSegment",       MEP_ROUTING),
        Map.entry("IfcPipeFitting",       MEP_ROUTING),
        Map.entry("IfcDuctSegment",       MEP_ROUTING),
        Map.entry("IfcDuctFitting",       MEP_ROUTING),
        Map.entry("IfcFlowSegment",       MEP_ROUTING),
        Map.entry("IfcFlowFitting",       MEP_ROUTING),
        Map.entry("IfcFlowController",    MEP_ROUTING),
        Map.entry("IfcFireSuppressionTerminal", MEP_TERMINAL),
        Map.entry("IfcLightFixture",      MEP_TERMINAL),
        Map.entry("IfcAirTerminal",       MEP_TERMINAL),
        Map.entry("IfcAlarm",             MEP_TERMINAL),
        Map.entry("IfcSanitaryTerminal",  MEP_TERMINAL),
        Map.entry("IfcFlowTerminal",      MEP_TERMINAL),
        Map.entry("IfcSensor",            MEP_TERMINAL),
        Map.entry("IfcValve",             MEP_TERMINAL),
        Map.entry("IfcElectricAppliance", MEP_TERMINAL),
        Map.entry("IfcController",        MEP_TERMINAL),
        Map.entry("IfcOutlet",            MEP_TERMINAL),
        Map.entry("IfcSwitchingDevice",   MEP_TERMINAL),
        Map.entry("IfcFan",               MEP_TERMINAL),
        Map.entry("IfcDoor",              OPENING),
        Map.entry("IfcWindow",            OPENING),
        Map.entry("IfcOpeningElement",    OPENING),
        Map.entry("IfcFurnishingElement", FURNISHING),
        Map.entry("IfcFurniture",         FURNISHING),
        Map.entry("IfcBuildingElementProxy", FURNISHING),
        Map.entry("IfcRoof",              ENVELOPE),
        Map.entry("IfcCovering",          ENVELOPE),
        Map.entry("IfcChimney",           ENVELOPE),
        Map.entry("IfcStairFlight",       CIRCULATION),
        Map.entry("IfcRailing",           CIRCULATION),
        Map.entry("IfcRampFlight",        CIRCULATION),
        Map.entry("IfcEarthworksFill",    SITE),
        Map.entry("IfcGeographicElement", SITE),
        Map.entry("IfcRail",              INFRASTRUCTURE),
        Map.entry("IfcTrackElement",      INFRASTRUCTURE),
        Map.entry("IfcCourse",            INFRASTRUCTURE),
        Map.entry("IfcSurfaceFeature",    INFRASTRUCTURE),
        Map.entry("IfcSign",              INFRASTRUCTURE)
    );

    private ProductCategory() {}

    /** Resolve IFC class to product category. Returns null for unknown. */
    public static String resolve(String ifcClass) {
        if (ifcClass == null) return null;
        return IFC_TO_CATEGORY.get(ifcClass);
    }

    /** Check if the IFC class belongs to the given product category. */
    public static boolean is(String ifcClass, String category) {
        return category.equals(resolve(ifcClass));
    }

    /** Derive discipline from product category. */
    public static String deriveDiscipline(String productCategory) {
        if (productCategory == null) return "ARC";
        return switch (productCategory) {
            case STRUCTURAL_LINEAR, STRUCTURAL_PLANAR -> "STR";
            case MEP_ROUTING, MEP_TERMINAL -> "MEP";
            default -> "ARC";
        };
    }
}
