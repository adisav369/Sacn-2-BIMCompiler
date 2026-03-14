package com.bim.compiler.dsl;

import com.bim.compiler.library.PlumbingPlacer;
import com.bim.compiler.library.PlumbingPlacer.*;
import com.bim.compiler.system.*;
import com.bim.compiler.witness.WitnessBuilder;

import java.util.*;

/**
 * Phase 38: Multi-Storey Support Test
 *
 * Tests the new Phase 38 features in isolation:
 * 1. Vertical MEP edges connecting stacks across storeys
 * 2. Stack alignment validation
 * 3. STOREYS_VERTICALLY_CONSISTENT witness
 *
 * Note: Full integration with BuildingCompiler is tested via IntegratedTownhouseTest
 */
public class MultiStoreyTest {

    private static final double TOLERANCE = 0.005;  // 5mm

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 38: MULTI-STOREY SUPPORT TEST");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        // =====================================================================
        // TEST 1: Vertical MEP edges for plumbing stack
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 1: VERTICAL MEP EDGES (CONNECTS_VERTICAL)");
        System.out.println("-".repeat(70));

        // Simulate a 2-storey building with stacked bathrooms
        // bath_g at level 0, bath_u at level 1, both in "plumbing_core" stack
        Map<String, List<StackRoomInfo>> stackRooms = new HashMap<>();
        stackRooms.put("plumbing_core", List.of(
            new StackRoomInfo("bath_g", 0, 0.0, 2.8, 8.0, 1.5),
            new StackRoomInfo("bath_u", 1, 2.8, 5.6, 8.0, 1.5)
        ));

        // Generate pipes for both bathrooms
        PlumbingPlacer placer = new PlumbingPlacer();
        List<PipeInstance> allPipes = new ArrayList<>();

        // Waste risers for both bathrooms
        allPipes.add(new PipeInstance(
            PipeType.WASTE_RISER,
            new com.bim.compiler.geometry.Point3D(8.0, 1.5, 0.0),
            new com.bim.compiler.geometry.Point3D(8.0, 1.5, 2.8),
            0.100, "bath_g_waste_riser"));
        allPipes.add(new PipeInstance(
            PipeType.WASTE_RISER,
            new com.bim.compiler.geometry.Point3D(8.0, 1.5, 2.8),
            new com.bim.compiler.geometry.Point3D(8.0, 1.5, 5.6),
            0.100, "bath_u_waste_riser"));

        // Branch pipes (fixtures connecting to risers)
        allPipes.add(new PipeInstance(
            PipeType.BRANCH_PIPE,
            new com.bim.compiler.geometry.Point3D(7.5, 1.5, 0.0),
            new com.bim.compiler.geometry.Point3D(8.0, 1.5, 0.0),
            0.100, "bath_g_toilet_branch"));
        allPipes.add(new PipeInstance(
            PipeType.BRANCH_PIPE,
            new com.bim.compiler.geometry.Point3D(7.5, 1.5, 2.8),
            new com.bim.compiler.geometry.Point3D(8.0, 1.5, 2.8),
            0.100, "bath_u_toilet_branch"));

        // Build multi-storey waste graph
        MEPSystem wasteSystem = placer.buildMultiStoreyWasteGraph(allPipes, stackRooms);

        System.out.println("MEPSystem: " + wasteSystem);
        System.out.println("  Nodes: " + wasteSystem.getNodes().size());
        System.out.println("  Edges: " + wasteSystem.getEdges().size());

        // Check for vertical edge
        boolean hasVerticalEdge = false;
        int verticalEdgeCount = 0;
        for (SystemEdge edge : wasteSystem.getEdges()) {
            System.out.printf("  Edge: %s (%s -> %s)%n",
                edge.type(), edge.fromNodeId(), edge.toNodeId());
            if (edge.type() == EdgeType.CONNECTS_VERTICAL) {
                hasVerticalEdge = true;
                verticalEdgeCount++;
                System.out.printf("    -> VERTICAL: stack=%s, from_level=%s, to_level=%s%n",
                    edge.properties().get("stack_name"),
                    edge.properties().get("from_level"),
                    edge.properties().get("to_level"));
            }
        }

