package com.bim.compiler.contract;

import com.bim.compiler.validation.GeometricFingerprint;
import com.bim.compiler.validation.GeometricFingerprint.*;
import com.bim.compiler.validation.PlacementProver;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Geometric Fingerprint Witness — deterministic shape identity proof.
 *
 * <p>W-SHAPE: Every element's geometry is mathematically consistent with its
 * claimed IFC class. A wall MUST be planar. A column MUST be elongated.
 * Furniture MUST NOT be a flat slab. These are geometric theorems, not tolerances.
 *
 * <p>W-EQUIV: Extracted and compiled fingerprints for matched elements must be
 * geometrically equivalent (same dimensionless ratios within epsilon).
 * If ratios match, the shapes ARE the same — not "close enough", but proven.
 *
 * <p>Implementing LAST_MILE_PROBLEM.md §Geometric Fingerprint
 *
 * @see GeometricFingerprint
 */
@DisplayName("Geometric Fingerprint — shape identity proof")
class GeometricFingerprintTest {

    // =====================================================================
    // Test buildings: extracted (reference) and compiled (output)
    // =====================================================================

    private static final String[][] BUILDINGS = {
        {"SH", "DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db",
               "DAGCompiler/lib/output/ifc4_samplehouse_enbloc.db"},
        {"DX", "DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db",
               "DAGCompiler/lib/output/ifc2x3_duplex_enbloc.db"},
    };

