package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.dsl.BuildingCompiler.SprinklerSpec;

import java.util.*;

/**
 * Phase 80: Places fire suppression piping connecting sprinkler heads.
 *
 * Following the PlumbingPlacer pattern:
 * - FP_RISER: Vertical riser from pump room to storey ceiling
 * - FP_MAIN: Horizontal main running along ceiling
 * - FP_BRANCH: Short connection from main to sprinkler head
 *
 * NFPA 13 pipe sizing (Light Hazard):
 * - Riser: 100mm (4") for buildings up to 52,000 sqft
 * - Main: 65mm (2.5") for up to 40 heads
 * - Branch: 25mm (1") for up to 2 heads, 32mm (1.25") for 3+ heads
 *
 * BOM SET APPROACH:
 * - FP_RISER_ASSEMBLY: Riser pipe + tee fitting per floor
 * - FP_MAIN_ASSEMBLY: Main pipe + branch tees
 * - FP_BRANCH_ASSEMBLY: Branch pipe + sprinkler head connection
 */
public class FireSuppressionPlacer {

    // Phase 92C: Federation-matched pipe diameters (meters) — 27mm UPVC throughout
    private static final double FP_RISER_DIAMETER = 0.027;    // 27mm UPVC (federation standard)
    private static final double FP_MAIN_DIAMETER  = 0.027;    // 27mm UPVC
    private static final double FP_BRANCH_DIAMETER = 0.027;   // 27mm UPVC

    // Pipe routing offsets (meters)
    private static final double MAIN_OFFSET_FROM_CEILING = 0.15;   // Main runs 150mm below ceiling
    private static final double BRANCH_DROP = 0.05;                 // Branch drops 50mm to head

    /**
     * FP pipe segment specification.
     */
    public record FPPipeSpec(
        String id,
        FPPipeType type,
        Point3D start,
        Point3D end,
        double diameterM,
        String assemblyId,    // BOM set: groups related pipes
        int sequence          // Sequence within assembly for ordering
    ) {
        public double length() {
            return Math.sqrt(
                Math.pow(end.x() - start.x(), 2) +
                Math.pow(end.y() - start.y(), 2) +
                Math.pow(end.z() - start.z(), 2)
            );
        }
    }

    public enum FPPipeType {
        RISER,      // Vertical from pump room
        MAIN,       // Horizontal along ceiling
        BRANCH,     // Short connection to head
        TEE,        // Phase 92C: T-connector fitting on MAIN (IfcPipeFitting, LOD400)
        TRANSITION, // Phase 92C: Transition adaptor below tee (IfcPipeFitting, LOD400)
        DROP        // Phase 92C: Short drop pipe from transition to head (IfcPipeSegment, LOD400)
    }

    // Phase 92C: T-assembly offsets derived from federation maths.
    // Federation: main_cz=7.01488, tee_cz=7.00515, trans_cz=6.97161, drop_cz=6.93332, head_maxZ=6.91624
    // TEE sits 9.73mm below MAIN pipe center (not centered on it).
    public static final double TEE_BELOW_MAIN       = 0.009728;  // main_cz - tee_cz
    public static final double TRANSITION_BELOW_TEE  = 0.033544;  // tee_cz - trans_cz
    public static final double DROP_BELOW_TEE        = 0.071825;  // tee_cz - drop_cz
    public static final double HEAD_TOP_BELOW_TEE    = 0.088910;  // tee_cz - head_maxZ

    /**
     * FP assembly for BOM grouping.
     * Each assembly is a billable set of components.
     */
    public record FPAssembly(
        String assemblyId,
        FPAssemblyType type,
        List<FPPipeSpec> pipes,
        int sprinklerCount,
        double totalLengthM
    ) {}

    public enum FPAssemblyType {
        RISER_ASSEMBLY,     // Vertical riser segment
        MAIN_ASSEMBLY,      // Horizontal main with branches
        BRANCH_ASSEMBLY     // Individual branch to head
    }

    /**
     * Generate fire suppression piping for a storey.
     *
     * ALGORITHM:
     * 1. Group sprinklers by room
     * 2. Calculate main routing path (centroid of rooms)
     * 3. For each room: generate branches from main to heads
     * 4. Generate riser connection to main
     *
     * @param sprinklers List of sprinkler specs for this storey
     * @param storeyName Storey identifier
     * @param riserX X position of riser (usually near stairwell/pump room)
     * @param riserY Y position of riser
     * @param floorZ Floor level of this storey
     * @param ceilingZ Top of storey (top of slab)
     * @return List of FP pipe specs
     */
    public List<FPPipeSpec> generateStoreyPiping(
            List<SprinklerSpec> sprinklers,
            String storeyName,
            double riserX, double riserY,
            double floorZ, double ceilingZ) {
        // Phase 85: delegate to metadata-aware overload with default slab thickness
        return generateStoreyPiping(sprinklers, storeyName, riserX, riserY,
            floorZ, ceilingZ, 0.15, MAIN_OFFSET_FROM_CEILING);
    }

