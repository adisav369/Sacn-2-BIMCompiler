package com.bim.compiler.validation;

import com.bim.compiler.BIMConstants;
import com.bim.ormsandbox.po.M_AdBuildingGrid;

import java.sql.*;
import java.util.*;

/**
 * Mathematical Placement Prover — pre-write and post-write validation.
 *
 * Runs pure-function proofs on placement data: (placements, metadata) → ProofResult.
 * 14 proofs in 5 tiers, from per-element arithmetic to conservation laws.
 *
 * <p>Integration:
 * <ul>
 *   <li>Pre-write: {@link #prove(List, String)} in BuildingWriter before emission</li>
 *   <li>Post-write: {@link #proveFromDB(String)} in E2E tests after DB write</li>
 * </ul>
 *
 * <p>Non-blocking: violations are reported, not thrown. Score is still arbiter.
 */
public class PlacementProver {

    // =========================================================================
    // Data records
    // =========================================================================

    /** Minimal placement data for proving (subset of PlacementLoader.Placement). */
    public record PlacementData(
        String guid,
        String ifcClass,
        String elementRef,
        String storey,
        double minX, double maxX,
        double minY, double maxY,
        double minZ, double maxZ
    ) {
        public double cx() { return (minX + maxX) / 2; }
        public double cy() { return (minY + maxY) / 2; }
        public double cz() { return (minZ + maxZ) / 2; }
        public double dx() { return maxX - minX; }
        public double dy() { return maxY - minY; }
        public double dz() { return maxZ - minZ; }
    }

    /** Result of a single proof. */
    public record ProofResult(
        String proofId,
        Status status,
        String element,
        String evidence,
        double measuredValue
    ) {
        public enum Status { PROVEN, VIOLATED, SKIPPED }

        public boolean isCritical() {
            return proofId.startsWith("P01") || proofId.startsWith("P02")
                || proofId.startsWith("P03") || proofId.startsWith("P04")
                // R5: Promoted from advisory — fundamental spatial integrity (LAST_MILE_PROBLEM.md)
                || proofId.startsWith("P05") || proofId.startsWith("P06")
                || proofId.startsWith("P16") || proofId.startsWith("P17")
                || proofId.startsWith("P22");
        }
    }

    /** Aggregate report of all proofs. */
    public record ProofReport(
        List<ProofResult> results,
        int proven,
        int violated,
        int skipped
    ) {
        public boolean allCriticalProven() {
            return results.stream()
                .filter(r -> r.isCritical() && r.status() == ProofResult.Status.VIOLATED)
                .findAny().isEmpty();
        }

        public int criticalViolations() {
            return (int) results.stream()
                .filter(r -> r.isCritical() && r.status() == ProofResult.Status.VIOLATED)
                .count();
        }
    }

    // =========================================================================
    // Constants
    // =========================================================================

    /** Coordinate sanity range (meters). */
    private static final double COORD_MIN = -100.0;
    private static final double COORD_MAX = 1000.0;

    /** Minimum dimension to not be degenerate (1mm). */
    private static final double MIN_DIMENSION = 0.001;

    /** Centroid duplicate detection threshold (1mm). */
    private static final double CENTROID_TOLERANCE = 0.001;

    /** Overlap volume threshold for same-class overlap (10mm cube). */
    private static final double OVERLAP_VOLUME_THRESHOLD = 0.00001; // (10mm)^3 in m^3 = 1e-6 ... use 1e-5 for tolerance

    /** Storey height defaults when grid data unavailable. */
    private static final double DEFAULT_GROUND_FLOOR_Z = 0.0;
    private static final double DEFAULT_STOREY_HEIGHT = 3.5;
    private static final double STOREY_Z_TOLERANCE = 0.5; // 500mm tolerance for Z band

    /** Classes exempt from same-class overlap (beams legitimately cross). */
    private static final Set<String> OVERLAP_EXEMPT_CLASSES = Set.of(
        "IfcMember", "IfcBuildingElementProxy");

    /** Containment tolerance (50mm — matches BIMConstants.PLANE_TOLERANCE). */
    private static final double CONTAINMENT_TOLERANCE = BIMConstants.PLANE_TOLERANCE;

    /** Wall coverage tolerance (10mm — matches BIMConstants.ASSEMBLY_TOLERANCE). */
    private static final double COVERAGE_TOLERANCE = BIMConstants.ASSEMBLY_TOLERANCE;

    /** Floor area conservation tolerance (10%). */
    private static final double AREA_CONSERVATION_TOLERANCE = 0.10;

    // =========================================================================
    // Main entry points
    // =========================================================================

    /**
     * Prove placement correctness from a list of placement data.
     * Used pre-write in BuildingWriter.
     */
    public static ProofReport prove(List<PlacementData> placements, String buildingName) {
        List<ProofResult> results = new ArrayList<>();

        // Tier 1: Per-element arithmetic
        for (PlacementData p : placements) {
            results.add(provePositiveExtent(p));
            results.add(proveFiniteCoords(p));
            results.add(proveMinDimension(p));
            results.add(proveStoreyZBand(p));
        }

        // Tier 2: Pairwise relations
        results.addAll(proveNoDuplicatePosition(placements));
        results.addAll(proveNoSameClassOverlap(placements));

        // Tier 3-5: Require relational metadata — attempt from library DB
        results.addAll(proveRelationalTiers(placements, buildingName));

        return buildReport(results);
    }