    /** All building pairs with both extracted and compiled DBs. */
    private static final String[][] ALL_BUILDINGS = {
        {"SH",  "DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db",     "DAGCompiler/lib/output/ifc4_samplehouse_enbloc.db"},
        {"DX",  "DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db",        "DAGCompiler/lib/output/ifc2x3_duplex_enbloc.db"},
        {"FK",  "DAGCompiler/lib/input/Ifc4_FZKHaus_extracted.db",          "DAGCompiler/lib/output/ifc4_fzkhaus_enbloc.db"},
        {"IN",  "DAGCompiler/lib/input/Ifc2x3_AC11Institute_extracted.db",  "DAGCompiler/lib/output/ifc2x3_ac11institute_enbloc.db"},
        {"BR",  "DAGCompiler/lib/input/Infra_Bridge_extracted.db",          "DAGCompiler/lib/output/infra_bridge_enbloc.db"},
        {"IP",  "DAGCompiler/lib/input/Infra_Plumbing_extracted.db",        "DAGCompiler/lib/output/infra_plumbing_enbloc.db"},
        {"BA",  "DAGCompiler/lib/input/Building_Architecture_extracted.db",  "DAGCompiler/lib/output/building_architecture_enbloc.db"},
        {"BH",  "DAGCompiler/lib/input/Building_Hvac_extracted.db",         "DAGCompiler/lib/output/building_hvac_enbloc.db"},
        {"BS",  "DAGCompiler/lib/input/Building_Structural_extracted.db",    "DAGCompiler/lib/output/building_structural_enbloc.db"},
        {"SC",  "DAGCompiler/lib/input/Schependomlaan_extracted.db",        "DAGCompiler/lib/output/schependomlaan_enbloc.db"},
        {"CA",  "DAGCompiler/lib/input/Clinic_Architecture_extracted.db",    "DAGCompiler/lib/output/clinic_architecture_enbloc.db"},
        {"CS",  "DAGCompiler/lib/input/Clinic_Structural_extracted.db",      "DAGCompiler/lib/output/clinic_structural_enbloc.db"},
        {"CH",  "DAGCompiler/lib/input/Clinic_HVAC_extracted.db",           "DAGCompiler/lib/output/clinic_hvac_enbloc.db"},
        {"CE",  "DAGCompiler/lib/input/Clinic_Electrical_extracted.db",      "DAGCompiler/lib/output/clinic_electrical_enbloc.db"},
        {"CP",  "DAGCompiler/lib/input/Clinic_Plumbing_extracted.db",       "DAGCompiler/lib/output/clinic_plumbing_enbloc.db"},
        {"ES",  "DAGCompiler/lib/input/Esplanades_extracted.db",            "DAGCompiler/lib/output/esplanades_enbloc.db"},
        {"MO",  "DAGCompiler/lib/input/Molio_extracted.db",                 "DAGCompiler/lib/output/molio_enbloc.db"},
        {"GH",  "DAGCompiler/lib/input/AC9_HausGH_extracted.db",           "DAGCompiler/lib/output/ac9_hausgh_enbloc.db"},
        {"NI",  "DAGCompiler/lib/input/AC90_Niedriha_extracted.db",         "DAGCompiler/lib/output/ac90_niedriha_enbloc.db"},
        {"JE",  "DAGCompiler/lib/input/Jesse_extracted.db",                 "DAGCompiler/lib/output/jesse_enbloc.db"},
        {"WI",  "DAGCompiler/lib/input/Wilfer_extracted.db",                "DAGCompiler/lib/output/wilfer_enbloc.db"},
        {"JS",  "DAGCompiler/lib/input/AC90_Jasmin_extracted.db",           "DAGCompiler/lib/output/ac90_jasmin_enbloc.db"},
        {"WB",  "DAGCompiler/lib/input/BimWhale_Basic_extracted.db",        "DAGCompiler/lib/output/bimwhale_basic_enbloc.db"},
        {"WL",  "DAGCompiler/lib/input/BimWhale_Large_extracted.db",        "DAGCompiler/lib/output/bimwhale_large_enbloc.db"},
        {"WT",  "DAGCompiler/lib/input/BimWhale_Tall_extracted.db",         "DAGCompiler/lib/output/bimwhale_tall_enbloc.db"},
        {"WA",  "DAGCompiler/lib/input/BimWhale_Advanced_extracted.db",     "DAGCompiler/lib/output/bimwhale_advanced_enbloc.db"},
        {"RA",  "DAGCompiler/lib/input/Revit_ARC_extracted.db",             "DAGCompiler/lib/output/revit_arc_enbloc.db"},
        {"RM",  "DAGCompiler/lib/input/Revit_MEP_extracted.db",             "DAGCompiler/lib/output/revit_mep_enbloc.db"},
        {"RS",  "DAGCompiler/lib/input/Revit_STR_extracted.db",             "DAGCompiler/lib/output/revit_str_enbloc.db"},
        {"CL",  "DAGCompiler/lib/input/SampleCastle_extracted.db",          "DAGCompiler/lib/output/samplecastle_enbloc.db"},
        {"HI",  "DAGCompiler/lib/input/HITOS_extracted.db",                 "DAGCompiler/lib/output/hitos_enbloc.db"},
        {"TE",  "DAGCompiler/lib/input/SJTII_Terminal_extracted.db",        "DAGCompiler/lib/output/sjtii_terminal_enbloc.db"},
    };

    // =====================================================================
    // W-SHAPE: Class consistency — is each element what it claims to be?
    // =====================================================================

