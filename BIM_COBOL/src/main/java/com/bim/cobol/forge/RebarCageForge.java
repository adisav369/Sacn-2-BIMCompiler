package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import com.bim.cobol.forge.rebar.*;

import java.util.*;

/**
 * REBAR_CAGE — compute reinforcement cage from element type + standards.
 *
 * Grammar: FORGE REBAR_CAGE type:SLAB grade:GRADE_30 exposure:XC1 width:6000 depth:4000 thickness:200
 *
 * // Implementing FORGE_SUITE_SRS.md §10 Phase 7 — Witness: W-FORGE-9
 */
public class RebarCageForge implements ForgeEngine {

    @Override
    public String pieceType() { return "REBAR_CAGE"; }

    @Override
    public ForgeResult compute(VerbContext ctx, Map<String, String> params) {
        String type = params.getOrDefault("type", "").toUpperCase();
        ConcreteGrade grade = ConcreteGrade.fromString(params.getOrDefault("grade", "GRADE_30"));
        ExposureClass exposure = ExposureClass.fromString(params.getOrDefault("exposure", "XC3"));

        if (grade == null) return ForgeResult.fail(pieceType(), "invalid concrete grade");
        if (exposure == null) return ForgeResult.fail(pieceType(), "invalid exposure class");

        return switch (type) {
            case "SLAB" -> computeSlab(params, grade, exposure);
            case "BEAM" -> computeBeam(params, grade, exposure);
            case "COLUMN" -> computeColumn(params, grade, exposure);
            default -> ForgeResult.fail(pieceType(), "type must be SLAB, BEAM, or COLUMN");
        };
    }

    private ForgeResult computeSlab(Map<String, String> params,
            ConcreteGrade grade, ExposureClass exposure) {
        Double width = parseDouble(params, "width");
        Double depth = parseDouble(params, "depth");
        Double thickness = parseDouble(params, "thickness");

        if (width == null || depth == null || thickness == null)
            return ForgeResult.fail(pieceType(), "SLAB requires: width, depth, thickness");

        Map<String, Double> fab = SlabReinforcement.calculate(width, depth, thickness, grade, exposure);

        List<String> compliance = new ArrayList<>();
        double ratio = fab.get("reinforcement_ratio");
        compliance.add(ratio >= 0.0013
            ? "PASS reinforcement ratio " + String.format("%.4f", ratio) + " >= 0.0013"
            : "FAIL reinforcement ratio " + String.format("%.4f", ratio) + " < 0.0013");
        double spacing = fab.get("main_bar_spacing");
        compliance.add(spacing <= 300
            ? "PASS main bar spacing " + spacing + "mm <= 300mm"
            : "FAIL main bar spacing " + spacing + "mm > 300mm");

        GeometryRecord rec = new GeometryRecord(
                null, null, depth, width, thickness,
                fab, 0, 0, 0, 0);

        return ForgeResult.ok(pieceType(),
                String.format("REBAR_CAGE SLAB %.0fx%.0fx%.0fmm T%.0f@%.0fmm",
                        width, depth, thickness,
                        fab.get("main_bar_diameter"), spacing),
                List.of(rec), compliance);
    }

    private ForgeResult computeBeam(Map<String, String> params,
            ConcreteGrade grade, ExposureClass exposure) {
        Double width = parseDouble(params, "width");
        Double height = parseDouble(params, "height");
        Double length = parseDouble(params, "length");

        if (width == null || height == null || length == null)
            return ForgeResult.fail(pieceType(), "BEAM requires: width, height, length");

        Map<String, Double> fab = BeamReinforcement.calculate(width, height, length, grade, exposure);

        List<String> compliance = new ArrayList<>();
        double ratio = fab.get("reinforcement_ratio");
        compliance.add(ratio >= 0.0013
            ? "PASS tension reinforcement ratio " + String.format("%.4f", ratio) + " >= 0.0013"
            : "FAIL tension reinforcement ratio " + String.format("%.4f", ratio) + " < 0.0013");
        double stirrupSpacing = fab.get("stirrup_spacing");
        double effectiveDepth = fab.get("effective_depth_mm");
        double maxStirrup = 0.75 * effectiveDepth;
        compliance.add(stirrupSpacing <= maxStirrup
            ? "PASS stirrup spacing " + stirrupSpacing + "mm <= 0.75d=" + String.format("%.0f", maxStirrup) + "mm"
            : "FAIL stirrup spacing " + stirrupSpacing + "mm > 0.75d=" + String.format("%.0f", maxStirrup) + "mm");

        GeometryRecord rec = new GeometryRecord(
                null, null, length, width, height,
                fab, 0, 0, 0, 0);

        return ForgeResult.ok(pieceType(),
                String.format("REBAR_CAGE BEAM %.0fx%.0fx%.0fmm %d+%d T%.0f",
                        width, height, length,
                        fab.get("bottom_bar_count").intValue(),
                        fab.get("top_bar_count").intValue(),
                        fab.get("main_bar_diameter")),
                List.of(rec), compliance);
    }

    private ForgeResult computeColumn(Map<String, String> params,
            ConcreteGrade grade, ExposureClass exposure) {
        Double width = parseDouble(params, "width");
        Double depth = parseDouble(params, "depth");
        Double height = parseDouble(params, "height");

        if (width == null || depth == null || height == null)
            return ForgeResult.fail(pieceType(), "COLUMN requires: width, depth, height");

        Map<String, Double> fab = ColumnReinforcement.calculate(width, depth, height, grade, exposure);

        List<String> compliance = new ArrayList<>();
        double ratio = fab.get("reinforcement_ratio");
        compliance.add(ratio >= 0.008
            ? "PASS reinforcement ratio " + String.format("%.4f", ratio) + " >= 0.008"
            : "FAIL reinforcement ratio " + String.format("%.4f", ratio) + " < 0.008");
        compliance.add(ratio <= 0.04
            ? "PASS reinforcement ratio " + String.format("%.4f", ratio) + " <= 0.04"
            : "FAIL reinforcement ratio " + String.format("%.4f", ratio) + " > 0.04");
        double linkSpacing = fab.get("link_spacing");
        double barDia = fab.get("longitudinal_bar_diameter");
        double maxLink = 12 * barDia;
        compliance.add(linkSpacing <= maxLink
            ? "PASS link spacing " + linkSpacing + "mm <= 12d=" + maxLink + "mm"
            : "FAIL link spacing " + linkSpacing + "mm > 12d=" + maxLink + "mm");

        GeometryRecord rec = new GeometryRecord(
                null, null, height, width, depth,
                fab, 0, 0, 0, 0);

        return ForgeResult.ok(pieceType(),
                String.format("REBAR_CAGE COLUMN %.0fx%.0fx%.0fmm %dT%.0f",
                        width, depth, height,
                        fab.get("longitudinal_bar_count").intValue(),
                        fab.get("longitudinal_bar_diameter")),
                List.of(rec), compliance);
    }

    private static Double parseDouble(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null) return null;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return null; }
    }
}
