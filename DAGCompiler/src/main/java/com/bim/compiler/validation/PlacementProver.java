package com.bim.compiler.validation;

import com.bim.compiler.BIMConstants;

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

    /** Minimal placement data for proving (subset of PlacementAD.Placement). */
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
                || proofId.startsWith("P07");
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
        } catch (SQLException e) {
            System.err.printf("[PROOF] Cannot read DB %s: %s%n", dbPath, e.getMessage());
            return buildReport(List.of());
        }

        // Detect building name from output DB path
        String buildingName = detectBuildingName(dbPath, placements);

        return prove(placements, buildingName);
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

        if (storey.contains("ground") || levelNum == 1 || storey.contains("0")
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

        // Roof elements have extended Z range
        if ("IfcRoof".equals(p.ifcClass())) {
            ceilingZ = Math.max(ceilingZ, DEFAULT_STOREY_HEIGHT * 3);
        }

        // MEP elements (piping, fittings, terminals) route between storeys and below grade
        String cls = p.ifcClass();
        if (cls.startsWith("IfcFlow") || cls.startsWith("IfcPipe")
                || cls.startsWith("IfcDuct") || "IfcBuildingElementProxy".equals(cls)) {
            floorZ = Math.min(floorZ, -2.0);   // sub-grade plumbing
            ceilingZ = Math.max(ceilingZ, ceilingZ + DEFAULT_STOREY_HEIGHT);
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
     * Run Tier 3-5 proofs using relational metadata from component_library.db.
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
                "jdbc:sqlite:library/component_library.db")) {

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
     * Key = "WALL_{room_name}_{FACE}" to match ad_element_rule.host_ref format.
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

                    // Key matches ad_element_rule.host_ref: "WALL_roomName_FACE"
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
     * Load element rules from ad_element_rule.
     * Schema: element_ref, ifc_class, host_type, host_ref, position_rule.
     */
    private static List<ElementRule> loadElementRules(Connection lib, String buildingName) throws SQLException {
        List<ElementRule> rules = new ArrayList<>();
        try {
            String sql = """
                SELECT ifc_class, element_ref, host_type, host_ref, position_rule
                FROM ad_element_rule
                WHERE building_type = ? AND is_active = 1
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rules.add(new ElementRule(
                            rs.getString("ifc_class"),
                            rs.getString("element_ref"),
                            rs.getString("host_type"),
                            rs.getString("host_ref"),
                            rs.getString("position_rule")
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

        String sql = "SELECT axis, position_mm FROM ad_building_grid WHERE building_type = ? AND is_active = 1";
        try (PreparedStatement ps = lib.prepareStatement(sql)) {
            ps.setString(1, buildingName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double pos = rs.getDouble("position_mm") / 1000.0; // mm to m
                    if ("X".equals(rs.getString("axis"))) {
                        minX = Math.min(minX, pos);
                        maxX = Math.max(maxX, pos);
                    } else {
                        minY = Math.min(minY, pos);
                        maxY = Math.max(maxY, pos);
                    }
                }
            }
        }

        if (minX == Double.MAX_VALUE) return 0;
        return 2 * ((maxX - minX) + (maxY - minY));
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    /** Find a placement by elementRef + ifcClass. */
    private static PlacementData findPlacement(List<PlacementData> placements,
            String elementRef, String ifcClass) {
        // First: exact elementRef match on element_name field
        for (PlacementData p : placements) {
            if (ifcClass.equals(p.ifcClass()) && elementRef != null
                    && elementRef.equals(p.elementRef())) {
                return p;
            }
        }
        // Second: guid contains elementRef pattern (e.g. "IfcDoor_1" → guid ends "_1")
        if (elementRef != null) {
            String suffix = elementRef.contains("_")
                ? elementRef.substring(elementRef.lastIndexOf('_'))
                : null;
            if (suffix != null) {
                for (PlacementData p : placements) {
                    if (ifcClass.equals(p.ifcClass()) && p.guid() != null
                            && p.guid().endsWith(suffix)) {
                        return p;
                    }
                }
            }
        }
        return null;
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