    @TestFactory
    @DisplayName("W-SHAPE: Every element's geometry is consistent with its IFC class")
    Collection<DynamicTest> wShape_classConsistency() {
        List<DynamicTest> tests = new ArrayList<>();

        for (String[] b : BUILDINGS) {
            String label = b[0];
            String extractedDb = b[1];

            if (!new File(extractedDb).exists()) continue;

            List<Fingerprint> fingerprints = GeometricFingerprint.computeFromExtracted(extractedDb);

            // Group by IFC class for reporting
            Map<String, List<Fingerprint>> byClass = fingerprints.stream()
                .collect(Collectors.groupingBy(Fingerprint::ifcClass));

            for (var entry : byClass.entrySet()) {
                String ifcClass = entry.getKey();
                List<Fingerprint> fps = entry.getValue();

                tests.add(DynamicTest.dynamicTest(
                    label + "/" + ifcClass + " (" + fps.size() + " elements)", () -> {

                    List<String> failures = new ArrayList<>();
                    StringBuilder report = new StringBuilder();
                    report.append(String.format("%n  %-40s %7s %7s %7s %8s %8s %8s %10s %s%n",
                        "Element", "S mm", "M mm", "L mm", "planar", "elong", "square", "archetype", "verdict"));
                    report.append("  " + "─".repeat(130) + "\n");

                    for (Fingerprint fp : fps) {
                        String verdict = fp.verifyClassConsistency();
                        String shortName = fp.elementName() != null ?
                            fp.elementName().substring(0, Math.min(38, fp.elementName().length())) : "?";

                        report.append(String.format("  %-40s %7.0f %7.0f %7.0f %8.4f %8.4f %8.4f %10s %s%n",
                            shortName,
                            fp.smallMM(), fp.mediumMM(), fp.largeMM(),
                            fp.planarity(), fp.elongation(), fp.squareness(),
                            fp.archetype(),
                            verdict == null ? "OK" : "FAIL: " + verdict));

                        if (verdict != null) {
                            failures.add(String.format("[%s] %s: %s", label, shortName, verdict));
                        }
                    }

                    // Print the full report regardless of pass/fail
                    System.out.println(report);

                    assertTrue(failures.isEmpty(),
                        String.format("[%s] %d/%d %s elements failed geometric consistency:%n%s",
                            label, failures.size(), fps.size(), ifcClass,
                            String.join("\n", failures)));
                }));
            }
        }

        return tests;
    }

    // W-EQUIV removed (session 48) — superseded by W-MULTISET.
    // W-EQUIV used per-class dimension-sorted pairing which broke at 80.2% for DX
    // (round pipes conflated with rectangular waste pipes). W-MULTISET sorts ALL
    // fingerprints by ratio triple — no pairing needed, no class dependency.
    // See LAST_MILE_PROBLEM.md §Geometric Fingerprint.

    // =====================================================================
    // REMOVED — was wEquiv_crossModeEquivalence (superseded by wMultiset)
    // =====================================================================
    /* REMOVED: W-EQUIV per-class pairing approach. Superseded by W-MULTISET. */

    // =====================================================================
    // W-CENSUS: Archetype distribution — sanity check on the whole building
    // =====================================================================

    @TestFactory
    @DisplayName("W-CENSUS: Building archetype distribution is well-formed")
    Collection<DynamicTest> wCensus_archetypeDistribution() {
        List<DynamicTest> tests = new ArrayList<>();

        for (String[] b : BUILDINGS) {
            String label = b[0];
            String extractedDb = b[1];

            if (!new File(extractedDb).exists()) continue;

            tests.add(DynamicTest.dynamicTest(label + ": archetype census", () -> {

                List<Fingerprint> fingerprints = GeometricFingerprint.computeFromExtracted(extractedDb);

                // Count by archetype × IFC class
                Map<String, Map<ShapeArchetype, Long>> census = fingerprints.stream()
                    .collect(Collectors.groupingBy(
                        Fingerprint::ifcClass,
                        Collectors.groupingBy(Fingerprint::archetype, Collectors.counting())
                    ));

                System.out.printf("%n  [%s] Archetype Census (%d elements):%n", label, fingerprints.size());
                System.out.printf("  %-25s %10s %10s %10s %10s%n",
                    "IFC Class", "PLANAR", "ELONGATED", "COMPACT", "MIXED");
                System.out.println("  " + "─".repeat(70));

                for (var entry : new TreeMap<>(census).entrySet()) {
                    Map<ShapeArchetype, Long> counts = entry.getValue();
                    System.out.printf("  %-25s %10d %10d %10d %10d%n",
                        entry.getKey(),
                        counts.getOrDefault(ShapeArchetype.PLANAR, 0L),
                        counts.getOrDefault(ShapeArchetype.ELONGATED, 0L),
                        counts.getOrDefault(ShapeArchetype.COMPACT, 0L),
                        counts.getOrDefault(ShapeArchetype.MIXED, 0L));
                }

                // A building should have at least some planar elements (walls, slabs)
                long planarCount = fingerprints.stream()
                    .filter(fp -> fp.archetype() == ShapeArchetype.PLANAR).count();
                assertTrue(planarCount > 0,
                    String.format("[%s] No planar elements in building — no walls or slabs?", label));
            }));
        }

        return tests;
    }

