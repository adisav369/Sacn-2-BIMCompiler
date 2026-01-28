package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.BuildingDefinition.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 17: Vertical Constraint Test
 *
 * Tests the extended vertical vocabulary:
 * - above: Room must be directly above another room
 * - below: Room must be directly below another room
 * - stack: Named vertical group (all rooms share X,Y)
 *
 * Use case: MEP risers, structural alignment, service cores
 */
public class VerticalConstraintTest {

    private static final double TOLERANCE = 0.005; // 5mm

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 17: VERTICAL CONSTRAINT TEST (above:/below:/stack:)");
        System.out.println("=".repeat(70));

        // Parse highrise DSL
        String dsl = Files.readString(Path.of("examples/highrise_vertical.bim"));
        System.out.println("\nDSL:");
        System.out.println(dsl);

        // Test 1: Parse DSL with vertical constraints
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 1: PARSE DSL WITH VERTICAL CONSTRAINTS");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);

        // Verify parsing of above: constraint
        RoomDef bed1 = findRoom(def, "bed_1");
        RoomDef bed2 = findRoom(def, "bed_2");
        RoomDef bathG = findRoom(def, "bath_g");
        RoomDef bath1 = findRoom(def, "bath_1");
        RoomDef bath2 = findRoom(def, "bath_2");

        boolean parsePass = true;

        System.out.println("\nParsed constraints:");
        System.out.println("  bed_1.above = " + (bed1 != null ? bed1.above() : "NOT FOUND"));
        parsePass &= bed1 != null && "living_g".equals(bed1.above());

        System.out.println("  bed_2.above = " + (bed2 != null ? bed2.above() : "NOT FOUND"));
        parsePass &= bed2 != null && "bed_1".equals(bed2.above());

        System.out.println("  bath_g.stack = " + (bathG != null ? bathG.stack() : "NOT FOUND"));
        parsePass &= bathG != null && "plumbing_core".equals(bathG.stack());

        System.out.println("  bath_1.stack = " + (bath1 != null ? bath1.stack() : "NOT FOUND"));
        parsePass &= bath1 != null && "plumbing_core".equals(bath1.stack());

        System.out.println("  bath_2.stack = " + (bath2 != null ? bath2.stack() : "NOT FOUND"));
        parsePass &= bath2 != null && "plumbing_core".equals(bath2.stack());

        System.out.println(parsePass ? "\n[PASS] All constraints parsed correctly" :
                                       "\n[FAIL] Some constraints not parsed");

        // Test 2: Compile and verify above: alignment
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 2: VERIFY above: CONSTRAINT (BEDROOM STACK)");
        System.out.println("-".repeat(70));

        BuildingSpec spec = BuildingCompiler.compile(def);

        // Find bedroom positions
        RoomSpec livingG = findRoomSpec(spec, "living_g");
        RoomSpec bed1Spec = findRoomSpec(spec, "bed_1");
        RoomSpec bed2Spec = findRoomSpec(spec, "bed_2");

        System.out.println("\nBedroom stack positions:");
        System.out.printf("  living_g: X=[%.2f, %.2f], Y=[%.2f, %.2f]%n",
            livingG.minX(), livingG.maxX(), livingG.minY(), livingG.maxY());
        System.out.printf("  bed_1:    X=[%.2f, %.2f], Y=[%.2f, %.2f]%n",
            bed1Spec.minX(), bed1Spec.maxX(), bed1Spec.minY(), bed1Spec.maxY());
        System.out.printf("  bed_2:    X=[%.2f, %.2f], Y=[%.2f, %.2f]%n",
            bed2Spec.minX(), bed2Spec.maxX(), bed2Spec.minY(), bed2Spec.maxY());

        // Verify X,Y alignment
        double bed1XDelta = Math.abs(bed1Spec.minX() - livingG.minX());
        double bed1YDelta = Math.abs(bed1Spec.minY() - livingG.minY());
        double bed2XDelta = Math.abs(bed2Spec.minX() - bed1Spec.minX());
        double bed2YDelta = Math.abs(bed2Spec.minY() - bed1Spec.minY());

        System.out.printf("\nbed_1 vs living_g: X delta=%.4fm, Y delta=%.4fm%n", bed1XDelta, bed1YDelta);
        System.out.printf("bed_2 vs bed_1:    X delta=%.4fm, Y delta=%.4fm%n", bed2XDelta, bed2YDelta);

        boolean abovePass = bed1XDelta < TOLERANCE && bed1YDelta < TOLERANCE &&
                            bed2XDelta < TOLERANCE && bed2YDelta < TOLERANCE;

        System.out.println(abovePass ? "[PASS] above: constraint working - bedrooms vertically aligned" :
                                       "[FAIL] above: constraint NOT working");

        // Test 3: Verify stack: alignment
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 3: VERIFY stack: CONSTRAINT (PLUMBING CORE)");
        System.out.println("-".repeat(70));

        RoomSpec bathGSpec = findRoomSpec(spec, "bath_g");
        RoomSpec bath1Spec = findRoomSpec(spec, "bath_1");
        RoomSpec bath2Spec = findRoomSpec(spec, "bath_2");

