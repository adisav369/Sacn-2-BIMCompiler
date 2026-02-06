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

    // NFPA 13 pipe diameters (meters) - Light Hazard Occupancy
    private static final double FP_RISER_DIAMETER = 0.100;    // 100mm (4") main riser
    private static final double FP_MAIN_DIAMETER = 0.065;     // 65mm (2.5") branch main
    private static final double FP_BRANCH_DIAMETER = 0.025;   // 25mm (1") to head

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
        BRANCH      // Short connection to head
    }

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
     * Phase 85: Metadata-driven piping generation.
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

        // Group sprinklers by room for branch organization
        Map<String, List<SprinklerSpec>> byRoom = new LinkedHashMap<>();
        for (SprinklerSpec s : sprinklers) {
            byRoom.computeIfAbsent(s.roomName(), k -> new ArrayList<>()).add(s);
        }

        // Calculate main pipe path - connect room centroids
        List<Point3D> mainPoints = new ArrayList<>();
        mainPoints.add(new Point3D(riserX, riserY, mainZ)); // Start at riser

        for (var entry : byRoom.entrySet()) {
            List<SprinklerSpec> roomSprinklers = entry.getValue();
            double roomCenterX = roomSprinklers.stream().mapToDouble(SprinklerSpec::x).average().orElse(0);
            double roomCenterY = roomSprinklers.stream().mapToDouble(SprinklerSpec::y).average().orElse(0);
            mainPoints.add(new Point3D(roomCenterX, roomCenterY, mainZ));
        }

        // Generate main pipe segments connecting the centroids
        String mainAssembly = assemblyPrefix + "MAIN";
        for (int i = 0; i < mainPoints.size() - 1; i++) {
            Point3D p1 = mainPoints.get(i);
            Point3D p2 = mainPoints.get(i + 1);

            // Route in L-shape: first X, then Y (Manhattan routing)
            if (Math.abs(p2.x() - p1.x()) > 0.01) {
                pipes.add(new FPPipeSpec(
                    assemblyPrefix + "main_x_" + pipeIndex++,
                    FPPipeType.MAIN,
                    p1,
                    new Point3D(p2.x(), p1.y(), mainZ),
                    FP_MAIN_DIAMETER,
                    mainAssembly,
                    i * 2
                ));
                p1 = new Point3D(p2.x(), p1.y(), mainZ);
            }
            if (Math.abs(p2.y() - p1.y()) > 0.01) {
                pipes.add(new FPPipeSpec(
                    assemblyPrefix + "main_y_" + pipeIndex++,
                    FPPipeType.MAIN,
                    p1,
                    new Point3D(p2.x(), p2.y(), mainZ),
                    FP_MAIN_DIAMETER,
                    mainAssembly,
                    i * 2 + 1
                ));
            }
        }

        // Generate branch pipes from main to each sprinkler head
        int branchIndex = 0;
        for (var entry : byRoom.entrySet()) {
            String roomName = entry.getKey();
            List<SprinklerSpec> roomSprinklers = entry.getValue();
            String branchAssembly = assemblyPrefix + roomName.toUpperCase() + "_BRANCH";

            double roomCenterX = roomSprinklers.stream().mapToDouble(SprinklerSpec::x).average().orElse(0);
            double roomCenterY = roomSprinklers.stream().mapToDouble(SprinklerSpec::y).average().orElse(0);
            Point3D roomTee = new Point3D(roomCenterX, roomCenterY, mainZ);

            for (SprinklerSpec sprinkler : roomSprinklers) {
                // Branch runs horizontally at main level, then drops to head
                Point3D headPoint = new Point3D(sprinkler.x(), sprinkler.y(), mainZ);
                Point3D dropPoint = new Point3D(sprinkler.x(), sprinkler.y(), sprinkler.z());

                // Horizontal branch (if not directly under tee)
                if (Math.abs(sprinkler.x() - roomCenterX) > 0.01 ||
                    Math.abs(sprinkler.y() - roomCenterY) > 0.01) {

                    // Route in L-shape from tee to head position
                    if (Math.abs(sprinkler.x() - roomCenterX) > 0.01) {
                        pipes.add(new FPPipeSpec(
                            assemblyPrefix + "branch_x_" + branchIndex,
                            FPPipeType.BRANCH,
                            roomTee,
                            new Point3D(sprinkler.x(), roomCenterY, mainZ),
                            FP_BRANCH_DIAMETER,
                            branchAssembly,
                            branchIndex * 3
                        ));
                    }
                    if (Math.abs(sprinkler.y() - roomCenterY) > 0.01) {
                        pipes.add(new FPPipeSpec(
                            assemblyPrefix + "branch_y_" + branchIndex,
                            FPPipeType.BRANCH,
                            new Point3D(sprinkler.x(), roomCenterY, mainZ),
                            headPoint,
                            FP_BRANCH_DIAMETER,
                            branchAssembly,
                            branchIndex * 3 + 1
                        ));
                    }
                }

                // Vertical drop to sprinkler head
                pipes.add(new FPPipeSpec(
                    assemblyPrefix + "drop_" + branchIndex,
                    FPPipeType.BRANCH,
                    headPoint,
                    dropPoint,
                    FP_BRANCH_DIAMETER,
                    branchAssembly,
                    branchIndex * 3 + 2
                ));
                branchIndex++;
            }
        }

        // Generate riser segment for this storey
        String riserAssembly = assemblyPrefix + "RISER";
        pipes.add(new FPPipeSpec(
            assemblyPrefix + "riser",
            FPPipeType.RISER,
            new Point3D(riserX, riserY, floorZ),
            new Point3D(riserX, riserY, mainZ),
            FP_RISER_DIAMETER,
            riserAssembly,
            0
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