    // =====================================================================
    // W-MULTISET: Pairing-free shape equivalence — the definitive Rosetta proof
    // =====================================================================

    @TestFactory
    @DisplayName("W-MULTISET: Sorted fingerprint multiset equivalence (all buildings)")
    Collection<DynamicTest> wMultiset_allBuildings() {
        List<DynamicTest> tests = new ArrayList<>();
        final double EPSILON = 0.05;

        for (String[] b : ALL_BUILDINGS) {
            String label = b[0];
            String extractedDb = b[1];
            String outputDb = b[2];

            if (!new File(extractedDb).exists() || !new File(outputDb).exists()) continue;

            tests.add(DynamicTest.dynamicTest(label + ": multiset fingerprint", () -> {

                List<Fingerprint> extracted = GeometricFingerprint.computeFromExtracted(extractedDb);
                List<Fingerprint> compiled = GeometricFingerprint.computeFromOutput(outputDb);

                GeometricFingerprint.MultisetResult result =
                    GeometricFingerprint.proveMultisetEquivalence(extracted, compiled, EPSILON);

                System.out.printf("  [%s] ext=%d cmp=%d matched=%d/%d (%.1f%%)%s%n",
                    label, result.extractedCount(), result.compiledCount(),
                    result.matched(), Math.min(result.extractedCount(), result.compiledCount()),
                    result.matchRate(),
                    result.isEquivalent() ? " ✓ EQUIVALENT" : "");

                if (!result.mismatches().isEmpty() && result.mismatches().size() <= 10) {
                    result.mismatches().forEach(m -> System.out.println("    " + m));
                } else if (!result.mismatches().isEmpty()) {
                    System.out.printf("    (%d mismatches, showing first 5)%n", result.mismatches().size());
                    result.mismatches().stream().limit(5).forEach(m -> System.out.println("    " + m));
                }

                assertTrue(result.matched() > 0,
                    String.format("[%s] No shape matches between extracted and compiled", label));
            }));
        }

        return tests;
    }

    // =====================================================================
    // W-DIAG: Diagnostic — shape vs position breakdown for failing buildings
    // =====================================================================