    /**
     * Phase 85+92B: Metadata-driven piping generation.
     *
     * Phase 92B rework:
     * - Single straight MAIN run at riserX along full floor Y-extent
     * - Per-sprinkler lateral BRANCH from MAIN to head position
     * - Dual end-wall RISER connections (south + north)
     *
     * @param slabThickness Slab thickness in meters (from BIMConstants or ad_floor_type)
     * @param mainOffset    Main pipe offset below slab bottom (from BOM params)
     */
    public List<FPPipeSpec> generateStoreyPiping(
            List<SprinklerSpec> sprinklers,
            String storeyName,
            double riserX, double riserY,
            double floorZ, double ceilingZ,
            double slabThickness, double mainOffset) {

        if (sprinklers.isEmpty()) {
            return List.of();
        }

        List<FPPipeSpec> pipes = new ArrayList<>();
        String assemblyPrefix = "FP_" + storeyName + "_";
        int pipeIndex = 0;

        // Phase 85: Main pipe below slab bottom, not below ceiling top
        double mainZ = ceilingZ - slabThickness - mainOffset;

        // Phase 92C: Compute full floor extents from sprinklers (with 1m margin)
        double floorMinX = sprinklers.stream().mapToDouble(SprinklerSpec::x).min().orElse(0) - 1.0;
        double floorMaxX = sprinklers.stream().mapToDouble(SprinklerSpec::x).max().orElse(0) + 1.0;
        double floorMinY = sprinklers.stream().mapToDouble(SprinklerSpec::y).min().orElse(0) - 1.0;
        double floorMaxY = sprinklers.stream().mapToDouble(SprinklerSpec::y).max().orElse(0) + 1.0;

        // Phase 92C: Split MAIN into segments between T-connectors
        // Sort sprinklers by Y to create segments: floorMinY → spr1.y → spr2.y → ... → floorMaxY
        List<SprinklerSpec> sorted = new ArrayList<>(sprinklers);
        sorted.sort((a, b) -> Double.compare(a.y(), b.y()));

        String mainAssembly = assemblyPrefix + "MAIN";
        double teeHalfWidth = 0.040;  // tee is ~80mm along Y (MAIN direction)
        double segStartY = floorMinY;
        int segIdx = 0;
        for (SprinklerSpec spr : sorted) {
            double teeY = spr.y();  // tee centered at sprinkler Y on the MAIN
            double segEndY = teeY - teeHalfWidth;
            if (segEndY > segStartY + 0.01) {
                pipes.add(new FPPipeSpec(
                    assemblyPrefix + "main_seg_" + segIdx++,
                    FPPipeType.MAIN,
                    new Point3D(riserX, segStartY, mainZ),
                    new Point3D(riserX, segEndY, mainZ),
                    FP_MAIN_DIAMETER, mainAssembly, segIdx
                ));
            }
            segStartY = teeY + teeHalfWidth;  // next segment starts after tee
        }
        // Final segment from last tee to floorMaxY
        if (floorMaxY > segStartY + 0.01) {
            pipes.add(new FPPipeSpec(
                assemblyPrefix + "main_seg_" + segIdx++,
                FPPipeType.MAIN,
                new Point3D(riserX, segStartY, mainZ),
                new Point3D(riserX, floorMaxY, mainZ),
                FP_MAIN_DIAMETER, mainAssembly, segIdx
            ));
        }

        // Phase 92C: T-assembly per sprinkler (federation pattern)
        // TEE is inline with MAIN pipe at (riserX, sprY, mainZ).
        // Federation proof: tee long arm (79mm) along pipe direction.
        // Our MAIN runs along Y → tee rotated π/2 to align arm with Y.
        // TRANSITION and DROP hang below tee at riserX (on pipe axis).
        int branchIndex = 0;
        for (SprinklerSpec sprinkler : sorted) {
            String tAssembly = assemblyPrefix + "T_" + branchIndex;
            double sprY = sprinkler.y();

            // All T-assembly parts at riserX (on the MAIN pipe), not at sprinkler.x()
            // Federation maths: TEE center is 9.73mm BELOW main pipe center
            double teeZ = mainZ - TEE_BELOW_MAIN;
            pipes.add(new FPPipeSpec(
                assemblyPrefix + "tee_" + branchIndex,
                FPPipeType.TEE,
                new Point3D(riserX, sprY, teeZ),
                new Point3D(riserX, sprY, teeZ),
                0, tAssembly, 1
            ));

            // Transition adaptor below tee
            double transZ = teeZ - TRANSITION_BELOW_TEE;
            pipes.add(new FPPipeSpec(
                assemblyPrefix + "trans_" + branchIndex,
                FPPipeType.TRANSITION,
                new Point3D(riserX, sprY, transZ),
                new Point3D(riserX, sprY, transZ),
                0, tAssembly, 2
            ));

            // Drop pipe below transition
            double dropZ = teeZ - DROP_BELOW_TEE;
            pipes.add(new FPPipeSpec(
                assemblyPrefix + "drop_" + branchIndex,
                FPPipeType.DROP,
                new Point3D(riserX, sprY, dropZ),
                new Point3D(riserX, sprY, dropZ),
                0, tAssembly, 3
            ));

            branchIndex++;
        }

        // Phase 92C: Risers at room X-extremes (not riserX center) to avoid blocking ceiling fan
        String riserAssembly = assemblyPrefix + "RISER";
        pipes.add(new FPPipeSpec(
            assemblyPrefix + "riser_south",
            FPPipeType.RISER,
            new Point3D(floorMinX, floorMinY, floorZ),
            new Point3D(floorMinX, floorMinY, mainZ),
            FP_RISER_DIAMETER,
            riserAssembly,
            0
        ));
        pipes.add(new FPPipeSpec(
            assemblyPrefix + "riser_north",
            FPPipeType.RISER,
            new Point3D(floorMaxX, floorMaxY, floorZ),
            new Point3D(floorMaxX, floorMaxY, mainZ),
            FP_RISER_DIAMETER,
            riserAssembly,
            1
        ));

        return pipes;
    }