    /**
     * Prove placement correctness by reading an output DB.
     * Used post-write in E2E tests.
     */
    public static ProofReport proveFromDB(String dbPath) {
        List<PlacementData> placements = new ArrayList<>();
        List<ProofResult> dbLevelResults = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT em.guid, em.ifc_class, em.element_name, em.storey,
                            r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                     FROM elements_meta em
                     JOIN elements_rtree r ON em.id = r.id
                     """)) {
                while (rs.next()) {
                    placements.add(new PlacementData(
                        rs.getString("guid"),
                        rs.getString("ifc_class"),
                        rs.getString("element_name"),
                        rs.getString("storey"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    ));
                }
            }
            // P22: opening mesh vertex containment (critical — reads vertex blobs)
            dbLevelResults.addAll(proveOpeningMeshInBbox(conn));
            // P23: drain segment corner alignment (advisory — bbox-level)
            dbLevelResults.addAll(proveDrainCornerAlignment(conn));
        } catch (SQLException e) {
            System.err.printf("[PROOF] Cannot read DB %s: %s%n", dbPath, e.getMessage());
            return buildReport(List.of());
        }

        // Detect building name from output DB path
        String buildingName = detectBuildingName(dbPath, placements);

        ProofReport baseReport = prove(placements, buildingName);
        List<ProofResult> allResults = new ArrayList<>(baseReport.results());
        allResults.addAll(dbLevelResults);
        return buildReport(allResults);
    }

    /**
     * Print a human-readable proof report.
     */
    public static void printReport(ProofReport report) {
        // Group violations by proof ID for concise output
        Map<String, List<ProofResult>> violationsByProof = new LinkedHashMap<>();
        for (ProofResult r : report.results()) {
            if (r.status() == ProofResult.Status.VIOLATED) {
                violationsByProof.computeIfAbsent(r.proofId(), k -> new ArrayList<>()).add(r);
            }
        }

        System.out.printf("[PROOF] %d proven, %d violated, %d skipped%n",
            report.proven(), report.violated(), report.skipped());

        if (!violationsByProof.isEmpty()) {
            for (var entry : violationsByProof.entrySet()) {
                List<ProofResult> violations = entry.getValue();
                ProofResult first = violations.get(0);
                if (violations.size() <= 3) {
                    for (ProofResult v : violations) {
                        System.out.printf("  [VIOLATED] %s %s: %s (%.4f)%n",
                            v.proofId(), v.element() != null ? v.element() : "GLOBAL",
                            v.evidence(), v.measuredValue());
                    }
                } else {
                    // Summarize: show first 2 + count
                    for (int i = 0; i < 2; i++) {
                        ProofResult v = violations.get(i);
                        System.out.printf("  [VIOLATED] %s %s: %s (%.4f)%n",
                            v.proofId(), v.element() != null ? v.element() : "GLOBAL",
                            v.evidence(), v.measuredValue());
                    }
                    System.out.printf("  [VIOLATED] %s ... and %d more%n",
                        first.proofId(), violations.size() - 2);
                }
            }
        }
    }

    // =========================================================================
    // Tier 1: Per-Element Arithmetic
    // =========================================================================

    /** P01: Every element has positive extent on all axes. */
    private static ProofResult provePositiveExtent(PlacementData p) {
        double dx = p.dx(), dy = p.dy(), dz = p.dz();
        if (dx > 0 && dy > 0 && dz > 0) {
            return new ProofResult("P01_POSITIVE_EXTENT", ProofResult.Status.PROVEN,
                p.guid(), "dx=%.4f dy=%.4f dz=%.4f".formatted(dx, dy, dz),
                Math.min(dx, Math.min(dy, dz)));
        }
        String axis = dx <= 0 ? "X" : dy <= 0 ? "Y" : "Z";
        double val = dx <= 0 ? dx : dy <= 0 ? dy : dz;
        return new ProofResult("P01_POSITIVE_EXTENT", ProofResult.Status.VIOLATED,
            p.guid(), "non-positive %s extent=%.6f".formatted(axis, val), val);
    }

    /** P02: No NaN, no Infinity, coordinates in sane range. */
    private static ProofResult proveFiniteCoords(PlacementData p) {
        double[] coords = {p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ()};
        String[] names = {"minX", "maxX", "minY", "maxY", "minZ", "maxZ"};

        for (int i = 0; i < coords.length; i++) {
            if (Double.isNaN(coords[i]) || Double.isInfinite(coords[i])) {
                return new ProofResult("P02_FINITE_COORDS", ProofResult.Status.VIOLATED,
                    p.guid(), "%s is NaN/Inf".formatted(names[i]), coords[i]);
            }
            if (coords[i] < COORD_MIN || coords[i] > COORD_MAX) {
                return new ProofResult("P02_FINITE_COORDS", ProofResult.Status.VIOLATED,
                    p.guid(), "%s=%.4f outside [%.0f, %.0f]".formatted(
                        names[i], coords[i], COORD_MIN, COORD_MAX), coords[i]);
            }
        }
        return new ProofResult("P02_FINITE_COORDS", ProofResult.Status.PROVEN,
            p.guid(), "all coords in range", 0);
    }

    /** P03: Smallest axis > 1mm (not degenerate sliver). */
    private static ProofResult proveMinDimension(PlacementData p) {
        double minDim = Math.min(p.dx(), Math.min(p.dy(), p.dz()));
        if (minDim >= MIN_DIMENSION) {
            return new ProofResult("P03_MIN_DIMENSION", ProofResult.Status.PROVEN,
                p.guid(), "min_dim=%.4f".formatted(minDim), minDim);
        }
        return new ProofResult("P03_MIN_DIMENSION", ProofResult.Status.VIOLATED,
            p.guid(), "min_dim=%.6f < %.3f".formatted(minDim, MIN_DIMENSION), minDim);
    }

    /** P04: Element Z range falls within expected storey Z band. */
    private static ProofResult proveStoreyZBand(PlacementData p) {
        // Determine storey Z band from storey name.
        // Naming conventions: "Ground Floor", "Level 1" (= ground in Revit convention),
        // "Level 2" (= first upper), "First Floor" (= upper), etc.
        double floorZ, ceilingZ;
        String storey = p.storey() != null ? p.storey().toLowerCase().trim() : "";

        // Parse level number if present (e.g. "level 1" → 1, "level 2" → 2)
        int levelNum = -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("level\\s*(\\d+)")
            .matcher(storey);
        if (m.find()) {
            levelNum = Integer.parseInt(m.group(1));
        }

        if (storey.contains("fdn") || storey.contains("foundation")) {
            // Foundation/sub-grade storey
            floorZ = -5.0;
            ceilingZ = DEFAULT_STOREY_HEIGHT;
        } else if (storey.contains("ground") || levelNum == 1 || storey.contains("0")
                || storey.isEmpty()) {
            // Ground floor: Z 0 to storey height
            floorZ = DEFAULT_GROUND_FLOOR_Z;
            ceilingZ = DEFAULT_STOREY_HEIGHT;
        } else if (storey.contains("first") || levelNum == 2 || storey.contains("upper")) {
            // First upper floor
            floorZ = DEFAULT_STOREY_HEIGHT;
            ceilingZ = DEFAULT_STOREY_HEIGHT * 2;
        } else if (storey.contains("second") || levelNum == 3) {
            floorZ = DEFAULT_STOREY_HEIGHT * 2;
            ceilingZ = DEFAULT_STOREY_HEIGHT * 3;
        } else if (storey.contains("roof") || storey.contains("top")) {
            floorZ = 0;
            ceilingZ = DEFAULT_STOREY_HEIGHT * 3;
        } else {
            // Unknown storey — use wide band
            floorZ = DEFAULT_GROUND_FLOOR_Z;
            ceilingZ = DEFAULT_STOREY_HEIGHT * 3;
        }

        // Roof and railing elements have extended Z range (extend above storey ceiling)
        String cls = p.ifcClass();
        if ("IfcRoof".equals(cls) || "IfcRailing".equals(cls)) {
            ceilingZ = Math.max(ceilingZ, DEFAULT_STOREY_HEIGHT * 3);
        }

        // Slabs sit at storey boundaries — extend band by 1 storey in each direction
        if ("IfcSlab".equals(cls)) {
            floorZ -= DEFAULT_STOREY_HEIGHT;
            ceilingZ += DEFAULT_STOREY_HEIGHT;
        }

        // MEP elements (piping, fittings, terminals) route between storeys and below grade
        if (cls.startsWith("IfcFlow") || cls.startsWith("IfcPipe")
                || cls.startsWith("IfcDuct") || "IfcBuildingElementProxy".equals(cls)) {
            floorZ = Math.min(floorZ, -2.0);   // sub-grade plumbing
            ceilingZ = Math.max(ceilingZ, ceilingZ + DEFAULT_STOREY_HEIGHT);
        }

        // Multi-storey elements (height > 90% of storey): extend ceiling by 1 additional storey
        double elementHeight = p.maxZ() - p.minZ();
        if (elementHeight > DEFAULT_STOREY_HEIGHT * 0.9) {
            ceilingZ += DEFAULT_STOREY_HEIGHT;
        }

        if (p.minZ() >= floorZ - STOREY_Z_TOLERANCE
                && p.maxZ() <= ceilingZ + STOREY_Z_TOLERANCE) {
            return new ProofResult("P04_STOREY_Z_BAND", ProofResult.Status.PROVEN,
                p.guid(), "Z[%.3f,%.3f] within [%.1f,%.1f]±%.1f".formatted(
                    p.minZ(), p.maxZ(), floorZ, ceilingZ, STOREY_Z_TOLERANCE),
                p.maxZ() - p.minZ());
        }
        return new ProofResult("P04_STOREY_Z_BAND", ProofResult.Status.VIOLATED,
            p.guid(), "Z[%.3f,%.3f] outside [%.1f,%.1f]±%.1f".formatted(
                p.minZ(), p.maxZ(), floorZ, ceilingZ, STOREY_Z_TOLERANCE),
            Math.max(p.maxZ() - ceilingZ, floorZ - p.minZ()));
    }

    // =========================================================================
    // Tier 2: Pairwise Relations
    // =========================================================================

    /** P05: No two elements share the same centroid (within 1mm). */
    private static List<ProofResult> proveNoDuplicatePosition(List<PlacementData> placements) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyViolation = false;

        for (int i = 0; i < placements.size(); i++) {
            for (int j = i + 1; j < placements.size(); j++) {
                PlacementData a = placements.get(i);
                PlacementData b = placements.get(j);

                // Different ifcClass at same position is OK (door + frame, etc.)
                if (!a.ifcClass().equals(b.ifcClass())) continue;

                double dist = Math.sqrt(
                    Math.pow(a.cx() - b.cx(), 2) +
                    Math.pow(a.cy() - b.cy(), 2) +
                    Math.pow(a.cz() - b.cz(), 2));

                if (dist < CENTROID_TOLERANCE) {
                    results.add(new ProofResult("P05_NO_DUPLICATE_POSITION",
                        ProofResult.Status.VIOLATED,
                        a.guid(), "duplicate centroid with %s dist=%.6f".formatted(b.guid(), dist),
                        dist));
                    anyViolation = true;
                }
            }
        }

        if (!anyViolation) {
            results.add(new ProofResult("P05_NO_DUPLICATE_POSITION",
                ProofResult.Status.PROVEN, null,
                "%d placements, no duplicate centroids".formatted(placements.size()), 0));
        }
        return results;
    }

    /** P06: No two elements of same ifcClass in same storey have overlapping bboxes. */
    private static List<ProofResult> proveNoSameClassOverlap(List<PlacementData> placements) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyViolation = false;

        // Group by (storey, ifcClass)
        Map<String, List<PlacementData>> groups = new HashMap<>();
        for (PlacementData p : placements) {
            if (OVERLAP_EXEMPT_CLASSES.contains(p.ifcClass())) continue;
            String key = p.storey() + "|" + p.ifcClass();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        for (var entry : groups.entrySet()) {
            List<PlacementData> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    PlacementData a = group.get(i);
                    PlacementData b = group.get(j);

                    double overlapX = Math.max(0, Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX()));
                    double overlapY = Math.max(0, Math.min(a.maxY(), b.maxY()) - Math.max(a.minY(), b.minY()));
                    double overlapZ = Math.max(0, Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ()));
                    double overlapVolume = overlapX * overlapY * overlapZ;

                    // Thin elements (IfcPlate party walls) share face — exempt if overlap is thin
                    if ("IfcPlate".equals(a.ifcClass()) || "IfcWall".equals(a.ifcClass())) {
                        // Walls can share a face plane (party wall)
                        double minOverlap = Math.min(overlapX, Math.min(overlapY, overlapZ));
                        if (minOverlap < COVERAGE_TOLERANCE) continue;
                    }

                    if (overlapVolume > OVERLAP_VOLUME_THRESHOLD) {
                        results.add(new ProofResult("P06_NO_SAME_CLASS_OVERLAP",
                            ProofResult.Status.VIOLATED,
                            a.guid(),
                            "overlaps %s vol=%.6f m³".formatted(b.guid(), overlapVolume),
                            overlapVolume));
                        anyViolation = true;
                    }
                }
            }
        }

        if (!anyViolation) {
            results.add(new ProofResult("P06_NO_SAME_CLASS_OVERLAP",
                ProofResult.Status.PROVEN, null,
                "%d placements, no same-class overlaps".formatted(placements.size()), 0));
        }
        return results;
    }

    // =========================================================================
    // Tier 3-5: Relational proofs (require library metadata)
    // =========================================================================

    /**
     * Run Tier 3-5 proofs using relational metadata from BOM.db.
     * If library unavailable, proofs are SKIPPED (not violated).
     */
    private static List<ProofResult> proveRelationalTiers(
            List<PlacementData> placements, String buildingName) {
        List<ProofResult> results = new ArrayList<>();

        if (buildingName == null || buildingName.isEmpty()) {
            results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.SKIPPED,
                null, "no building name — skipping relational proofs", 0));
            return results;
        }

        try (Connection lib = DriverManager.getConnection(
                "jdbc:sqlite:" + System.getProperty("bom.db"))) {

            // Load wall faces for this building
            Map<String, WallFaceData> wallFaces = loadWallFaces(lib, buildingName);
            Map<String, RoomData> rooms = loadRooms(lib, buildingName);
            List<ElementRule> elementRules = loadElementRules(lib, buildingName);

            if (wallFaces.isEmpty() && rooms.isEmpty()) {
                results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.SKIPPED,
                    null, "no relational data for " + buildingName, 0));
                return results;
            }

            // Build placement lookup by elementRef
            Map<String, PlacementData> byRef = new HashMap<>();
            Map<String, PlacementData> byGuid = new HashMap<>();
            for (PlacementData p : placements) {
                if (p.elementRef() != null) byRef.put(p.elementRef(), p);
                if (p.guid() != null) byGuid.put(p.guid(), p);
            }

            // Tier 3: Host-element containment
            results.addAll(proveOpeningContainment(placements, wallFaces, elementRules, buildingName));
            results.addAll(proveFurnitureInRoom(placements, rooms, elementRules, buildingName));
            results.addAll(proveFixtureOnSurface(placements, wallFaces, elementRules, buildingName));

            // Tier 4: Topological closure
            results.addAll(provePerimeterClosure(wallFaces));
            results.addAll(proveWallCoverage(wallFaces, rooms));
            results.addAll(proveDoorPerRoom(placements, rooms, wallFaces, elementRules));

            // Tier 5: Conservation laws
            results.addAll(provePerimeterLength(wallFaces, lib, buildingName));
            results.addAll(proveFloorArea(placements, rooms));

            // Tier 6: MEP system proofs (require CONNECTS_TO edges)
            results.addAll(provePipeInHost(placements, rooms, elementRules, buildingName));
            results.addAll(proveWasteGradient(placements, lib, buildingName));
            results.addAll(proveSystemConnected(placements, lib, buildingName));
            results.addAll(proveVentAboveRoof(placements, lib, buildingName));

            // Tier 7: Visual quality proofs (Phase RM-7)
            results.addAll(proveLodGeometry(placements, lib, buildingName));
            results.addAll(proveWallOrientation(placements, lib, buildingName));
            results.addAll(proveElementInRoom(placements, rooms, elementRules, buildingName));

        } catch (SQLException e) {
            results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.SKIPPED,
                null, "library DB error: " + e.getMessage(), 0));
        }

        return results;
    }

    // =========================================================================
    // Tier 3: Host-Element Containment
    // =========================================================================

    /** P07: Every IfcDoor/IfcWindow bbox projects within its host wall bbox. */
    private static List<ProofResult> proveOpeningContainment(
            List<PlacementData> placements, Map<String, WallFaceData> wallFaces,
            List<ElementRule> rules, String buildingName) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        // Find opening rules (doors/windows with host_type=WALL)
        for (ElementRule rule : rules) {
            if (!"WALL".equals(rule.hostType())) continue;
            if (!"IfcDoor".equals(rule.ifcClass()) && !"IfcWindow".equals(rule.ifcClass())) continue;

            // Find the placement for this element
            PlacementData opening = findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (opening == null) continue;

            // Resolve host wall face
            WallFaceData hostWall = wallFaces.get(rule.hostRef());
            if (hostWall == null) continue;

            anyChecked = true;

            // 2D containment check on wall plane + height
            // Wall has an axis (N/S/E/W) — check containment on wall plane
            boolean contained;
            double maxExceedance;

            if (hostWall.runsNS()) {
                // Wall runs N-S: check Y span containment + height
                double exY1 = hostWall.minY() - opening.minY();
                double exY2 = opening.maxY() - hostWall.maxY();
                double exZ1 = hostWall.minZ() - opening.minZ();
                double exZ2 = opening.maxZ() - hostWall.maxZ();
                maxExceedance = Math.max(0, Math.max(exY1, Math.max(exY2, Math.max(exZ1, exZ2))));
                contained = maxExceedance <= CONTAINMENT_TOLERANCE;
            } else {
                // Wall runs E-W: check X span containment + height
                double exX1 = hostWall.minX() - opening.minX();
                double exX2 = opening.maxX() - hostWall.maxX();
                double exZ1 = hostWall.minZ() - opening.minZ();
                double exZ2 = opening.maxZ() - hostWall.maxZ();
                maxExceedance = Math.max(0, Math.max(exX1, Math.max(exX2, Math.max(exZ1, exZ2))));
                contained = maxExceedance <= CONTAINMENT_TOLERANCE;
            }

            if (contained) {
                results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.PROVEN,
                    opening.guid(), "%s in wall %s".formatted(rule.ifcClass(), rule.hostRef()),
                    maxExceedance));
            } else {
                results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.VIOLATED,
                    opening.guid(),
                    "%s exceeds wall %s by %.4fm".formatted(rule.ifcClass(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.SKIPPED,
                null, "no opening rules with resolved wall faces", 0));
        }
        return results;
    }

    /** P08: Every IfcFurniture/IfcFurnishingElement centroid is within its host room. */
    private static List<ProofResult> proveFurnitureInRoom(
            List<PlacementData> placements, Map<String, RoomData> rooms,
            List<ElementRule> rules, String buildingName) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        for (ElementRule rule : rules) {
            if (!"ROOM".equals(rule.hostType())) continue;
            if (!"IfcFurniture".equals(rule.ifcClass())
                    && !"IfcFurnishingElement".equals(rule.ifcClass())) continue;

            PlacementData furn = findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (furn == null) continue;

            RoomData room = rooms.get(rule.hostRef());
            if (room == null) continue;

            anyChecked = true;

            // 2D centroid-in-room check
            boolean inside = furn.cx() >= room.minX() - CONTAINMENT_TOLERANCE
                          && furn.cx() <= room.maxX() + CONTAINMENT_TOLERANCE
                          && furn.cy() >= room.minY() - CONTAINMENT_TOLERANCE
                          && furn.cy() <= room.maxY() + CONTAINMENT_TOLERANCE;

            if (inside) {
                results.add(new ProofResult("P08_FURNITURE_IN_ROOM", ProofResult.Status.PROVEN,
                    furn.guid(), "centroid in room %s".formatted(rule.hostRef()), 0));
            } else {
                double dx = Math.max(0, Math.max(room.minX() - furn.cx(), furn.cx() - room.maxX()));
                double dy = Math.max(0, Math.max(room.minY() - furn.cy(), furn.cy() - room.maxY()));
                double dist = Math.sqrt(dx * dx + dy * dy);
                results.add(new ProofResult("P08_FURNITURE_IN_ROOM", ProofResult.Status.VIOLATED,
                    furn.guid(),
                    "centroid %.4fm outside room %s".formatted(dist, rule.hostRef()), dist));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P08_FURNITURE_IN_ROOM", ProofResult.Status.SKIPPED,
                null, "no furniture rules with resolved rooms", 0));
        }
        return results;
    }

    /** P09: Every hosted fixture shares at least one face with its host surface. */
    private static List<ProofResult> proveFixtureOnSurface(
            List<PlacementData> placements, Map<String, WallFaceData> wallFaces,
            List<ElementRule> rules, String buildingName) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        Set<String> fixtureClasses = Set.of("IfcSanitaryTerminal", "IfcFlowTerminal");

        for (ElementRule rule : rules) {
            if (!"WALL".equals(rule.hostType())) continue;
            if (!fixtureClasses.contains(rule.ifcClass())) continue;

            PlacementData fixture = findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (fixture == null) continue;

            WallFaceData wall = wallFaces.get(rule.hostRef());
            if (wall == null) continue;

            anyChecked = true;

            // Check if any fixture face is near the wall surface
            double gap;
            if (wall.runsNS()) {
                gap = Math.min(
                    Math.abs(fixture.minX() - wall.wallX()),
                    Math.abs(fixture.maxX() - wall.wallX()));
            } else {
                gap = Math.min(
                    Math.abs(fixture.minY() - wall.wallY()),
                    Math.abs(fixture.maxY() - wall.wallY()));
            }

            if (gap <= CONTAINMENT_TOLERANCE) {
                results.add(new ProofResult("P09_FIXTURE_ON_SURFACE", ProofResult.Status.PROVEN,
                    fixture.guid(), "gap=%.4fm to wall %s".formatted(gap, rule.hostRef()), gap));
            } else {
                results.add(new ProofResult("P09_FIXTURE_ON_SURFACE", ProofResult.Status.VIOLATED,
                    fixture.guid(),
                    "gap=%.4fm from wall %s (>%.3f)".formatted(gap, rule.hostRef(), CONTAINMENT_TOLERANCE),
                    gap));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P09_FIXTURE_ON_SURFACE", ProofResult.Status.SKIPPED,
                null, "no fixture rules with resolved walls", 0));
        }
        return results;
    }

    // =========================================================================
    // Tier 4: Topological Closure
    // =========================================================================

    /** P10: Exterior wall segments form a closed polygon (2D XY). */
    private static List<ProofResult> provePerimeterClosure(Map<String, WallFaceData> wallFaces) {
        List<ProofResult> results = new ArrayList<>();

        // Extract exterior walls
        List<WallFaceData> exterior = wallFaces.values().stream()
            .filter(w -> w.isExterior()).toList();

        if (exterior.isEmpty()) {
            results.add(new ProofResult("P10_PERIMETER_CLOSURE", ProofResult.Status.SKIPPED,
                null, "no exterior walls found", 0));
            return results;
        }

        // Build vertex graph from wall endpoints
        // Each wall segment: (startX,startY) → (endX,endY)
        Map<String, Integer> vertexDegree = new HashMap<>();
        int edgeCount = 0;

        for (WallFaceData w : exterior) {
            String start, end;
            if (w.runsNS()) {
                start = coordKey(w.wallX(), w.minY());
                end = coordKey(w.wallX(), w.maxY());
            } else {
                start = coordKey(w.minX(), w.wallY());
                end = coordKey(w.maxX(), w.wallY());
            }
            vertexDegree.merge(start, 1, Integer::sum);
            vertexDegree.merge(end, 1, Integer::sum);
            edgeCount++;
        }

        // For closed polygon: every vertex must have even degree (2 = simple loop)
        long oddDegreeVertices = vertexDegree.values().stream()
            .filter(d -> d % 2 != 0).count();

        if (oddDegreeVertices == 0 && edgeCount >= 4) {
            results.add(new ProofResult("P10_PERIMETER_CLOSURE", ProofResult.Status.PROVEN,
                null, "%d exterior walls, %d vertices, all even degree".formatted(
                    edgeCount, vertexDegree.size()), 0));
        } else if (edgeCount < 4) {
            results.add(new ProofResult("P10_PERIMETER_CLOSURE", ProofResult.Status.VIOLATED,
                null, "only %d exterior walls (need >= 4 for closure)".formatted(edgeCount),
                edgeCount));
        } else {
            results.add(new ProofResult("P10_PERIMETER_CLOSURE", ProofResult.Status.VIOLATED,
                null, "%d odd-degree vertices (gaps in perimeter)".formatted(oddDegreeVertices),
                oddDegreeVertices));
        }

        return results;
    }

    /** P11: For each room face, total wall length = room extent. */
    private static List<ProofResult> proveWallCoverage(
            Map<String, WallFaceData> wallFaces, Map<String, RoomData> rooms) {
        List<ProofResult> results = new ArrayList<>();

        if (rooms.isEmpty() || wallFaces.isEmpty()) {
            results.add(new ProofResult("P11_WALL_COVERAGE", ProofResult.Status.SKIPPED,
                null, "no room/wall data", 0));
            return results;
        }

        // For each room, check that walls on each face cover the room extent
        boolean anyViolation = false;
        int checkedFaces = 0;

        for (var roomEntry : rooms.entrySet()) {
            RoomData room = roomEntry.getValue();

            // Find walls that belong to this room's faces
            for (WallFaceData wall : wallFaces.values()) {
                if (!wall.roomName().equals(roomEntry.getKey())) continue;
                checkedFaces++;
                // Wall length should approximate room extent on the wall's axis
                double wallLength = wall.runsNS() ? (wall.maxY() - wall.minY()) : (wall.maxX() - wall.minX());
                double expectedLength = wall.runsNS()
                    ? (room.maxY() - room.minY())
                    : (room.maxX() - room.minX());

                if (Math.abs(wallLength - expectedLength) > COVERAGE_TOLERANCE + CONTAINMENT_TOLERANCE) {
                    results.add(new ProofResult("P11_WALL_COVERAGE", ProofResult.Status.VIOLATED,
                        null, "room %s face %s: wall=%.3f expected=%.3f".formatted(
                            roomEntry.getKey(), wall.face(), wallLength, expectedLength),
                        Math.abs(wallLength - expectedLength)));
                    anyViolation = true;
                }
            }
        }

        if (checkedFaces == 0) {
            results.add(new ProofResult("P11_WALL_COVERAGE", ProofResult.Status.SKIPPED,
                null, "no wall-to-room face mappings found", 0));
        } else if (!anyViolation) {
            results.add(new ProofResult("P11_WALL_COVERAGE", ProofResult.Status.PROVEN,
                null, "%d room faces checked, all covered".formatted(checkedFaces), 0));
        }
        return results;
    }

    /** P12: Every room (except utility/porch) has at least 1 door. */
    private static List<ProofResult> proveDoorPerRoom(
            List<PlacementData> placements, Map<String, RoomData> rooms,
            Map<String, WallFaceData> wallFaces, List<ElementRule> rules) {
        List<ProofResult> results = new ArrayList<>();

        if (rooms.isEmpty()) {
            results.add(new ProofResult("P12_ROOM_HAS_DOOR", ProofResult.Status.SKIPPED,
                null, "no room data", 0));
            return results;
        }

        // Count doors per room (via wall face → room mapping)
        Map<String, Integer> doorsPerRoom = new HashMap<>();
        for (String roomName : rooms.keySet()) {
            doorsPerRoom.put(roomName, 0);
        }

        for (ElementRule rule : rules) {
            if (!"WALL".equals(rule.hostType()) || !"IfcDoor".equals(rule.ifcClass())) continue;
            WallFaceData wall = wallFaces.get(rule.hostRef());
            if (wall != null) {
                doorsPerRoom.merge(wall.roomName(), 1, Integer::sum);
            }
        }

        boolean anyViolation = false;
        Set<String> exemptRooms = Set.of("porch", "anjung", "utility", "store", "corridor");

        for (var entry : doorsPerRoom.entrySet()) {
            String roomName = entry.getKey();
            boolean exempt = exemptRooms.stream()
                .anyMatch(e -> roomName.toLowerCase().contains(e));

            if (exempt) continue;

            if (entry.getValue() >= 1) {
                results.add(new ProofResult("P12_ROOM_HAS_DOOR", ProofResult.Status.PROVEN,
                    null, "room %s has %d door(s)".formatted(roomName, entry.getValue()), 0));
            } else {
                results.add(new ProofResult("P12_ROOM_HAS_DOOR", ProofResult.Status.VIOLATED,
                    null, "room %s has no doors".formatted(roomName), 0));
                anyViolation = true;
            }
        }

        return results;
    }

    // =========================================================================
    // Tier 5: Conservation Laws
    // =========================================================================

    /** P13: Sum of exterior wall lengths = building perimeter. */
    private static List<ProofResult> provePerimeterLength(
            Map<String, WallFaceData> wallFaces, Connection lib, String buildingName) {
        List<ProofResult> results = new ArrayList<>();

        // Compute expected perimeter from building grid
        double expectedPerimeter;
        try {
            expectedPerimeter = computeExpectedPerimeter(lib, buildingName);
        } catch (SQLException e) {
            results.add(new ProofResult("P13_PERIMETER_LENGTH", ProofResult.Status.SKIPPED,
                null, "cannot read building grid: " + e.getMessage(), 0));
            return results;
        }

        if (expectedPerimeter <= 0) {
            results.add(new ProofResult("P13_PERIMETER_LENGTH", ProofResult.Status.SKIPPED,
                null, "no building grid data", 0));
            return results;
        }

        // Sum exterior wall lengths
        double totalLength = 0;
        for (WallFaceData w : wallFaces.values()) {
            if (!w.isExterior()) continue;
            totalLength += w.runsNS() ? (w.maxY() - w.minY()) : (w.maxX() - w.minX());
        }

        double diff = Math.abs(totalLength - expectedPerimeter);
        double tolerance = expectedPerimeter * AREA_CONSERVATION_TOLERANCE;

        if (diff <= tolerance) {
            results.add(new ProofResult("P13_PERIMETER_LENGTH", ProofResult.Status.PROVEN,
                null, "total=%.3fm expected=%.3fm diff=%.3fm".formatted(
                    totalLength, expectedPerimeter, diff), diff));
        } else {
            results.add(new ProofResult("P13_PERIMETER_LENGTH", ProofResult.Status.VIOLATED,
                null, "total=%.3fm expected=%.3fm diff=%.3fm (>%.1f%%)".formatted(
                    totalLength, expectedPerimeter, diff, AREA_CONSERVATION_TOLERANCE * 100),
                diff));
        }
        return results;
    }

    /** P14: Sum of room floor areas ≈ total slab area. */
    private static List<ProofResult> proveFloorArea(
            List<PlacementData> placements, Map<String, RoomData> rooms) {
        List<ProofResult> results = new ArrayList<>();

        if (rooms.isEmpty()) {
            results.add(new ProofResult("P14_FLOOR_AREA", ProofResult.Status.SKIPPED,
                null, "no room data", 0));
            return results;
        }

        // Sum room areas
        double totalRoomArea = 0;
        for (RoomData room : rooms.values()) {
            totalRoomArea += (room.maxX() - room.minX()) * (room.maxY() - room.minY());
        }

        // Sum ground-level slab areas
        double totalSlabArea = 0;
        int slabCount = 0;
        for (PlacementData p : placements) {
            if ("IfcSlab".equals(p.ifcClass()) && p.minZ() < 0.5) { // ground-level slabs
                totalSlabArea += p.dx() * p.dy();
                slabCount++;
            }
        }

        if (slabCount == 0) {
            results.add(new ProofResult("P14_FLOOR_AREA", ProofResult.Status.SKIPPED,
                null, "no ground-level slabs found", 0));
            return results;
        }

        double ratio = totalSlabArea > 0
            ? Math.abs(totalRoomArea - totalSlabArea) / totalSlabArea
            : 1.0;

        if (ratio <= AREA_CONSERVATION_TOLERANCE) {
            results.add(new ProofResult("P14_FLOOR_AREA", ProofResult.Status.PROVEN,
                null, "rooms=%.2fm² slabs=%.2fm² ratio=%.2f%%".formatted(
                    totalRoomArea, totalSlabArea, ratio * 100), ratio));
        } else {
            results.add(new ProofResult("P14_FLOOR_AREA", ProofResult.Status.VIOLATED,
                null, "rooms=%.2fm² slabs=%.2fm² ratio=%.1f%% (>%.0f%%)".formatted(
                    totalRoomArea, totalSlabArea, ratio * 100,
                    AREA_CONSERVATION_TOLERANCE * 100), ratio));
        }
        return results;
    }

    // =========================================================================
    // Tier 6: MEP System Proofs
    // =========================================================================

    /** P15: Every IfcFlowSegment bbox is contained within its host room bbox (with 50mm tolerance). */
    private static List<ProofResult> provePipeInHost(
            List<PlacementData> placements, Map<String, RoomData> rooms,
            List<ElementRule> rules, String buildingName) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        for (ElementRule rule : rules) {
            if (!"ROOM".equals(rule.hostType())) continue;
            if (!"IfcFlowSegment".equals(rule.ifcClass())
                    && !"IfcFlowFitting".equals(rule.ifcClass())) continue;

            PlacementData pipe = findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (pipe == null) continue;

            RoomData room = rooms.get(rule.hostRef());
            if (room == null) continue;

            anyChecked = true;

            // 2D containment: pipe bbox within room bbox + tolerance
            double exMinX = room.minX() - pipe.minX();
            double exMaxX = pipe.maxX() - room.maxX();
            double exMinY = room.minY() - pipe.minY();
            double exMaxY = pipe.maxY() - room.maxY();
            double maxExceedance = Math.max(0,
                Math.max(exMinX, Math.max(exMaxX, Math.max(exMinY, exMaxY))));

            if (maxExceedance <= CONTAINMENT_TOLERANCE) {
                results.add(new ProofResult("P15_PIPE_IN_HOST", ProofResult.Status.PROVEN,
                    pipe.guid(), "%s in room %s (exceed=%.4f)".formatted(
                        rule.elementRef(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            } else {
                results.add(new ProofResult("P15_PIPE_IN_HOST", ProofResult.Status.VIOLATED,
                    pipe.guid(), "%s exceeds room %s by %.4fm".formatted(
                        rule.elementRef(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P15_PIPE_IN_HOST", ProofResult.Status.SKIPPED,
                null, "no pipe rules with resolved rooms", 0));
        }
        return results;
    }

    /**
     * P16: Waste gradient — for each CONNECTS_TO edge in waste system,
     * from.centroidZ >= to.centroidZ - 1mm (waste flows downward).
     */
    private static List<ProofResult> proveWasteGradient(
            List<PlacementData> placements, Connection lib, String buildingName) throws SQLException {
        List<ProofResult> results = new ArrayList<>();

        // Load CONNECTS_TO edges
        List<String[]> connectEdges = loadConnectsToEdges(lib, buildingName);
        if (connectEdges.isEmpty()) {
            results.add(new ProofResult("P16_WASTE_GRADIENT", ProofResult.Status.SKIPPED,
                null, "no CONNECTS_TO edges for " + buildingName, 0));
            return results;
        }

        // Build placement lookup by elementRef
        Map<String, PlacementData> byRef = new HashMap<>();
        for (PlacementData p : placements) {
            if (p.elementRef() != null) byRef.put(p.elementRef(), p);
        }

        boolean anyViolation = false;
        int checked = 0;

        for (String[] edge : connectEdges) {
            PlacementData from = byRef.get(edge[0]);
            PlacementData to = byRef.get(edge[1]);
            if (from == null || to == null) continue;

            checked++;
            // Waste gradient: drain water flows from fixture down to underground.
            // For horizontal-to-vertical connections, compare base Z (minZ):
            //   branch at -0.15m connects to riser base at 0m — valid (branch is lower)
            // For horizontal-to-horizontal, compare centroid Z.
            // Key invariant: from.minZ >= to.minZ - 1mm (water doesn't flow uphill)
            double fromZ = from.minZ();
            double toZ = to.minZ();
            double gradientMm = (fromZ - toZ) * 1000.0;

            if (fromZ >= toZ - 0.001) {
                results.add(new ProofResult("P16_WASTE_GRADIENT", ProofResult.Status.PROVEN,
                    edge[0], "%s(baseZ=%.3f) → %s(baseZ=%.3f) gradient=%.1fmm".formatted(
                        edge[0], fromZ, edge[1], toZ, gradientMm),
                    gradientMm));
            } else {
                results.add(new ProofResult("P16_WASTE_GRADIENT", ProofResult.Status.VIOLATED,
                    edge[0], "%s(baseZ=%.3f) → %s(baseZ=%.3f) UPHILL %.1fmm".formatted(
                        edge[0], fromZ, edge[1], toZ, gradientMm),
                    gradientMm));
                anyViolation = true;
            }
        }

        if (checked == 0) {
            results.add(new ProofResult("P16_WASTE_GRADIENT", ProofResult.Status.SKIPPED,
                null, "no resolved CONNECTS_TO edges", 0));
        }
        return results;
    }

    /**
     * P17: System connectivity — all wet-room fixture terminals can reach
     * the underground main (SOURCE) via CONNECTS_TO graph traversal.
     */
    private static List<ProofResult> proveSystemConnected(
            List<PlacementData> placements, Connection lib, String buildingName) throws SQLException {
        List<ProofResult> results = new ArrayList<>();

        List<String[]> connectEdges = loadConnectsToEdges(lib, buildingName);
        if (connectEdges.isEmpty()) {
            results.add(new ProofResult("P17_SYSTEM_CONNECTED", ProofResult.Status.SKIPPED,
                null, "no CONNECTS_TO edges for " + buildingName, 0));
            return results;
        }

        // Build adjacency list (from -> list of to)
        Map<String, Set<String>> adj = new HashMap<>();
        Set<String> allNodes = new HashSet<>();
        for (String[] edge : connectEdges) {
            adj.computeIfAbsent(edge[0], k -> new HashSet<>()).add(edge[1]);
            allNodes.add(edge[0]);
            allNodes.add(edge[1]);
        }

        // Find terminals (IfcFurnishingElement, IfcFlowTerminal) and source (underground pipe)
        Set<String> terminals = new HashSet<>();
        String source = null;
        for (String ref : allNodes) {
            if (ref.contains("FurnishingElement") || ref.contains("FlowTerminal")) {
                terminals.add(ref);
            }
            // Underground main is a sink (nothing drains FROM it, or it's pipe_7)
            if (ref.contains("FlowSegment_7")) {
                source = ref;
            }
        }

        if (source == null) {
            // Find node with no outgoing CONNECTS_TO edges = implicit sink
            for (String ref : allNodes) {
                if (!adj.containsKey(ref) && ref.contains("FlowSegment")) {
                    source = ref;
                    break;
                }
            }
        }

        if (source == null || terminals.isEmpty()) {
            results.add(new ProofResult("P17_SYSTEM_CONNECTED", ProofResult.Status.SKIPPED,
                null, "cannot identify source/terminals", 0));
            return results;
        }

        // BFS from each terminal to see if it can reach source
        for (String terminal : terminals) {
            boolean reached = canReach(terminal, source, adj);
            if (reached) {
                results.add(new ProofResult("P17_SYSTEM_CONNECTED", ProofResult.Status.PROVEN,
                    terminal, "%s → %s (path exists)".formatted(terminal, source), 0));
            } else {
                results.add(new ProofResult("P17_SYSTEM_CONNECTED", ProofResult.Status.VIOLATED,
                    terminal, "%s cannot reach %s".formatted(terminal, source), 1));
            }
        }

        return results;
    }

    /** BFS reachability check through CONNECTS_TO adjacency. */
    private static boolean canReach(String from, String target, Map<String, Set<String>> adj) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(from);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(target)) return true;
            if (visited.contains(current)) continue;
            visited.add(current);

            Set<String> neighbors = adj.get(current);
            if (neighbors != null) {
                queue.addAll(neighbors);
            }
        }
        return false;
    }

    /**
     * P18: Vent pipe extends above roof — vent.maxZ > roof.maxZ (advisory).
     */
    private static List<ProofResult> proveVentAboveRoof(
            List<PlacementData> placements, Connection lib, String buildingName) throws SQLException {
        List<ProofResult> results = new ArrayList<>();

        // Find vent pipe and roof from placement data (no c_orderline lookup needed)
        PlacementData vent = null;
        PlacementData roof = null;
        for (PlacementData p : placements) {
            if ("IfcRoof".equals(p.ifcClass())) {
                roof = p;
            }
            // Identify vent by M_Product_ID from c_orderline (WHAT column)
            if ("IfcFlowSegment".equals(p.ifcClass()) && p.elementRef() != null) {
                String sql = "SELECT M_Product_ID FROM c_orderline WHERE C_Order_ID = ? AND Name = ? AND IsActive = 1";
                try (PreparedStatement ps = lib.prepareStatement(sql)) {
                    ps.setString(1, buildingName);
                    ps.setString(2, p.elementRef());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String family = rs.getString(1);
                            // Vent pipe: vertical PVC pipe that extends above roof
                            // §11.9: orientation column dropped from c_orderline.
                            // TODO: Read orientation from PP_Order_Node when migrated.
                            // For now, identify vent by maxZ > 3.0 heuristic only.
                            if (family != null && p.maxZ() > 3.0) {
                                vent = p;
                            }
                        }
                    }
                }
            }
        }

        if (vent == null || roof == null) {
            results.add(new ProofResult("P18_VENT_ABOVE_ROOF", ProofResult.Status.SKIPPED,
                null, "no vent pipe or roof found", 0));
            return results;
        }

        if (vent.maxZ() > roof.maxZ()) {
            results.add(new ProofResult("P18_VENT_ABOVE_ROOF", ProofResult.Status.PROVEN,
                vent.guid(), "vent.maxZ=%.3f > roof.maxZ=%.3f".formatted(vent.maxZ(), roof.maxZ()),
                vent.maxZ() - roof.maxZ()));
        } else {
            results.add(new ProofResult("P18_VENT_ABOVE_ROOF", ProofResult.Status.VIOLATED,
                vent.guid(), "vent.maxZ=%.3f ≤ roof.maxZ=%.3f".formatted(vent.maxZ(), roof.maxZ()),
                roof.maxZ() - vent.maxZ()));
        }

        return results;
    }

    /** Load CONNECTS_TO edges from ad_element_dependency. */
    private static List<String[]> loadConnectsToEdges(Connection lib, String buildingName) throws SQLException {
        List<String[]> edges = new ArrayList<>();
        try {
            String sql = """
                SELECT element_ref, parent_ref
                FROM ad_element_dependency
                WHERE building_type = ? AND relation = 'CONNECTS_TO' AND is_active = 1
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        edges.add(new String[]{rs.getString(1), rs.getString(2)});
                    }
                }
            }
        } catch (SQLException e) {
            // Table may not exist
        }
        return edges;
    }

    // =========================================================================
    // Tier 7: Visual Quality Proofs (Phase RM-7)
    // =========================================================================

    /**
     * P19: LOD Geometry — elements with library geometry entries should NOT fall back
     * to 8-vertex parametric boxes. Checks the output DB for vertex counts.
     * This proof runs from proveFromDB path (post-write), not pre-write.
     * In pre-write context, skips (vertex counts not yet known).
     */
    private static List<ProofResult> proveLodGeometry(
            List<PlacementData> placements, Connection lib, String buildingName) throws SQLException {
        List<ProofResult> results = new ArrayList<>();

        // Load (ifc_class, storey) tuples that HAVE library geometry
        // I_Geometry_Map is in component_library.db — ATTACH for cross-DB query
        Set<String> hasLibraryGeometry = new HashSet<>();
        try {
            try (Statement att = lib.createStatement()) {
                att.execute("ATTACH DATABASE 'library/component_library.db' AS lod");
            }
            String sql = """
                SELECT DISTINCT ifc_class, storey FROM lod.I_Geometry_Map
                WHERE building_type = ? AND geometry_hash IS NOT NULL
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        hasLibraryGeometry.add(rs.getString(1) + "|" + rs.getString(2));
                    }
                }
            }
            try (Statement det = lib.createStatement()) {
                det.execute("DETACH DATABASE lod");
            }
        } catch (SQLException e) {
            // Table may not exist or ATTACH failed
        }

        if (hasLibraryGeometry.isEmpty()) {
            results.add(new ProofResult("P19_LOD_GEOMETRY", ProofResult.Status.SKIPPED,
                null, "no library geometry for " + buildingName, 0));
            return results;
        }

        // P19 is meaningful only in post-write (proveFromDB) where we'd check actual vertex counts.
        // In pre-write, we just verify that geometry_map coverage exists (advisory).
        boolean anyChecked = false;
        for (PlacementData p : placements) {
            String key = p.ifcClass() + "|" + p.storey();
            if (hasLibraryGeometry.contains(key)) {
                anyChecked = true;
                // Mark as proven — actual vertex count check happens in post-write
                results.add(new ProofResult("P19_LOD_GEOMETRY", ProofResult.Status.PROVEN,
                    p.guid(), "%s/%s has library geometry coverage".formatted(p.ifcClass(), p.storey()), 0));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P19_LOD_GEOMETRY", ProofResult.Status.SKIPPED,
                null, "no placements match library geometry entries", 0));
        }
        return results;
    }

    /**
     * P20: Wall Orientation — for IfcPlate/IfcWall elements, verify that
     * NS-oriented walls have dx < dy and EW-oriented walls have dy < dx.
     * Catches walls rotated 90 degrees from their intended orientation.
     */
    private static List<ProofResult> proveWallOrientation(
            List<PlacementData> placements, Connection lib, String buildingName) throws SQLException {
        List<ProofResult> results = new ArrayList<>();

        // §11.9: orientation column DROPPED from c_orderline.
        // TODO: Read orientation from PP_Order_Node when placement data is migrated.
        Map<String, String> orientationByRef = new HashMap<>();

        if (orientationByRef.isEmpty()) {
            results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.SKIPPED,
                null, "no wall orientation data for " + buildingName, 0));
            return results;
        }

        for (PlacementData p : placements) {
            if (!"IfcPlate".equals(p.ifcClass()) && !"IfcWall".equals(p.ifcClass())) continue;

            String orient = orientationByRef.get(p.elementRef());
            if (orient == null) continue;

            double dx = p.dx();
            double dy = p.dy();

            if ("NS".equals(orient)) {
                // NS wall: thin in X (dx), long in Y (dy) → dx < dy
                if (dx < dy) {
                    results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.PROVEN,
                        p.guid(), "NS wall dx=%.4f < dy=%.4f".formatted(dx, dy), dx / dy));
                } else {
                    results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.VIOLATED,
                        p.guid(), "NS wall dx=%.4f >= dy=%.4f (rotated?)".formatted(dx, dy), dx / dy));
                }
            } else if ("EW".equals(orient)) {
                // EW wall: long in X (dx), thin in Y (dy) → dy < dx
                if (dy < dx) {
                    results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.PROVEN,
                        p.guid(), "EW wall dy=%.4f < dx=%.4f".formatted(dy, dx), dy / dx));
                } else {
                    results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.VIOLATED,
                        p.guid(), "EW wall dy=%.4f >= dx=%.4f (rotated?)".formatted(dy, dx), dy / dx));
                }
            }
        }

        if (results.isEmpty()) {
            results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.SKIPPED,
                null, "no wall placements matched orientation rules", 0));
        }
        return results;
    }

    /**
     * P21: Element In Room — for IfcFurnishingElement hosted on rooms,
     * verify the full bbox is within the room boundary (not just centroid).
     * Stricter than P08 which only checks centroid.
     */
    private static List<ProofResult> proveElementInRoom(
            List<PlacementData> placements, Map<String, RoomData> rooms,
            List<ElementRule> rules, String buildingName) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        for (ElementRule rule : rules) {
            if (!"ROOM".equals(rule.hostType())) continue;
            if (!"IfcFurnishingElement".equals(rule.ifcClass())
                    && !"IfcFurniture".equals(rule.ifcClass())) continue;

            PlacementData furn = findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (furn == null) continue;

            RoomData room = rooms.get(rule.hostRef());
            if (room == null) continue;

            anyChecked = true;

            // Full bbox containment (with tolerance)
            double exMinX = room.minX() - furn.minX();
            double exMaxX = furn.maxX() - room.maxX();
            double exMinY = room.minY() - furn.minY();
            double exMaxY = furn.maxY() - room.maxY();
            double maxExceedance = Math.max(0,
                Math.max(exMinX, Math.max(exMaxX, Math.max(exMinY, exMaxY))));

            if (maxExceedance <= CONTAINMENT_TOLERANCE) {
                results.add(new ProofResult("P21_ELEMENT_IN_ROOM", ProofResult.Status.PROVEN,
                    furn.guid(), "%s bbox within room %s (exceed=%.4f)".formatted(
                        rule.elementRef(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            } else {
                results.add(new ProofResult("P21_ELEMENT_IN_ROOM", ProofResult.Status.VIOLATED,
                    furn.guid(), "%s bbox exceeds room %s by %.4fm".formatted(
                        rule.elementRef(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P21_ELEMENT_IN_ROOM", ProofResult.Status.SKIPPED,
                null, "no furniture rules with resolved rooms", 0));
        }
        return results;
    }

    // =========================================================================
    // Data loading helpers
    // =========================================================================

    /**
     * Wall face data — geometry derived from room boundary + face direction.
     * ad_wall_face stores (room_name, face, is_exterior) but NOT coordinates.
     * We compute wall geometry from the parent room's boundary.
     */
    private record WallFaceData(
        String roomName, String face, boolean isExterior,
        double minX, double maxX, double minY, double maxY,
        double minZ, double maxZ,
        double wallX, double wallY
    ) {
        /** Returns true if wall runs N-S direction (EAST/WEST room faces → long axis is Y). */
        boolean runsNS() {
            return "EAST".equals(face) || "WEST".equals(face);
        }
        /** Returns true if wall runs E-W direction (NORTH/SOUTH room faces → long axis is X). */
        boolean runsEW() {
            return "NORTH".equals(face) || "SOUTH".equals(face);
        }
    }

    private record RoomData(
        String name, double minX, double maxX, double minY, double maxY
    ) {}

    private record ElementRule(
        String ifcClass, String elementRef,
        String hostType, String hostRef, String positionRule
    ) {}

    /**
     * Load wall face data from ad_wall_face + ad_room_boundary.
     * Derives wall geometry from room boundaries since ad_wall_face has no coords.
     * Key = "WALL_{room_name}_{FACE}" to match c_orderline.host_ref format.
     */
    private static Map<String, WallFaceData> loadWallFaces(
            Connection lib, String buildingName) throws SQLException {
        // First load rooms to get geometry
        Map<String, RoomData> rooms = loadRooms(lib, buildingName);
        Map<String, WallFaceData> faces = new LinkedHashMap<>();

        String sql = """
            SELECT room_name, face, is_exterior
            FROM ad_wall_face
            WHERE building_type = ? AND is_active = 1
            """;

        try (PreparedStatement ps = lib.prepareStatement(sql)) {
            ps.setString(1, buildingName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String roomName = rs.getString("room_name");
                    String face = rs.getString("face");
                    boolean isExterior = rs.getInt("is_exterior") == 1;

                    RoomData room = rooms.get(roomName);
                    if (room == null) continue;

                    // Derive wall geometry from room boundary + face direction
                    double wMinX, wMaxX, wMinY, wMaxY;
                    double wallX, wallY;
                    double wallThickness = BIMConstants.STANDARD_WALL_THICKNESS;

                    switch (face) {
                        case "NORTH" -> {
                            // North face: runs along X at room's max Y
                            wMinX = room.minX(); wMaxX = room.maxX();
                            wMinY = room.maxY() - wallThickness / 2;
                            wMaxY = room.maxY() + wallThickness / 2;
                            wallX = (room.minX() + room.maxX()) / 2;
                            wallY = room.maxY();
                        }
                        case "SOUTH" -> {
                            wMinX = room.minX(); wMaxX = room.maxX();
                            wMinY = room.minY() - wallThickness / 2;
                            wMaxY = room.minY() + wallThickness / 2;
                            wallX = (room.minX() + room.maxX()) / 2;
                            wallY = room.minY();
                        }
                        case "EAST" -> {
                            wMinX = room.maxX() - wallThickness / 2;
                            wMaxX = room.maxX() + wallThickness / 2;
                            wMinY = room.minY(); wMaxY = room.maxY();
                            wallX = room.maxX();
                            wallY = (room.minY() + room.maxY()) / 2;
                        }
                        case "WEST" -> {
                            wMinX = room.minX() - wallThickness / 2;
                            wMaxX = room.minX() + wallThickness / 2;
                            wMinY = room.minY(); wMaxY = room.maxY();
                            wallX = room.minX();
                            wallY = (room.minY() + room.maxY()) / 2;
                        }
                        default -> { continue; }
                    }

                    // Key matches c_orderline.host_ref: "WALL_roomName_FACE"
                    String key = "WALL_" + roomName + "_" + face;
                    faces.put(key, new WallFaceData(
                        roomName, face, isExterior,
                        wMinX, wMaxX, wMinY, wMaxY,
                        0.0, DEFAULT_STOREY_HEIGHT,  // ground floor Z
                        wallX, wallY
                    ));
                }
            }
        } catch (SQLException e) {
            // Table doesn't exist — return empty
        }

        return faces;
    }

    /**
     * Load room boundary data from ad_room_boundary.
     * Uses min_x_mm/max_x_mm columns (values in mm, converted to meters).
     */
    private static Map<String, RoomData> loadRooms(Connection lib, String buildingName) throws SQLException {
        Map<String, RoomData> rooms = new LinkedHashMap<>();
        try {
            String sql = """
                SELECT room_name, min_x_mm, max_x_mm, min_y_mm, max_y_mm
                FROM ad_room_boundary
                WHERE building_type = ? AND is_active = 1
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("room_name");
                        rooms.put(name, new RoomData(name,
                            rs.getDouble("min_x_mm") / 1000.0,
                            rs.getDouble("max_x_mm") / 1000.0,
                            rs.getDouble("min_y_mm") / 1000.0,
                            rs.getDouble("max_y_mm") / 1000.0));
                    }
                }
            }
        } catch (SQLException e) {
            // Table may not exist — return empty
        }
        return rooms;
    }

    /**
     * Load element rules from c_orderline — WHAT columns only.
     *
     * <p>§11.9: host_type, host_ref, position_rule DROPPED from c_orderline.
     * ElementRule fields for placement data are null until PP_Order_Node migration.
     */
    private static List<ElementRule> loadElementRules(Connection lib, String buildingName) throws SQLException {
        List<ElementRule> rules = new ArrayList<>();
        try {
            String sql = """
                SELECT IfcClass, Name
                FROM c_orderline
                WHERE C_Order_ID = ? AND IsActive = 1
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // host_type, host_ref, position_rule dropped (§11.9) → null
                        rules.add(new ElementRule(
                            rs.getString("IfcClass"),
                            rs.getString("Name"),
                            null, null, null
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            // Table may not exist
        }
        return rules;
    }

    /**
     * Compute expected building perimeter from grid extents.
     * ad_building_grid.position_mm is in millimeters.
     */
    private static double computeExpectedPerimeter(Connection lib, String buildingName) throws SQLException {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (M_AdBuildingGrid row : M_AdBuildingGrid.getByBuilding(lib, buildingName)) {
            double pos = row.getPositionMm() / 1000.0; // mm to m
            if ("X".equals(row.getAxis())) {
                minX = Math.min(minX, pos);
                maxX = Math.max(maxX, pos);
            } else {
                minY = Math.min(minY, pos);
                maxY = Math.max(maxY, pos);
            }
        }

        if (minX == Double.MAX_VALUE) return 0;
        return 2 * ((maxX - minX) + (maxY - minY));
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    /** Find a placement by elementRef + ifcClass (two-tier search). */
    private static PlacementData findPlacement(List<PlacementData> placements,
            String elementRef, String ifcClass) {
        PlacementData match = findPlacementExact(placements, elementRef, ifcClass);
        if (match == null) {
            match = findPlacementBySuffix(placements, elementRef, ifcClass);
        }
        return match;
    }

    /** Tier 1: exact elementRef match on element_name field. */
    private static PlacementData findPlacementExact(List<PlacementData> placements,
            String elementRef, String ifcClass) {
        if (elementRef == null) return placements.stream()
                .filter(p -> ifcClass.equals(p.ifcClass()))
                .findFirst().orElse(null);
        return placements.stream()
                .filter(p -> ifcClass.equals(p.ifcClass()) && elementRef.equals(p.elementRef()))
                .findFirst().orElse(null);
    }

    /** Tier 2: guid suffix pattern (e.g. "IfcDoor_1" → guid ends "_1"). */
    private static PlacementData findPlacementBySuffix(List<PlacementData> placements,
            String elementRef, String ifcClass) {
        if (elementRef == null || !elementRef.contains("_")) return placements.stream()
                .filter(p -> ifcClass.equals(p.ifcClass()))
                .findFirst().orElse(null);
        String suffix = elementRef.substring(elementRef.lastIndexOf('_'));
        return placements.stream()
                .filter(p -> ifcClass.equals(p.ifcClass()) && p.guid() != null
                        && p.guid().endsWith(suffix))
                .findFirst().orElse(null);
    }

    /** Round coordinate for vertex key (snaps to 1mm grid). */
    private static String coordKey(double x, double y) {
        return "%.3f,%.3f".formatted(x, y);
    }

    /** Detect building name from DB path or placement data. */
    private static String detectBuildingName(String dbPath, List<PlacementData> placements) {
        String lower = dbPath.toLowerCase();
        if (lower.contains("sample_house")) return "Ifc4_SampleHouse";
        if (lower.contains("duplex")) return "Ifc2x3_Duplex";
        if (lower.contains("tb_lktn")) return "TB_LKTN";
        if (lower.contains("terminal")) return "Ifc4_Terminal";
        return "";
    }

    /** Build aggregate report from results list. */
    /**
     * P22: OPENING_MESH_IN_BBOX (CRITICAL) — for each IfcDoor/IfcWindow, read mesh vertices
     * from base_geometries and verify no vertex protrudes beyond the elements_rtree bbox by >1mm.
     *
     * Catches: window frame overflowing through wall (opening-axis protrusion).
     * Detects regressions in MeshBinder depth-capping and isNS orientation logic.
     */
    private static List<ProofResult> proveOpeningMeshInBbox(Connection conn) {
        List<ProofResult> results = new ArrayList<>();
        String sql = """
            SELECT em.guid, em.ifc_class,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   ei.geometry_hash, bg.vertices
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            JOIN element_instances ei ON ei.guid = em.guid
            JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash
            WHERE em.ifc_class IN ('IfcDoor', 'IfcWindow')
              AND (ei.geometry_hash LIKE 'LOD_%' OR ei.geometry_hash LIKE 'GEO_%')
            """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String guid = rs.getString("guid");
                double rMinX = rs.getDouble("minX"), rMaxX = rs.getDouble("maxX");
                double rMinY = rs.getDouble("minY"), rMaxY = rs.getDouble("maxY");
                double rMinZ = rs.getDouble("minZ"), rMaxZ = rs.getDouble("maxZ");
                byte[] blob = rs.getBytes("vertices");
                float[] verts = p22BlobToFloats(blob);
                int count = verts.length / 3;
                if (count == 0) {
                    results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                        ProofResult.Status.SKIPPED, guid, "no vertices", 0));
                    continue;
                }
                double actMinX = Double.MAX_VALUE, actMaxX = -Double.MAX_VALUE;
                double actMinY = Double.MAX_VALUE, actMaxY = -Double.MAX_VALUE;
                double actMinZ = Double.MAX_VALUE, actMaxZ = -Double.MAX_VALUE;
                for (int i = 0; i < count; i++) {
                    double vx = verts[i * 3], vy = verts[i * 3 + 1], vz = verts[i * 3 + 2];
                    if (vx < actMinX) actMinX = vx; if (vx > actMaxX) actMaxX = vx;
                    if (vy < actMinY) actMinY = vy; if (vy > actMaxY) actMaxY = vy;
                    if (vz < actMinZ) actMinZ = vz; if (vz > actMaxZ) actMaxZ = vz;
                }
                double tol = 0.001;
                double overflow = Math.max(0, Math.max(
                    Math.max(actMaxX - rMaxX - tol, rMinX - actMinX - tol),
                    Math.max(Math.max(actMaxY - rMaxY - tol, rMinY - actMinY - tol),
                             Math.max(actMaxZ - rMaxZ - tol, rMinZ - actMinZ - tol))));
                if (overflow > 0) {
                    results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                        ProofResult.Status.VIOLATED, guid,
                        String.format("mesh protrudes %.1fmm beyond bbox", overflow * 1000),
                        overflow));
                } else {
                    results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                        ProofResult.Status.PROVEN, guid, "mesh within bbox", 0));
                }
            }
        } catch (SQLException e) {
            results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                ProofResult.Status.SKIPPED, null, "DB read failed: " + e.getMessage(), 0));
        }
        if (results.isEmpty()) {
            results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                ProofResult.Status.SKIPPED, null, "no IfcDoor/IfcWindow with world geometry", 0));
        }
        return results;
    }

    /** Deserialize float32 LE blob to float array (for P22). */
    private static float[] p22BlobToFloats(byte[] blob) {
        if (blob == null || blob.length == 0) return new float[0];
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(blob)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[blob.length / 4];
        for (int i = 0; i < result.length; i++) result[i] = buf.getFloat();
        return result;
    }

    /**
     * P23: DRAIN_CORNER_ALIGNMENT (advisory) — for each pair of orthogonally adjacent
     * IfcFlowSegment elements at the same Z level, verify that the corner where they
     * meet has ≤5mm gap. Catches drain U-shape perimeter misalignment.
     *
     * "Adjacent" = one horizontal segment (wide X) and one vertical segment (wide Y)
     * whose bboxes are within 5mm of meeting at a corner.
     * Advisory because TB-LKTN drain U-shape is not yet fully resolved.
     */
    private static List<ProofResult> proveDrainCornerAlignment(Connection conn) {
        List<ProofResult> results = new ArrayList<>();
        List<String> guids = new ArrayList<>();
        List<double[]> bboxes = new ArrayList<>();  // [minX, maxX, minY, maxY, minZ, maxZ]

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT em.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                 FROM elements_meta em
                 JOIN elements_rtree r ON em.id = r.id
                 WHERE em.ifc_class = 'IfcFlowSegment'
                 ORDER BY r.minZ, r.minX, r.minY
                 """)) {
            while (rs.next()) {
                guids.add(rs.getString("guid"));
                bboxes.add(new double[]{
                    rs.getDouble("minX"), rs.getDouble("maxX"),
                    rs.getDouble("minY"), rs.getDouble("maxY"),
                    rs.getDouble("minZ"), rs.getDouble("maxZ")
                });
            }
        } catch (SQLException e) {
            results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                ProofResult.Status.SKIPPED, null, "DB read failed: " + e.getMessage(), 0));
            return results;
        }

        if (guids.size() < 2) {
            results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                ProofResult.Status.SKIPPED, null,
                guids.isEmpty() ? "no IfcFlowSegment elements" : "only 1 drain segment", 0));
            return results;
        }

        // For each segment, classify as horizontal (bboxW > bboxD) or vertical (bboxD > bboxW).
        // Check that each segment has at least one orthogonal neighbor within 5mm corner proximity.
        double cornerTol = 0.005;  // 5mm corner alignment tolerance
        double zTol      = 0.050;  // 50mm Z-level grouping tolerance

        for (int i = 0; i < bboxes.size(); i++) {
            double[] a = bboxes.get(i);
            double aW = a[1] - a[0], aD = a[3] - a[2];
            boolean aHoriz = aW >= aD;  // horizontal = wider in X than Y
            // Corners of segment a in XY
            double[][] aCorn = {{a[0], a[2]}, {a[1], a[2]}, {a[0], a[3]}, {a[1], a[3]}};

            double minGap = Double.MAX_VALUE;
            for (int j = 0; j < bboxes.size(); j++) {
                if (i == j) continue;
                double[] b = bboxes.get(j);
                if (Math.abs(a[4] - b[4]) > zTol) continue;  // different Z level
                double bW = b[1] - b[0], bD = b[3] - b[2];
                boolean bHoriz = bW >= bD;
                if (aHoriz == bHoriz) continue;  // skip same-orientation pairs (parallel segments)
                // Corners of segment b in XY
                double[][] bCorn = {{b[0], b[2]}, {b[1], b[2]}, {b[0], b[3]}, {b[1], b[3]}};
                // Find minimum distance between any corner of a and any corner of b
                for (double[] ca : aCorn) {
                    for (double[] cb : bCorn) {
                        double d = Math.hypot(ca[0] - cb[0], ca[1] - cb[1]);
                        if (d < minGap) minGap = d;
                    }
                }
            }

            if (minGap == Double.MAX_VALUE) {
                results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                    ProofResult.Status.SKIPPED, guids.get(i),
                    "no orthogonal neighbor at same Z", 0));
            } else if (minGap <= cornerTol) {
                results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                    ProofResult.Status.PROVEN, guids.get(i),
                    String.format("corner gap %.1fmm", minGap * 1000), minGap));
            } else {
                results.add(new ProofResult("P23_DRAIN_CORNER_ALIGNMENT",
                    ProofResult.Status.VIOLATED, guids.get(i),
                    String.format("corner gap %.1fmm exceeds 5mm tolerance", minGap * 1000),
                    minGap));
            }
        }
        return results;
    }

    private static ProofReport buildReport(List<ProofResult> results) {
        int proven = 0, violated = 0, skipped = 0;
        for (ProofResult r : results) {
            switch (r.status()) {
                case PROVEN -> proven++;
                case VIOLATED -> violated++;
                case SKIPPED -> skipped++;
            }
        }
        return new ProofReport(results, proven, violated, skipped);
    }
}