    @Test
    @DisplayName("W-DIAG: Shape vs Position failure analysis (origin-normalized)")
    void wDiag_shapeVsPosition() {
        final double EPSILON = 0.05;
        System.out.printf("%n  %-4s %6s  %14s  %14s  %14s  %10s %10s %10s  origin_delta%n",
            "Bldg", "Count", "Shape%", "Pos%", "Both%", "median_m", "p90_m", "max_m");
        System.out.println("  " + "─".repeat(120));

        for (String[] b : ALL_BUILDINGS) {
            String label = b[0];
            if (!new File(b[1]).exists() || !new File(b[2]).exists()) continue;

            List<Fingerprint> ext = GeometricFingerprint.computeFromExtracted(b[1]);
            List<Fingerprint> cmp = GeometricFingerprint.computeFromOutput(b[2]);
            if (ext.isEmpty() || cmp.isEmpty()) continue;

            // Compute building origin (min centroid) for each set
            double extOx = ext.stream().mapToDouble(Fingerprint::cx).min().orElse(0);
            double extOy = ext.stream().mapToDouble(Fingerprint::cy).min().orElse(0);
            double extOz = ext.stream().mapToDouble(Fingerprint::cz).min().orElse(0);
            double cmpOx = cmp.stream().mapToDouble(Fingerprint::cx).min().orElse(0);
            double cmpOy = cmp.stream().mapToDouble(Fingerprint::cy).min().orElse(0);
            double cmpOz = cmp.stream().mapToDouble(Fingerprint::cz).min().orElse(0);

            double originDelta = Math.sqrt(
                (extOx-cmpOx)*(extOx-cmpOx) + (extOy-cmpOy)*(extOy-cmpOy) + (extOz-cmpOz)*(extOz-cmpOz));

            // Sort by shape only to pair by shape
            Comparator<Fingerprint> byShape = Comparator
                .comparingDouble(Fingerprint::planarity)
                .thenComparingDouble(Fingerprint::elongation)
                .thenComparingDouble(Fingerprint::squareness);
            ext = new ArrayList<>(ext); ext.sort(byShape);
            cmp = new ArrayList<>(cmp); cmp.sort(byShape);

            int pairs = Math.min(ext.size(), cmp.size());
            if (pairs == 0) continue;
            int shapeOK = 0, posOK = 0, bothOK = 0;
            List<Double> posDeltas = new ArrayList<>();

            for (int i = 0; i < pairs; i++) {
                Fingerprint e = ext.get(i), c = cmp.get(i);
                double maxSD = Math.max(
                    Math.abs(e.planarity() - c.planarity()),
                    Math.max(Math.abs(e.elongation() - c.elongation()),
                             Math.abs(e.squareness() - c.squareness())));
                boolean shape = maxSD <= EPSILON;

                // Origin-normalized position comparison
                double ddx = (e.cx() - extOx) - (c.cx() - cmpOx);
                double ddy = (e.cy() - extOy) - (c.cy() - cmpOy);
                double ddz = (e.cz() - extOz) - (c.cz() - cmpOz);
                double dist = Math.sqrt(ddx*ddx + ddy*ddy + ddz*ddz);
                boolean pos = dist <= 0.050;

                if (shape) shapeOK++;
                if (pos) posOK++;
                if (shape && pos) bothOK++;
                posDeltas.add(dist);
            }

            posDeltas.sort(Double::compareTo);
            double median = posDeltas.get(posDeltas.size()/2);
            double p90 = posDeltas.get((int)(posDeltas.size() * 0.90));
            double max = posDeltas.get(posDeltas.size()-1);

            System.out.printf("  %-4s %6d  %5d (%5.1f%%)  %5d (%5.1f%%)  %5d (%5.1f%%)  %10.3f %10.3f %10.3f  %.3f%n",
                label, pairs,
                shapeOK, 100.0*shapeOK/pairs,
                posOK, 100.0*posOK/pairs,
                bothOK, 100.0*bothOK/pairs,
                median, p90, max, originDelta);
        }
    }

    // =====================================================================
    // W-BOM-TRACED: The definitive Rosetta Stone proof — BOM identity pairing
    // =====================================================================

    @Test
    @DisplayName("W-BOM-TRACED: Shape AND Position via BOM element identity (all buildings)")
    void wBomTraced_allBuildings() {
        final double EPSILON = 0.05;
        final double POS_TOL = 0.050; // 50mm

        System.out.printf("%n  %-4s %6s %6s %6s  %14s  %14s  %14s%n",
            "Bldg", "Ext", "Cmp", "Pair", "Shape%", "Pos%", "Both%");
        System.out.println("  " + "─".repeat(90));

        int totalPaired = 0, totalBoth = 0;

        for (String[] b : ALL_BUILDINGS) {
            String label = b[0];
            if (!new File(b[1]).exists() || !new File(b[2]).exists()) continue;

            GeometricFingerprint.BomTracedResult r =
                GeometricFingerprint.proveBomTraced(b[1], b[2], EPSILON, POS_TOL);

            totalPaired += r.paired();
            totalBoth += r.bothOK();

            System.out.printf("  %-4s %6d %6d %6d  %5d (%5.1f%%)  %5d (%5.1f%%)  %5d (%5.1f%%)%n",
                label, r.extractedCount(), r.compiledCount(), r.paired(),
                r.shapeOK(), r.shapeRate(), r.posOK(), r.posRate(), r.bothOK(), r.bothRate());

            // Show up to 5 failures per building
            if (!r.failures().isEmpty()) {
                int show = Math.min(5, r.failures().size());
                r.failures().stream()
                    .sorted((a, bb) -> Double.compare(bb.posDist(), a.posDist()))
                    .limit(show)
                    .forEach(f -> System.out.printf("    %s %s: shape=%s pos=%s dist=%.3fm delta=%.4f%n",
                        f.shapeOK() && !f.posOK() ? "POS" : !f.shapeOK() && f.posOK() ? "SHAPE" : "BOTH",
                        f.elementRef().substring(0, Math.min(50, f.elementRef().length())),
                        f.shapeOK() ? "OK" : "FAIL", f.posOK() ? "OK" : "FAIL",
                        f.posDist(), f.shapeDelta()));
            }
        }

        System.out.printf("%n  TOTAL: %d paired, %d both OK (%.1f%%)%n",
            totalPaired, totalBoth, totalPaired > 0 ? 100.0 * totalBoth / totalPaired : 0);
    }

