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
 * WYSIWYG Totality Test — every ARC/STC element in reference exists in output with matching AABB.
 *
 * <p><b>Purpose:</b> G1-COUNT proves total element count matches. G3-DIGEST proves the
 * aggregate spatial hash matches. But neither identifies WHICH element is wrong or by how
 * much. This test opens reference and output side by side and asserts per-element AABB
 * identity within 1mm tolerance, restricted to ARC/STC classes.
 *
 * <p><b>Scope:</b> ARC/STC classes only. DISC devices (MEP terminals, routing runs) obey
 * validation rules, not IFC survey positions — they are excluded from this test.
 * See {@link ExtractedGeometryTruthTest} T3-DISC-COUNT for DISC count verification.
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
 *   <li>W-TOT-ARC-1: SH — every ARC/STC element matches output AABB within 1mm</li>
 *   <li>W-TOT-ARC-2: DX — every ARC/STC element matches output AABB within 1mm</li>
 *   <li>W-TOT-ARC-3: Bijection — no extra ARC/STC elements in output not in reference</li>
 * </ul>
 *
 * <p>Addresses Gap 5 (LAST_MILE_PROBLEM.md): "No test opens input and output side by side
 * and confirms every element matches by identity."
 *
 * @Traces AUDIT_20260402.txt §3 — discipline-split fidelity
 * @see SpatialDiff — the per-element diff engine
 * @see SpotCheckContractTest — spot-checks 5 elements (this tests ALL)
 * @see RosettaStoneGateTest — the 6-gate framework
 */
// Implementing AUDIT_20260402.txt §3 — Witness: W-TOT-ARC-1, W-TOT-ARC-2, W-TOT-ARC-3
class TotalityContractTest {

    private static final double TOLERANCE_MM = 1.0;
    private static final Set<String> GATE_SCOPE = Set.of("RE_SH", "RE_DX", "CO_TE");

    /**
     * ARC/STC IFC classes — only these are subject to position fidelity in this test.
     * DISC devices obey validation rules, not IFC survey. See ExtractedGeometryTruthTest T3-DISC-COUNT.
     */
    private static final Set<String> ARC_STC_FILTER = Set.of(
        "IfcWall", "IfcWallStandardCase", "IfcSlab", "IfcRoof", "IfcColumn", "IfcBeam",
        "IfcMember", "IfcPlate", "IfcWindow", "IfcDoor", "IfcStair", "IfcStairFlight",
        "IfcRamp", "IfcRampFlight", "IfcCovering", "IfcFurnishingElement",
        "IfcSpace", "IfcBuildingElementProxy", "IfcFooting", "IfcPile"
    );

    @TestFactory
    @DisplayName("W-TOT-ARC: WYSIWYG totality — every ARC/STC element matches within 1mm")
    Collection<DynamicTest> totalityTests() {
        List<BuildingEntry> buildings;
        try {
            buildings = BuildingRegistry.loadActive();
        } catch (RuntimeException e) {
            fail("BuildingRegistry unavailable: " + e.getMessage());
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
        // W-TOT-ARC-1/2/3: ARC/STC only — DISC devices excluded (position governed by validation rules)
        DiffReport report = SpatialDiff.diff(b.referenceDbPath(), b.outputDbPath(), ARC_STC_FILTER);

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
