package com.bim.compiler.contract;

import com.bim.compiler.dsl.BuildingRegistry;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Rotation Contract Test — AABB width/depth alignment of doors and windows.
 *
 * <p><b>Purpose:</b> MeshBinder.java:84-97 computes rotation by comparing mesh
 * X-extent to AABB width vs depth — a heuristic. No test verifies the chosen
 * rotation matches the reference. A door rotated 90 degrees passes all gates
 * (count, volume, even digest if both W and D swap symmetrically).
 *
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>For each IfcDoor/IfcWindow in the reference DB, determine if width &gt; depth (EW)
 *       or depth &gt; width (NS) — the "orientation signature"</li>
 *   <li>Do the same for the output DB, matching by position sort order within each class</li>
 *   <li>Assert orientation matches for every paired element</li>
 * </ol>
 *
 * <p><b>Why this matters:</b> A 90-degree rotation swaps width and depth. If the reference
 * door is 810mm wide x 135mm deep (EW orientation), but the output door is 135mm wide x
 * 810mm deep (NS), the door has been rotated. AABB volume is unchanged, digest may even
 * match if all doors swap consistently — but the visual result is wrong.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-ROT-1: For each IfcDoor/IfcWindow in SH output, width/depth alignment matches reference</li>
 *   <li>W-ROT-2: No opening has AABB width and depth swapped vs reference</li>
 * </ul>
 *
 * <p>Addresses Gap 3b (LAST_MILE_PROBLEM.md): "Rotation untested."
 *
 * @see SpotCheckContractTest — per-element AABB checks (includes doors/windows)
 * @see RosettaStoneGateTest — the 6-gate framework
 */
class RotationContractTest {

    private static final Set<String> GATE_SCOPE = Set.of("RE_SH", "RE_DX");
    private static final Set<String> OPENING_CLASSES = Set.of("IfcDoor", "IfcWindow");

    /** Orientation signature: EW if width > depth, NS if depth > width, SQUARE if equal. */
    private enum Orientation { EW, NS, SQUARE }

    private record OrientedElement(
        String ifcClass,
        int indexInClass,
        double width,   // maxX - minX
        double depth,   // maxY - minY
        Orientation orientation
    ) {}

    @TestFactory
    @DisplayName("W-ROT: Opening rotation — width/depth alignment matches reference")
    Collection<DynamicTest> rotationTests() {
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
                    "W-ROT " + b.docTypeId(), () -> runRotation(b)));
            }
        }
        return tests;
    }

    private void runRotation(BuildingEntry b) throws Exception {
        String tag = b.docTypeId();
        Map<String, List<OrientedElement>> refElements = loadOpenings(b.referenceDbPath());
        Map<String, List<OrientedElement>> outElements = loadOpenings(b.outputDbPath());

        int checked = 0;
        int swapped = 0;
        List<String> violations = new ArrayList<>();

        for (String cls : OPENING_CLASSES) {
            List<OrientedElement> refList = refElements.getOrDefault(cls, List.of());
            List<OrientedElement> outList = outElements.getOrDefault(cls, List.of());

            assumeTrue(!refList.isEmpty(),
                tag + " has no " + cls + " in reference — skip rotation check");

            int paired = Math.min(refList.size(), outList.size());
            for (int i = 0; i < paired; i++) {
                OrientedElement ref = refList.get(i);
                OrientedElement out = outList.get(i);
                checked++;

                // Skip SQUARE elements (width ≈ depth — rotation is ambiguous)
                if (ref.orientation == Orientation.SQUARE || out.orientation == Orientation.SQUARE) {
                    continue;
                }

                if (ref.orientation != out.orientation) {
                    swapped++;
                    violations.add(String.format(
                        "%s #%d: ref=%.0fmm x %.0fmm (%s) → out=%.0fmm x %.0fmm (%s)",
                        cls, i,
                        ref.width * 1000, ref.depth * 1000, ref.orientation,
                        out.width * 1000, out.depth * 1000, out.orientation));
                }
            }
        }

        if (!violations.isEmpty()) {
            System.err.printf("[W-ROT] %s: %d/%d openings have swapped width/depth:%n",
                tag, swapped, checked);
            violations.forEach(v -> System.err.println("  " + v));
        }

        assertEquals(0, swapped,
            String.format("[W-ROT] %s: %d opening(s) have width/depth swapped vs reference:%n  %s",
                tag, swapped, String.join("\n  ", violations)));

        System.out.printf("[W-ROT] %s PASS: %d openings checked, all orientations match%n",
            tag, checked);
    }

    /**
     * Load door/window elements sorted in SpatialDigest order, compute orientation.
     */
    private static Map<String, List<OrientedElement>> loadOpenings(String dbPath) throws SQLException {
        String sql = """
            SELECT em.ifc_class,
                   r.minX, r.maxX, r.minY, r.maxY
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.ifc_class IN ('IfcDoor', 'IfcWindow')
            ORDER BY em.ifc_class,
                     ROUND(r.minX * 1000), ROUND(r.minY * 1000), ROUND(r.minZ * 1000),
                     ROUND(r.maxX * 1000), ROUND(r.maxY * 1000), ROUND(r.maxZ * 1000)
            """;

        Map<String, List<OrientedElement>> result = new TreeMap<>();
        Map<String, Integer> classIndex = new HashMap<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String cls = rs.getString(1);
                double width = rs.getDouble(3) - rs.getDouble(2);   // maxX - minX
                double depth = rs.getDouble(5) - rs.getDouble(4);   // maxY - minY

                // 1mm tolerance for SQUARE detection
                Orientation orient;
                if (Math.abs(width - depth) < 0.001) {
                    orient = Orientation.SQUARE;
                } else {
                    orient = width > depth ? Orientation.EW : Orientation.NS;
                }

                int idx = classIndex.merge(cls, 0, (old, v) -> old + 1);
                result.computeIfAbsent(cls, k -> new ArrayList<>())
                    .add(new OrientedElement(cls, idx, width, depth, orient));
            }
        }
        return result;
    }
}
