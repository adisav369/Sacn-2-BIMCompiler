package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.BuildingDefinition.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 17: Below Constraint Test
 *
 * Tests the below: constraint where a room on a lower storey
 * aligns with a room on a higher storey (with explicit position).
 */
public class BelowConstraintTest {

    private static final double TOLERANCE = 0.005;

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 17: below: CONSTRAINT TEST");
        System.out.println("=".repeat(70));

        String dsl = Files.readString(Path.of("examples/below_constraint.bim"));
        System.out.println("\nDSL:");
        System.out.println(dsl);

        // Parse
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 1: PARSE below: CONSTRAINT");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);

        RoomDef kitchenG = null;
        for (StoreyDef storey : def.storeys()) {
            for (RoomDef room : storey.rooms()) {
                if (room.name().equals("kitchen_g")) {
                    kitchenG = room;
                }
            }
        }

        System.out.println("kitchen_g.below = " + (kitchenG != null ? kitchenG.below() : "NOT FOUND"));
        boolean parsePass = kitchenG != null && "kitchen_upper".equals(kitchenG.below());
        System.out.println(parsePass ? "[PASS] below: constraint parsed" : "[FAIL] below: not parsed");

        // Compile
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 2: COMPILE AND VERIFY ALIGNMENT");
        System.out.println("-".repeat(70));

        BuildingSpec spec = BuildingCompiler.compile(def);

        RoomSpec kitchenGSpec = null;
        RoomSpec kitchenUpperSpec = null;
        for (StoreySpec storey : spec.storeys()) {
            for (RoomSpec room : storey.rooms()) {
                if (room.name().equals("kitchen_g")) kitchenGSpec = room;
                if (room.name().equals("kitchen_upper")) kitchenUpperSpec = room;
            }
        }

        if (kitchenGSpec == null || kitchenUpperSpec == null) {
            System.out.println("[FAIL] Could not find both kitchens");
            return;
        }

        System.out.println("\nKitchen positions:");
        System.out.printf("  kitchen_upper: X=[%.2f, %.2f], Y=[%.2f, %.2f], Z=[%.2f, %.2f]%n",
            kitchenUpperSpec.minX(), kitchenUpperSpec.maxX(),
            kitchenUpperSpec.minY(), kitchenUpperSpec.maxY(),
            kitchenUpperSpec.minZ(), kitchenUpperSpec.maxZ());
        System.out.printf("  kitchen_g:     X=[%.2f, %.2f], Y=[%.2f, %.2f], Z=[%.2f, %.2f]%n",
            kitchenGSpec.minX(), kitchenGSpec.maxX(),
            kitchenGSpec.minY(), kitchenGSpec.maxY(),
            kitchenGSpec.minZ(), kitchenGSpec.maxZ());

        double xDelta = Math.abs(kitchenGSpec.minX() - kitchenUpperSpec.minX());
        double yDelta = Math.abs(kitchenGSpec.minY() - kitchenUpperSpec.minY());

        System.out.printf("\nAlignment: X delta=%.4fm, Y delta=%.4fm%n", xDelta, yDelta);

        boolean alignPass = xDelta < TOLERANCE && yDelta < TOLERANCE;
        System.out.println(alignPass ? "[PASS] kitchen_g aligned below kitchen_upper" :
                                       "[FAIL] kitchens not aligned");

        // Verify Z ordering
        boolean zPass = kitchenGSpec.maxZ() <= kitchenUpperSpec.minZ() + 0.15;  // Allow slab overlap
        System.out.println(zPass ? "[PASS] Z ordering correct (ground below upper)" :
                                   "[FAIL] Z ordering incorrect");

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));

        int passed = (parsePass ? 1 : 0) + (alignPass ? 1 : 0) + (zPass ? 1 : 0);
        System.out.printf("Passed: %d / 3%n", passed);

        if (passed == 3) {
            System.out.println("\nOVERALL: PASS - below: constraint working!");
            System.out.println("  kitchen_g aligns with kitchen_upper on storey above");
        } else {
            System.out.println("\nOVERALL: FAIL");
        }
        System.out.println("=".repeat(70));
    }
}