        System.out.println("\nPlumbing core positions:");
        System.out.printf("  bath_g: X=[%.2f, %.2f], Y=[%.2f, %.2f]%n",
            bathGSpec.minX(), bathGSpec.maxX(), bathGSpec.minY(), bathGSpec.maxY());
        System.out.printf("  bath_1: X=[%.2f, %.2f], Y=[%.2f, %.2f]%n",
            bath1Spec.minX(), bath1Spec.maxX(), bath1Spec.minY(), bath1Spec.maxY());
        System.out.printf("  bath_2: X=[%.2f, %.2f], Y=[%.2f, %.2f]%n",
            bath2Spec.minX(), bath2Spec.maxX(), bath2Spec.minY(), bath2Spec.maxY());

        // Verify all bathrooms share same X,Y
        double stack1XDelta = Math.abs(bath1Spec.minX() - bathGSpec.minX());
        double stack1YDelta = Math.abs(bath1Spec.minY() - bathGSpec.minY());
        double stack2XDelta = Math.abs(bath2Spec.minX() - bathGSpec.minX());
        double stack2YDelta = Math.abs(bath2Spec.minY() - bathGSpec.minY());

        System.out.printf("\nbath_1 vs bath_g: X delta=%.4fm, Y delta=%.4fm%n", stack1XDelta, stack1YDelta);
        System.out.printf("bath_2 vs bath_g: X delta=%.4fm, Y delta=%.4fm%n", stack2XDelta, stack2YDelta);

        boolean stackPass = stack1XDelta < TOLERANCE && stack1YDelta < TOLERANCE &&
                            stack2XDelta < TOLERANCE && stack2YDelta < TOLERANCE;

        System.out.println(stackPass ? "[PASS] stack: constraint working - bathrooms in plumbing core" :
                                       "[FAIL] stack: constraint NOT working");

        // Test 4: Verify Z progression
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 4: VERIFY Z PROGRESSION (VERTICAL STACKING)");
        System.out.println("-".repeat(70));

        System.out.println("\nStorey Z ranges:");
        for (StoreySpec storey : spec.storeys()) {
            System.out.printf("  %s: Z=[%.2f, %.2f]%n",
                storey.name(), storey.baseZ(), storey.baseZ() + storey.height());
        }

        System.out.println("\nRoom Z ranges:");
        System.out.printf("  living_g: Z=[%.2f, %.2f]%n", livingG.minZ(), livingG.maxZ());
        System.out.printf("  bed_1:    Z=[%.2f, %.2f]%n", bed1Spec.minZ(), bed1Spec.maxZ());
        System.out.printf("  bed_2:    Z=[%.2f, %.2f]%n", bed2Spec.minZ(), bed2Spec.maxZ());

        // Verify Z ordering: bed_1.minZ > living_g.maxZ (with some overlap for slab)
        boolean zPass = bed1Spec.minZ() >= livingG.maxZ() - 0.15 &&
                        bed2Spec.minZ() >= bed1Spec.maxZ() - 0.15;

        System.out.println(zPass ? "[PASS] Z progression correct - rooms stack vertically" :
                                   "[FAIL] Z progression incorrect");

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        if (parsePass) { passed++; System.out.println("1. Parse constraints      PASS"); }
        else { failed++; System.out.println("1. Parse constraints      FAIL"); }

        if (abovePass) { passed++; System.out.println("2. above: alignment       PASS"); }
        else { failed++; System.out.println("2. above: alignment       FAIL"); }

        if (stackPass) { passed++; System.out.println("3. stack: alignment       PASS"); }
        else { failed++; System.out.println("3. stack: alignment       FAIL"); }

        if (zPass) { passed++; System.out.println("4. Z progression          PASS"); }
        else { failed++; System.out.println("4. Z progression          FAIL"); }

        System.out.println();
        System.out.printf("Passed: %d / %d%n", passed, passed + failed);

        System.out.println("\n" + "=".repeat(70));
        if (passed == 4) {
            System.out.println("OVERALL: PASS - Phase 17 vertical constraints working!");
            System.out.println("=".repeat(70));
            System.out.println("\nVertical vocabulary proven:");
            System.out.println("  above: Rooms align with room below (bed_1 above living_g)");
            System.out.println("  stack: Named group shares X,Y (plumbing_core across 3 storeys)");
            System.out.println("  Z progression: Rooms stack correctly in 3D");
        } else {
            System.out.println("OVERALL: FAIL - some tests failed");
            System.out.println("=".repeat(70));
        }
    }

    private static RoomDef findRoom(BuildingDefinition def, String name) {
        for (StoreyDef storey : def.storeys()) {
            for (RoomDef room : storey.rooms()) {
                if (room.name().equals(name)) {
                    return room;
                }
            }
        }
        return null;
    }

    private static RoomSpec findRoomSpec(BuildingSpec spec, String name) {
        for (StoreySpec storey : spec.storeys()) {
            for (RoomSpec room : storey.rooms()) {
                if (room.name().equals(name)) {
                    return room;
                }
            }
        }
        return null;
    }
}