    /**
     * Calculate optimal riser position for a building.
     * Places riser at centroid of all sprinklers to minimize pipe runs.
     *
     * @param allSprinklers All sprinklers in building
     * @return Optimal (x, y) for riser
     */
    public double[] calculateOptimalRiserPosition(List<SprinklerSpec> allSprinklers) {
        if (allSprinklers.isEmpty()) {
            return new double[]{0, 0};
        }

        double sumX = 0, sumY = 0;
        for (SprinklerSpec s : allSprinklers) {
            sumX += s.x();
            sumY += s.y();
        }

        return new double[]{
            sumX / allSprinklers.size(),
            sumY / allSprinklers.size()
        };
    }

    /**
     * Group pipes into BOM assemblies for procurement.
     *
     * @param pipes List of pipe specs
     * @return Map of assembly ID to assembly record
     */
    public Map<String, FPAssembly> groupIntoAssemblies(List<FPPipeSpec> pipes) {
        Map<String, List<FPPipeSpec>> byAssembly = new LinkedHashMap<>();
        for (FPPipeSpec pipe : pipes) {
            byAssembly.computeIfAbsent(pipe.assemblyId(), k -> new ArrayList<>()).add(pipe);
        }

        Map<String, FPAssembly> assemblies = new LinkedHashMap<>();
        for (var entry : byAssembly.entrySet()) {
            String assemblyId = entry.getKey();
            List<FPPipeSpec> assemblyPipes = entry.getValue();

            // Sort by sequence
            assemblyPipes.sort(Comparator.comparingInt(FPPipeSpec::sequence));

            // Determine assembly type
            FPAssemblyType type = FPAssemblyType.MAIN_ASSEMBLY;
            if (assemblyId.contains("RISER")) {
                type = FPAssemblyType.RISER_ASSEMBLY;
            } else if (assemblyId.contains("BRANCH")) {
                type = FPAssemblyType.BRANCH_ASSEMBLY;
            }

            // Count sprinklers (drops = sprinklers)
            int sprinklerCount = (int) assemblyPipes.stream()
                .filter(p -> p.id().contains("drop_"))
                .count();

            // Total length
            double totalLength = assemblyPipes.stream()
                .mapToDouble(FPPipeSpec::length)
                .sum();

            assemblies.put(assemblyId, new FPAssembly(
                assemblyId,
                type,
                assemblyPipes,
                sprinklerCount,
                totalLength
            ));
        }

        return assemblies;
    }