        boolean systemConnected = wasteSystem.isConnected();
        System.out.printf("\nVertical edges: %d%n", verticalEdgeCount);
        System.out.printf("System connected: %s%n", systemConnected);

        boolean test1Pass = hasVerticalEdge && systemConnected && verticalEdgeCount == 1;
        System.out.println(test1Pass ? "[PASS] Vertical MEP edges created and system connected" :
            "[FAIL] Missing vertical edge or system disconnected");
        if (test1Pass) passed++; else failed++;

        // =====================================================================
        // TEST 2: Stack alignment validation
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 2: STACK ALIGNMENT VALIDATION");
        System.out.println("-".repeat(70));

        // Test aligned stacks
        Map<String, StackAlignmentResult> alignmentResults =
            PlumbingPlacer.validateMultiStoreyAlignment(stackRooms, TOLERANCE);

        boolean allAligned = true;
        for (var entry : alignmentResults.entrySet()) {
            StackAlignmentResult result = entry.getValue();
            System.out.printf("Stack '%s': aligned=%s, maxDeltaX=%.4fm, maxDeltaY=%.4fm%n",
                entry.getKey(), result.aligned(), result.maxDeltaX(), result.maxDeltaY());
            if (!result.aligned()) allAligned = false;
        }

        // Test misaligned stack
        System.out.println("\nTesting misaligned stack:");
        Map<String, List<StackRoomInfo>> misalignedStack = new HashMap<>();
        misalignedStack.put("bad_stack", List.of(
            new StackRoomInfo("room_g", 0, 0.0, 2.8, 5.0, 3.0),
            new StackRoomInfo("room_u", 1, 2.8, 5.6, 5.1, 3.0)  // 0.1m X offset
        ));

        Map<String, StackAlignmentResult> badResults =
            PlumbingPlacer.validateMultiStoreyAlignment(misalignedStack, TOLERANCE);

        boolean badStackFails = false;
        for (var entry : badResults.entrySet()) {
            StackAlignmentResult result = entry.getValue();
            System.out.printf("Stack '%s': aligned=%s, maxDeltaX=%.4fm (should fail)%n",
                entry.getKey(), result.aligned(), result.maxDeltaX());
            if (!result.aligned()) badStackFails = true;
        }

        boolean test2Pass = allAligned && badStackFails;
        System.out.println(test2Pass ? "[PASS] Stack alignment validation works correctly" :
            "[FAIL] Stack alignment validation issue");
        if (test2Pass) passed++; else failed++;

        // =====================================================================
        // TEST 3: STOREYS_VERTICALLY_CONSISTENT witness
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 3: STOREYS_VERTICALLY_CONSISTENT WITNESS");
        System.out.println("-".repeat(70));

        WitnessBuilder witness = new WitnessBuilder("TB-LKTN-2S");

        // Record vertical constraint: master above living
        witness.verticalConstraint("master", "above", "living",
            2.5, 2.0, 2.8,   // master center and Z
            2.5, 2.0, 0.0);  // living center and Z (same X,Y, lower Z)

        // Record vertical constraint: child above kitchen
        witness.verticalConstraint("child", "above", "kitchen",
            6.5, 1.5, 2.8,   // child center and Z
            6.5, 1.5, 0.0);  // kitchen center and Z

        // Record stack alignment
        StackAlignmentResult plumbingResult = alignmentResults.get("plumbing_core");
        witness.stackAlignment("plumbing_core", plumbingResult.aligned(),
            plumbingResult.maxDeltaX(), plumbingResult.maxDeltaY(), 2);

