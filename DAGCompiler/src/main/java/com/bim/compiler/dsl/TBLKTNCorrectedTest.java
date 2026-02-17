package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import java.nio.file.*;

/**
 * TB-LKTN Corrected Layout Test
 * Verifies the corrected layout matches the PDF verification.
 */
public class TBLKTNCorrectedTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("TB-LKTN CORRECTED LAYOUT TEST");
        System.out.println("=".repeat(70));

        String dsl = Files.readString(Path.of("examples/tb_lktn_corrected.dsl"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        // Verify grid
        System.out.println("\n1. GRID VERIFICATION");
        System.out.println("-".repeat(70));
        GridDef grid = def.grid();
        if (grid != null) {
            System.out.println("X Axes: " + grid.xAxes());
            System.out.println("Y Axes: " + grid.yAxes());
            System.out.println("\nGrid positions (from PDF):");
            System.out.println("  A = 0mm (left edge)");
            System.out.println("  B = 1300mm");
            System.out.println("  C = 4400mm");
            System.out.println("  D = 8100mm");
            System.out.println("  E = 11200mm (right edge)");
            System.out.println("\nCalculated grid positions:");
            for (String x : grid.xAxes()) {
                System.out.printf("  %s -> X=%.0fmm%n", x, grid.getX(x) * 1000);
            }
        }

        // Verify rooms with grid bounds
        System.out.println("\n2. ROOM LAYOUT VERIFICATION");
        System.out.println("-".repeat(70));

        var storey = def.storeys().get(0);
        System.out.printf("Rooms: %d%n%n", storey.rooms().size());

        // Expected layout from PDF:
        String[][] expected = {
            {"anjung", "C1-D2", "3.7", "2.3", "PORCH at front"},
            {"bilik_utama", "A2-C3", "4.4", "3.1", "Master at front-left"},
            {"ruang_tamu", "C2-D3", "3.7", "3.1", "Living at front-center"},
            {"bilik_2", "D2-E3", "3.1", "3.1", "Bed2 at front-right"},
            {"bilik_mandi", "A3-B4", "1.3", "1.6", "Bath behind master"},
            {"ruang_makan", "C3-D4", "3.7", "1.6", "Dining in middle"},
            {"bilik_3", "D3-E5", "3.1", "3.1", "Bed3 at right, spans 2 rows"},
            {"tandas", "A4-B5", "1.3", "1.5", "Toilet back-left"},
            {"ruang_basuh", "B4-C5", "3.1", "1.5", "Laundry back"},
            {"dapur", "C4-D5", "3.7", "1.5", "Kitchen back-center"},
        };

        int match = 0;
        int total = expected.length;

        for (String[] exp : expected) {
            String name = exp[0];
            String bounds = exp[1];
            double expW = Double.parseDouble(exp[2]);
            double expD = Double.parseDouble(exp[3]);
            String desc = exp[4];

            // Find room
            RoomDef room = storey.rooms().stream()
                .filter(r -> r.name().equals(name))
                .findFirst().orElse(null);

            if (room == null) {
                System.out.printf("[MISS] %s - NOT FOUND%n", name);
                continue;
            }

            String actualBounds = room.gridBounds();
            if (actualBounds == null) actualBounds = "(no bounds)";

            double[] dims = {0, 0};
            if (room.hasGridBounds() && grid != null) {
                GridBounds gb = room.getParsedGridBounds();
                if (gb != null) {
                    dims = gb.getDimensions(grid);
                }
            }

            boolean ok = Math.abs(dims[0] - expW) < 0.2 && Math.abs(dims[1] - expD) < 0.2;
            if (ok) match++;

            System.out.printf("%s %-14s bounds:%-8s size:%.1fx%.1fm (exp:%.1fx%.1f) - %s%n",
                ok ? "[OK]" : "[!!]",
                name, actualBounds,
                dims[0], dims[1], expW, expD, desc);
        }

        // Adjacency verification
        System.out.println("\n3. ADJACENCY VERIFICATION");
        System.out.println("-".repeat(70));

        // From PDF: RUANG TAMU is the circulation hub
        RoomDef ruangTamu = storey.rooms().stream()
            .filter(r -> r.name().equals("ruang_tamu")).findFirst().orElse(null);

        if (ruangTamu != null) {
            System.out.println("RUANG TAMU adjacencies (circulation hub):");
            System.out.println("  Declared: " + ruangTamu.adjacentTo());
            System.out.println("  Expected: [anjung, bilik_utama, bilik_2, ruang_makan]");

            boolean hasAll = ruangTamu.adjacentTo().containsAll(
                java.util.List.of("anjung", "bilik_utama", "bilik_2", "ruang_makan"));
            System.out.println("  Match: " + (hasAll ? "YES" : "NO"));
        }

        // Circulation path
        System.out.println("\n4. CIRCULATION PATH");
        System.out.println("-".repeat(70));
        System.out.println("Entry → anjung → ruang_tamu → {bilik_utama, bilik_2, ruang_makan}");
        System.out.println("                          ruang_makan → dapur → ruang_basuh");
        System.out.println("                                                ↓");
        System.out.println("                          Wet zone: bilik_mandi ↔ tandas");

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.printf("LAYOUT MATCH: %d/%d rooms correctly positioned%n", match, total);
        System.out.println("=".repeat(70));

        if (match == total) {
            System.out.println("\n[SUCCESS] Layout matches PDF verification!");
        } else {
            System.out.println("\n[REVIEW] Some dimensions differ - check grid spacing");
        }
    }
}