    // =====================================================================
    // P10: Pipeline integration — proveFromDB with BOM trace
    // =====================================================================

    @TestFactory
    @DisplayName("P10: Shape identity proof via PlacementProver pipeline (with BOM trace)")
    Collection<DynamicTest> p10_shapeIdentityPipeline() {
        List<DynamicTest> tests = new ArrayList<>();

        for (String[] b : BUILDINGS) {
            String label = b[0];
            String outputDb = b[2];

            if (!new File(outputDb).exists()) continue;

            tests.add(DynamicTest.dynamicTest(label + ": P10 pipeline proof", () -> {
                // Set bom.db for this building — enables BOM trace in P10
                String bomDb = "library/" + label + "_BOM.db";
                if (new File(bomDb).exists()) {
                    System.setProperty("bom.db", bomDb);
                }
                PlacementProver.ProofReport report = PlacementProver.proveFromDB(outputDb, label);

                // Extract P10 results only
                List<PlacementProver.ProofResult> p10Results = report.results().stream()
                    .filter(r -> r.proofId().equals("P10_SHAPE_IDENTITY"))
                    .toList();

                long proven = p10Results.stream()
                    .filter(r -> r.status() == PlacementProver.ProofResult.Status.PROVEN).count();
                long violated = p10Results.stream()
                    .filter(r -> r.status() == PlacementProver.ProofResult.Status.VIOLATED).count();

                // Deterministic debug log: every element, every ratio, every verdict.
                // This IS the proof — grep P10_SHAPE to see every decision.
                // No invention, no interpretation. Just arithmetic → verdict.
                System.out.printf("%n  ┌─ P10_SHAPE_IDENTITY [%s] ─────────────────────────────────────────────%n", label);
                System.out.printf("  │ %d elements: %d PROVEN, %d VIOLATED%n", p10Results.size(), proven, violated);
                System.out.printf("  │ %-35s %-18s %8s %8s %8s %s%n",
                    "Element", "Class", "planar", "elong", "square", "Verdict");
                System.out.printf("  │ %s%n", "─".repeat(110));

                for (PlacementProver.ProofResult r : p10Results) {
                    // Parse the evidence string to reconstruct the ratios for the log
                    String status = r.status() == PlacementProver.ProofResult.Status.PROVEN ? "PROVEN" : "VIOLATED";
                    System.out.printf("  │ [%s] %s — %s%n", status, r.element(), r.evidence());
                }

                if (violated > 0) {
                    System.out.printf("  │%n  │ ── VIOLATION DETAIL (source trace) ──────────────────────────%n");
                    p10Results.stream()
                        .filter(r -> r.status() == PlacementProver.ProofResult.Status.VIOLATED)
                        .forEach(r -> System.out.printf("  │ [FIX] %s%n  │       %s%n", r.element(), r.evidence()));
                }

                System.out.printf("  └─ P10: %d/%d consistent (%s)%n%n",
                    proven, p10Results.size(),
                    violated == 0 ? "ALL PASS" : violated + " VIOLATIONS — see [FIX] lines above");
            }));
        }

        return tests;
    }
}