    /**
     * Generate BOM summary for fire suppression system.
     *
     * @param assemblies Map of assemblies
     * @return BOM entries for export
     */
    public List<BOMEntry> generateBOM(Map<String, FPAssembly> assemblies) {
        List<BOMEntry> bom = new ArrayList<>();

        // Aggregate by pipe diameter
        double riserLengthM = 0;
        double mainLengthM = 0;
        double branchLengthM = 0;
        int totalDrops = 0;

        for (FPAssembly assembly : assemblies.values()) {
            for (FPPipeSpec pipe : assembly.pipes()) {
                switch (pipe.type()) {
                    case RISER -> riserLengthM += pipe.length();
                    case MAIN -> mainLengthM += pipe.length();
                    case BRANCH -> branchLengthM += pipe.length();
                }
                if (pipe.id().contains("drop_")) {
                    totalDrops++;
                }
            }
        }

        // BOM entries
        if (riserLengthM > 0) {
            bom.add(new BOMEntry(
                "FP_PIPE_100MM",
                "Fire Suppression Pipe 100mm (4\")",
                "METER",
                Math.ceil(riserLengthM),
                "NFPA 13 Schedule 40"
            ));
        }
        if (mainLengthM > 0) {
            bom.add(new BOMEntry(
                "FP_PIPE_65MM",
                "Fire Suppression Pipe 65mm (2.5\")",
                "METER",
                Math.ceil(mainLengthM),
                "NFPA 13 Schedule 40"
            ));
        }
        if (branchLengthM > 0) {
            bom.add(new BOMEntry(
                "FP_PIPE_25MM",
                "Fire Suppression Pipe 25mm (1\")",
                "METER",
                Math.ceil(branchLengthM),
                "NFPA 13 Schedule 40"
            ));
        }

        // Fittings estimate: 1 tee per branch, 1 elbow per direction change
        int teeCount = totalDrops;  // Each drop needs a tee
        int elbowCount = assemblies.size() * 2;  // Estimate 2 elbows per assembly

        if (teeCount > 0) {
            bom.add(new BOMEntry(
                "FP_TEE_25MM",
                "Fire Suppression Tee 25mm",
                "EACH",
                teeCount,
                "Threaded"
            ));
        }
        if (elbowCount > 0) {
            bom.add(new BOMEntry(
                "FP_ELBOW_65MM",
                "Fire Suppression Elbow 65mm",
                "EACH",
                elbowCount,
                "90° Threaded"
            ));
        }

        return bom;
    }

    /**
     * BOM entry for fire suppression materials.
     */
    public record BOMEntry(
        String productCode,
        String description,
        String unit,
        double quantity,
        String specification
    ) {}

    /**
     * Test entry point.
     */
    public static void main(String[] args) {
        System.out.println("=== FireSuppressionPlacer Test ===\n");

        FireSuppressionPlacer placer = new FireSuppressionPlacer();

        // Create test sprinklers (simulating a floor with 2 rooms)
        List<SprinklerSpec> sprinklers = List.of(
            new SprinklerSpec("s1", "living", 2.0, 2.0, 2.8, "pendant", 4.0),
            new SprinklerSpec("s2", "living", 6.0, 2.0, 2.8, "pendant", 4.0),
            new SprinklerSpec("s3", "living", 2.0, 5.0, 2.8, "pendant", 4.0),
            new SprinklerSpec("s4", "living", 6.0, 5.0, 2.8, "pendant", 4.0),
            new SprinklerSpec("s5", "bedroom", 10.0, 2.0, 2.8, "pendant", 4.0),
            new SprinklerSpec("s6", "bedroom", 10.0, 5.0, 2.8, "pendant", 4.0)
        );

        // Calculate optimal riser position
        double[] riserPos = placer.calculateOptimalRiserPosition(sprinklers);
        System.out.printf("Optimal riser position: (%.2f, %.2f)%n", riserPos[0], riserPos[1]);

        // Generate piping
        List<FPPipeSpec> pipes = placer.generateStoreyPiping(
            sprinklers, "Ground",
            riserPos[0], riserPos[1],
            0.0, 3.0
        );

        System.out.printf("\nGenerated %d pipe segments:%n", pipes.size());
        for (FPPipeSpec pipe : pipes) {
            System.out.printf("  %s: %s (%.2fm) [%s]%n",
                pipe.id(), pipe.type(), pipe.length(), pipe.assemblyId());
        }

        // Group into assemblies
        Map<String, FPAssembly> assemblies = placer.groupIntoAssemblies(pipes);
        System.out.printf("\nGrouped into %d assemblies:%n", assemblies.size());
        for (FPAssembly assembly : assemblies.values()) {
            System.out.printf("  %s: %s - %d pipes, %.2fm total, %d sprinklers%n",
                assembly.assemblyId(), assembly.type(),
                assembly.pipes().size(), assembly.totalLengthM(), assembly.sprinklerCount());
        }

        // Generate BOM
        List<BOMEntry> bom = placer.generateBOM(assemblies);
        System.out.println("\nBill of Materials:");
        for (BOMEntry entry : bom) {
            System.out.printf("  %s: %.0f %s - %s%n",
                entry.productCode(), entry.quantity(), entry.unit(), entry.description());
        }

        System.out.println("\n=== Test Complete ===");
    }
}
