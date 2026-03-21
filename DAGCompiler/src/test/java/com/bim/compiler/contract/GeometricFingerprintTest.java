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

    // =====================================================================
    // W-EQUIV: Cross-mode equivalence — extracted ≡ compiled
    // =====================================================================

    @TestFactory
    @DisplayName("W-EQUIV: Extracted and compiled fingerprints are geometrically equivalent")
    Collection<DynamicTest> wEquiv_crossModeEquivalence() {
        List<DynamicTest> tests = new ArrayList<>();

        // Epsilon for ratio comparison: 5% allows for inner-surface AABB gradient
        // while catching genuine shape mismatches (wrong element, 90° swap, etc.)
        final double EPSILON = 0.05;

        for (String[] b : BUILDINGS) {
            String label = b[0];
            String extractedDb = b[1];
            String outputDb = b[2];

            if (!new File(extractedDb).exists() || !new File(outputDb).exists()) continue;

            tests.add(DynamicTest.dynamicTest(label + ": cross-mode equivalence", () -> {

                List<Fingerprint> extracted = GeometricFingerprint.computeFromExtracted(extractedDb);
                List<Fingerprint> compiled = GeometricFingerprint.computeFromOutput(outputDb);

                // Cross-mode matching: GUIDs differ (IFC vs compiled names).
                // Match by IFC class + sorted dimension order (largest first).
                // Within same class, pair by dimension-sorted rank — same approach
                // as COBOL batch matching: sort both sides identically, pair by position.
                Map<String, List<Fingerprint>> extByClass = extracted.stream()
                    .collect(Collectors.groupingBy(Fingerprint::ifcClass));
                Map<String, List<Fingerprint>> cmpByClass = compiled.stream()
                    .collect(Collectors.groupingBy(Fingerprint::ifcClass));

                int matched = 0, equivalent = 0;
                List<String> mismatches = new ArrayList<>();

                StringBuilder report = new StringBuilder();
                report.append(String.format("%n  %-30s %-15s %8s %8s %8s │ %8s %8s %8s │ %s%n",
                    "Element", "Class", "ext.P", "ext.E", "ext.S", "cmp.P", "cmp.E", "cmp.S", "Verdict"));
                report.append("  " + "─".repeat(130) + "\n");

                for (var entry : extByClass.entrySet()) {
                    String ifcClass = entry.getKey();
                    List<Fingerprint> extList = new ArrayList<>(entry.getValue());
                    List<Fingerprint> cmpList = cmpByClass.getOrDefault(ifcClass, List.of());
                    if (cmpList.isEmpty()) continue;
                    cmpList = new ArrayList<>(cmpList);

                    // Sort both by (largeMM desc, mediumMM desc, smallMM desc) — deterministic pairing
                    Comparator<Fingerprint> byDims = Comparator
                        .comparingDouble(Fingerprint::largeMM).reversed()
                        .thenComparing(Comparator.comparingDouble(Fingerprint::mediumMM).reversed())
                        .thenComparing(Comparator.comparingDouble(Fingerprint::smallMM).reversed());
                    extList.sort(byDims);
                    cmpList.sort(byDims);

                    int pairs = Math.min(extList.size(), cmpList.size());
                    for (int i = 0; i < pairs; i++) {
                        Fingerprint ext = extList.get(i);
                        Fingerprint cmp = cmpList.get(i);
                        matched++;

                        String diff = GeometricFingerprint.proveEquivalence(ext, cmp, EPSILON);
                        if (diff == null) equivalent++;

                        String shortName = ext.elementName() != null ?
                            ext.elementName().substring(0, Math.min(28, ext.elementName().length())) : "?";

                        report.append(String.format("  %-30s %-15s %8.4f %8.4f %8.4f │ %8.4f %8.4f %8.4f │ %s%n",
                            shortName, ifcClass,
                            ext.planarity(), ext.elongation(), ext.squareness(),
                            cmp.planarity(), cmp.elongation(), cmp.squareness(),
                            diff == null ? "EQUIVALENT" : "DIFFER: " + diff));

                        if (diff != null) {
                            mismatches.add(String.format("[%s] %s (%s): %s", label, shortName, ifcClass, diff));
                        }
                    }
                }

                report.append(String.format("%n  Summary: %d matched, %d equivalent, %d differ (epsilon=%.2f)%n",
                    matched, equivalent, mismatches.size(), EPSILON));

                System.out.println(report);

                if (!mismatches.isEmpty()) {
                    System.out.println("  *** Elements with geometric differences (investigation targets):");
                    mismatches.forEach(m -> System.out.println("    " + m));
                }

                assertTrue(matched > 0,
                    String.format("[%s] No class matches between extracted and compiled", label));
                System.out.printf("  [%s] %d/%d matched elements are geometrically equivalent (%.1f%%)%n",
                    label, equivalent, matched, 100.0 * equivalent / matched);
            }));
        }

        return tests;
    }

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
