package com.bim.compiler.contract;

import com.bim.compiler.dsl.BuildingRegistry;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.validation.SpatialDiff;
import com.bim.compiler.validation.SpatialDiff.Band;
import com.bim.compiler.validation.SpatialDiff.DiffReport;
import com.bim.compiler.validation.SpatialDiff.ElementDelta;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * WYSIWYG Totality Test — every element in reference exists in output with matching AABB.
 *
 * <p><b>Purpose:</b> G1-COUNT proves total element count matches. G3-DIGEST proves the
 * aggregate spatial hash matches. But neither identifies WHICH element is wrong or by how
 * much. This test opens reference and output side by side and asserts per-element AABB
 * identity within 1mm tolerance.
 *
 * <p><b>Matching strategy:</b> Elements are matched by position sort order within each
 * ifc_class (same deterministic order as SpatialDigest). This is necessary because:
 * <ul>
 *   <li>Reference DBs do not have element_ref column</li>
 *   <li>Element names differ between extracted and compiled output</li>
 *   <li>Position-sorted matching is proven reliable by SpatialDigest (G3)</li>
 * </ul>
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-TOT-1: SH — every element in reference matches output AABB within 1mm</li>
 *   <li>W-TOT-2: DX — every element in reference matches output AABB within 1mm</li>
 *   <li>W-TOT-3: Bijection — no extra elements in output that aren't in reference</li>
 * </ul>
 *
 * <p>Addresses Gap 5 (LAST_MILE_PROBLEM.md): "No test opens input and output side by side
 * and confirms every element matches by identity."
 *
 * @see SpatialDiff — the per-element diff engine
 * @see SpotCheckContractTest — spot-checks 5 elements (this tests ALL)
 * @see RosettaStoneGateTest — the 6-gate framework
 */
class TotalityContractTest {

    private static final double TOLERANCE_MM = 1.0;
    private static final Set<String> GATE_SCOPE = Set.of("RE_SH", "RE_DX");

    @TestFactory
    @DisplayName("W-TOT: WYSIWYG totality — every element matches within 1mm")
    Collection<DynamicTest> totalityTests() {
        List<BuildingEntry> buildings;
        try {
            buildings = BuildingRegistry.loadActive();
        } catch (RuntimeException e) {
            // No compile DB available (standalone run without bom.db) — skip
            assumeTrue(false, "BuildingRegistry unavailable: " + e.getMessage());
            return List.of();
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (BuildingEntry b : buildings) {
            if (b.hasReference() && GATE_SCOPE.contains(b.docTypeId())) {
                tests.add(DynamicTest.dynamicTest(
                    "W-TOT " + b.docTypeId(), () -> runTotality(b)));
            }
        }
        return tests;
    }

    private void runTotality(BuildingEntry b) {
        String tag = b.docTypeId();
        DiffReport report = SpatialDiff.diff(b.referenceDbPath(), b.outputDbPath());

        // W-TOT-1/2: Every paired element within tolerance
        List<ElementDelta> violations = report.deltas().stream()
            .filter(d -> d.band() != Band.EXACT)
            .toList();

        if (!violations.isEmpty()) {
            System.err.printf("[W-TOT] %s: %d violations (of %d elements)%n",
                tag, violations.size(), report.deltas().size());
            System.err.println(report.summary());
        }

        // Assert no DRIFT or SHIFT
        assertEquals(0, report.drift(),
            String.format("[W-TOT] %s: %d elements drifted (>1mm, ≤50mm)%n%s",
                tag, report.drift(), report.summary()));
        assertEquals(0, report.shift(),
            String.format("[W-TOT] %s: %d elements shifted (>50mm)%n%s",
                tag, report.shift(), report.summary()));

        // W-TOT-3: Bijection — no missing or extra
        assertEquals(0, report.missing(),
            String.format("[W-TOT] %s: %d elements in reference missing from output%n%s",
                tag, report.missing(), report.summary()));
        assertEquals(0, report.extra(),
            String.format("[W-TOT] %s: %d extra elements in output not in reference%n%s",
                tag, report.extra(), report.summary()));

        // All exact
        assertEquals(report.deltas().size(), report.exact(),
            String.format("[W-TOT] %s: only %d/%d exact matches%n%s",
                tag, report.exact(), report.deltas().size(), report.summary()));

        System.out.printf("[W-TOT] %s PASS: %d/%d elements exact match within %.0fmm%n",
            tag, report.exact(), report.deltas().size(), TOLERANCE_MM);
    }
}