        // Build witness
        Map<String, Object> witnessData = witness.build();

        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) witnessData.get("claims");

        @SuppressWarnings("unchecked")
        Map<String, Object> verticalClaim =
            (Map<String, Object>) claims.get("STOREYS_VERTICALLY_CONSISTENT");

        String claimStatus = (String) verticalClaim.get("status");
        System.out.println("Claim status: " + claimStatus);

        @SuppressWarnings("unchecked")
        Map<String, Object> w = (Map<String, Object>) verticalClaim.get("witness");
        if (w != null) {
            System.out.println("Witness data:");
            System.out.println("  - Tolerance: " + w.get("tolerance_m") + "m");
            System.out.println("  - Violations: " + w.get("violations"));
        }

        boolean test3Pass = "PROVEN".equals(claimStatus);
        System.out.println(test3Pass ? "[PASS] Witness PROVEN" : "[FAIL] Witness not PROVEN");
        if (test3Pass) passed++; else failed++;

        // =====================================================================
        // TEST 4: Witness with violations
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 4: WITNESS WITH VIOLATIONS");
        System.out.println("-".repeat(70));

        WitnessBuilder badWitness = new WitnessBuilder("bad-building");

        // Misaligned vertical constraint (X differs)
        badWitness.verticalConstraint("upper", "above", "lower",
            5.0, 3.0, 2.8,   // upper center and Z
            5.1, 3.0, 0.0);  // lower center (0.1m X offset - should fail)

        // Misaligned stack
        badWitness.stackAlignment("bad_stack", false, 0.1, 0.0, 2);

        Map<String, Object> badWitnessData = badWitness.build();

        @SuppressWarnings("unchecked")
        Map<String, Object> badClaims = (Map<String, Object>) badWitnessData.get("claims");

        @SuppressWarnings("unchecked")
        Map<String, Object> badVerticalClaim =
            (Map<String, Object>) badClaims.get("STOREYS_VERTICALLY_CONSISTENT");

        String badStatus = (String) badVerticalClaim.get("status");
        System.out.println("Bad claim status: " + badStatus);

        @SuppressWarnings("unchecked")
        Map<String, Object> badW = (Map<String, Object>) badVerticalClaim.get("witness");
        if (badW != null) {
            System.out.println("  - Violations: " + badW.get("violations"));
        }

        boolean test4Pass = "UNPROVABLE".equals(badStatus);
        System.out.println(test4Pass ? "[PASS] Invalid building correctly marked UNPROVABLE" :
            "[FAIL] Invalid building should be UNPROVABLE");
        if (test4Pass) passed++; else failed++;

        // =====================================================================
        // TEST 5: EdgeType enum has CONNECTS_VERTICAL
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 5: EDGETYPE ENUM");
        System.out.println("-".repeat(70));

        boolean hasConnectsVertical = false;
        for (EdgeType et : EdgeType.values()) {
            System.out.println("  " + et);
            if (et == EdgeType.CONNECTS_VERTICAL) {
                hasConnectsVertical = true;
            }
        }

        boolean test5Pass = hasConnectsVertical;
        System.out.println(test5Pass ? "[PASS] CONNECTS_VERTICAL edge type exists" :
            "[FAIL] CONNECTS_VERTICAL edge type missing");
        if (test5Pass) passed++; else failed++;

        // =====================================================================
        // SUMMARY
        // =====================================================================
        System.out.println("\n" + "=".repeat(70));
        System.out.println("PHASE 38 TEST SUMMARY");
        System.out.println("=".repeat(70));

        System.out.println("1. Vertical MEP Edges:     " + (passed >= 1 ? "PASS" : "FAIL"));
        System.out.println("2. Stack Alignment:        " + (passed >= 2 ? "PASS" : "FAIL"));
        System.out.println("3. Witness PROVEN:         " + (passed >= 3 ? "PASS" : "FAIL"));
        System.out.println("4. Witness UNPROVABLE:     " + (passed >= 4 ? "PASS" : "FAIL"));
        System.out.println("5. EdgeType enum:          " + (passed >= 5 ? "PASS" : "FAIL"));

        System.out.println();
        System.out.printf("PASSED: %d / %d%n", passed, passed + failed);

        System.out.println("\n" + "=".repeat(70));
        if (passed == 5) {
            System.out.println("OVERALL: PASS - Phase 38 Multi-Storey Support Complete!");
            System.out.println("=".repeat(70));
            System.out.println("\nNew capabilities:");
            System.out.println("  - CONNECTS_VERTICAL edge type for plumbing stacks");
            System.out.println("  - buildMultiStoreyWasteGraph() for stack connections");
            System.out.println("  - validateMultiStoreyAlignment() for stack validation");
            System.out.println("  - STOREYS_VERTICALLY_CONSISTENT witness (claim #14)");
            System.out.println("  - verticalConstraint() and stackAlignment() in WitnessBuilder");
        } else {
            System.out.println("OVERALL: FAIL - " + failed + " test(s) failed");
            System.out.println("=".repeat(70));
            System.exit(1);
        }
    }
}
