package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.*;

/**
 * Check 25: Opening Orientation (Phase 81B Enhanced)
 *
 * Verifies that doors and windows are:
 * 1. Linked to wall assemblies in BOM (via assembly_components)
 * 2. Geometrically aligned: opening depth matches wall depth direction
 * 3. Not protruding beyond wall face (vertex-level inspection)
 *
 * VERTEX-LEVEL CHECKS:
 * - Wall has "thin" dimension (depth) and "long" dimension (length)
 * - Opening's thin dimension must align with wall's thin dimension
 * - Opening must not extend beyond wall bounds in the depth direction
 *
 * BOM ASSEMBLY PATTERN:
 *   WALL_PANEL_ASSEMBLY
 *   ├── FRAME (structural)
 *   ├── CLADDING (surface)
 *   └── OPENING (door/window) ← local offset from wall origin
 */
public class OpeningOrientationCheck implements SanityCheck {

    // Tolerances (meters)
    private static final double WALL_TOLERANCE = 0.2;      // For wall-finding fallback
    private static final double PROTRUSION_TOLERANCE = 0.15; // Max acceptable protrusion (150mm)
    private static final double DEPTH_ALIGNMENT_TOLERANCE = 0.3; // Depth direction alignment

    @Override
    public String getId() { return "opening_orientation"; }

    @Override
    public String getName() { return "Opening Orientation"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> doors = model.getDoors();
        List<Element> windows = model.getWindows();

        if (doors.isEmpty() && windows.isEmpty()) {
            return CheckResult.pass(getId(), getName())
                .summary("No openings to check (N/A)")
                .build();
        }

        List<String> issues = new ArrayList<>();
        List<String> details = new ArrayList<>();
        int totalOpenings = doors.size() + windows.size();
        int bomLinked = 0;
        int vertexAligned = 0;
        int protrusionFails = 0;
        int depthAlignFails = 0;

        // Get BOM assembly links from database
        Map<String, WallOpeningLink> bomLinks = new HashMap<>();
        try {
            bomLinks = loadBOMLinks(model);
        } catch (SQLException e) {
            details.add("Warning: Could not load BOM assembly data: " + e.getMessage());
        }

        // Check each door
        for (Element door : doors) {
            if (door.bbox() == null) continue;

            WallOpeningLink link = bomLinks.get(door.guid());
            if (link != null) {
                bomLinked++;

                // Vertex-level check: protrusion and depth alignment
                VertexCheckResult vcr = checkVertexAlignment(door.bbox(), link.wallBounds);
                if (vcr.aligned) {
                    vertexAligned++;
                } else {
                    if (vcr.protrusion > PROTRUSION_TOLERANCE) {
                        protrusionFails++;
                        issues.add(String.format("Door %s protrudes %.0fmm beyond wall",
                            door.guid(), vcr.protrusion * 1000));
                    }
                    if (!vcr.depthAligned) {
                        depthAlignFails++;
                        issues.add(String.format("Door %s depth direction misaligned with wall",
                            door.guid()));
                    }
                }
            } else {
                // Fallback: geometric wall finding + vertex check
                OpeningFallbackResult fbr = checkOpeningFallback(door, "Door", model);
                bomLinked += fbr.linked ? 1 : 0;
                vertexAligned += fbr.aligned ? 1 : 0;
                if (fbr.protrusion > PROTRUSION_TOLERANCE) {
                    protrusionFails++;
                    issues.add(String.format("Door %s protrudes %.0fmm beyond wall (fallback)",
                        door.guid(), fbr.protrusion * 1000));
                }
                if (!fbr.depthAligned && fbr.linked) {
                    depthAlignFails++;
                    issues.add(String.format("Door %s depth direction misaligned (fallback)", door.guid()));
                }
                if (!fbr.linked) {
                    issues.add(String.format("Door %s not linked to any wall (floating)", door.guid()));
                }
            }
        }

        // Check each window
        for (Element window : windows) {
            if (window.bbox() == null) continue;

            WallOpeningLink link = bomLinks.get(window.guid());
            if (link != null) {
                bomLinked++;

                VertexCheckResult vcr = checkVertexAlignment(window.bbox(), link.wallBounds);
                if (vcr.aligned) {
                    vertexAligned++;
                } else {
                    if (vcr.protrusion > PROTRUSION_TOLERANCE) {
                        protrusionFails++;
                        issues.add(String.format("Window %s protrudes %.0fmm beyond wall",
                            window.guid(), vcr.protrusion * 1000));
                    }
                    if (!vcr.depthAligned) {
                        depthAlignFails++;
                        issues.add(String.format("Window %s depth direction misaligned",
                            window.guid()));
                    }
                }
            } else {
                // Fallback: geometric wall finding + vertex check
                OpeningFallbackResult fbr = checkOpeningFallback(window, "Window", model);
                bomLinked += fbr.linked ? 1 : 0;
                vertexAligned += fbr.aligned ? 1 : 0;
                if (fbr.protrusion > PROTRUSION_TOLERANCE) {
                    protrusionFails++;
                    issues.add(String.format("Window %s protrudes %.0fmm beyond wall (fallback)",
                        window.guid(), fbr.protrusion * 1000));
                }
                if (!fbr.depthAligned && fbr.linked) {
                    depthAlignFails++;
                    issues.add(String.format("Window %s depth direction misaligned (fallback)", window.guid()));
                }
            }
        }

        // Build result
        int criticalFails = protrusionFails + (int) issues.stream()
            .filter(s -> s.contains("floating")).count();

        if (criticalFails > 0) {
            StringBuilder detail = new StringBuilder();
            detail.append(String.format("BOM linked: %d/%d openings\n", bomLinked, totalOpenings));
            detail.append(String.format("Vertex aligned: %d/%d\n", vertexAligned, bomLinked));
            detail.append(String.format("Protrusion failures: %d\n", protrusionFails));
            detail.append(String.format("Depth alignment failures: %d\n", depthAlignFails));
            detail.append("\nIssues:\n");

            int shown = 0;
            for (String issue : issues) {
                if (shown < 10) {
                    detail.append("  - ").append(issue).append("\n");
                    shown++;
                }
            }
            if (issues.size() > 10) {
                detail.append(String.format("  ... and %d more\n", issues.size() - 10));
            }

            return CheckResult.fail(getId(), getName())
                .summary(String.format("%d/%d openings have vertex-level issues",
                    criticalFails, totalOpenings))
                .detail(detail.toString())
                .guidance("Openings must not protrude beyond wall faces. Check library forward_axis and rotation.")
                .data("total_openings", totalOpenings)
                .data("bom_linked", bomLinked)
                .data("vertex_aligned", vertexAligned)
                .data("protrusion_fails", protrusionFails)
                .data("depth_align_fails", depthAlignFails)
                .build();
        }

        if (depthAlignFails > 0 || !details.isEmpty()) {
            StringBuilder detail = new StringBuilder();
            detail.append(String.format("BOM linked: %d/%d openings\n", bomLinked, totalOpenings));
            detail.append(String.format("Vertex aligned: %d/%d\n", vertexAligned, bomLinked));
            for (String d : details) {
                detail.append(d).append("\n");
            }

            return CheckResult.warning(getId(), getName())
                .summary(String.format("%d/%d openings pass vertex check", vertexAligned, totalOpenings))
                .detail(detail.toString())
                .data("total_openings", totalOpenings)
                .data("bom_linked", bomLinked)
                .data("vertex_aligned", vertexAligned)
                .build();
        }

        return CheckResult.pass(getId(), getName())
            .summary(String.format("%d/%d openings pass vertex alignment check",
                vertexAligned, totalOpenings))
            .detail(String.format("BOM linked: %d, Vertex aligned: %d", bomLinked, vertexAligned))
            .data("total_openings", totalOpenings)
            .data("bom_linked", bomLinked)
            .data("vertex_aligned", vertexAligned)
            .build();
    }

    /**
     * Link between opening and wall from BOM assembly_components table.
     */
    private record WallOpeningLink(
        String openingGuid,
        String wallAssemblyGuid,
        BoundingBox wallBounds,
        double localX, double localY, double localZ
    ) {}

    /**
     * Result of vertex-level alignment check.
     */
    private record VertexCheckResult(
        boolean aligned,
        boolean depthAligned,
        double protrusion  // How much opening extends beyond wall (meters)
    ) {}

    /**
     * Load BOM links from assembly_components table.
     */
    private Map<String, WallOpeningLink> loadBOMLinks(SanityModel model) throws SQLException {
        Map<String, WallOpeningLink> links = new HashMap<>();

        String sql = """
            SELECT
                ac.component_guid as opening_guid,
                ac.assembly_guid as wall_guid,
                ac.local_x, ac.local_y, ac.local_z,
                MIN(wr.minX) as wall_minX, MAX(wr.maxX) as wall_maxX,
                MIN(wr.minY) as wall_minY, MAX(wr.maxY) as wall_maxY,
                MIN(wr.minZ) as wall_minZ, MAX(wr.maxZ) as wall_maxZ
            FROM assembly_components ac
            JOIN assembly_components wc ON ac.assembly_guid = wc.assembly_guid
                AND wc.role IN ('FRAME', 'CLADDING', 'WALL')
            JOIN elements_meta wm ON wc.component_guid = wm.guid
            JOIN elements_rtree wr ON wm.id = wr.id
            WHERE ac.role = 'OPENING'
            GROUP BY ac.component_guid
            """;

        try (Connection conn = model.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                BoundingBox wallBounds = new BoundingBox(
                    rs.getDouble("wall_minX"), rs.getDouble("wall_minY"), rs.getDouble("wall_minZ"),
                    rs.getDouble("wall_maxX"), rs.getDouble("wall_maxY"), rs.getDouble("wall_maxZ")
                );

                links.put(rs.getString("opening_guid"), new WallOpeningLink(
                    rs.getString("opening_guid"),
                    rs.getString("wall_guid"),
                    wallBounds,
                    rs.getDouble("local_x"),
                    rs.getDouble("local_y"),
                    rs.getDouble("local_z")
                ));
            }
        }

        return links;
    }

    /**
     * Vertex-level alignment check.
     *
     * Checks:
     * 1. Opening depth direction matches wall depth direction
     * 2. Opening doesn't protrude beyond wall face
     */
    private VertexCheckResult checkVertexAlignment(BoundingBox opening, BoundingBox wall) {
        // Determine wall orientation (which axis is "thin")
        double wallXExtent = wall.width();   // maxX - minX
        double wallYExtent = wall.depth();   // maxY - minY

        boolean wallAlongX = wallXExtent > wallYExtent * 2;  // Wall runs along X
        boolean wallAlongY = wallYExtent > wallXExtent * 2;  // Wall runs along Y

        // Determine opening orientation
        double openXExtent = opening.width();
        double openYExtent = opening.depth();

        boolean openAlongX = openXExtent > openYExtent * 1.5;
        boolean openAlongY = openYExtent > openXExtent * 1.5;

        // Check depth alignment: opening thin direction should match wall thin direction
        boolean depthAligned;
        if (wallAlongX) {
            // Wall thin in Y, opening should also be thin in Y (or square-ish)
            depthAligned = !openAlongY || openYExtent < DEPTH_ALIGNMENT_TOLERANCE;
        } else if (wallAlongY) {
            // Wall thin in X, opening should also be thin in X (or square-ish)
            depthAligned = !openAlongX || openXExtent < DEPTH_ALIGNMENT_TOLERANCE;
        } else {
            // Square wall, accept any orientation
            depthAligned = true;
        }

        // Check protrusion: opening shouldn't extend far beyond wall face
        double protrusion = 0;
        if (wallAlongX) {
            // Wall thin in Y - check Y protrusion
            double wallY1 = wall.minY(), wallY2 = wall.maxY();
            double openY1 = opening.minY(), openY2 = opening.maxY();
            protrusion = Math.max(0,
                Math.max(wallY1 - openY1, openY2 - wallY2));
        } else if (wallAlongY) {
            // Wall thin in X - check X protrusion
            double wallX1 = wall.minX(), wallX2 = wall.maxX();
            double openX1 = opening.minX(), openX2 = opening.maxX();
            protrusion = Math.max(0,
                Math.max(wallX1 - openX1, openX2 - wallX2));
        }

        boolean aligned = depthAligned && protrusion <= PROTRUSION_TOLERANCE;
        return new VertexCheckResult(aligned, depthAligned, protrusion);
    }

    /**
     * Result of fallback opening check (geometric wall finding + vertex alignment).
     */
    private record OpeningFallbackResult(
        boolean linked,
        boolean aligned,
        boolean depthAligned,
        double protrusion
    ) {}

    /**
     * Fallback check for openings not found in BOM links.
     * 1. Try geometric wall finding from model walls
     * 2. If that fails, try rtree SQL query for nearest IfcWall within 0.3m
     * 3. Run vertex alignment check on any match found
     */
    private OpeningFallbackResult checkOpeningFallback(Element opening, String type, SanityModel model) {
        BoundingBox openingBox = opening.bbox();

        // Try 1: geometric wall finding from loaded walls
        Element hostWall = findHostWall(opening, model.getWalls());
        if (hostWall != null && hostWall.bbox() != null) {
            VertexCheckResult vcr = checkVertexAlignment(openingBox, hostWall.bbox());
            return new OpeningFallbackResult(true, vcr.aligned, vcr.depthAligned, vcr.protrusion);
        }

        // Try 2: rtree SQL query for IfcWall within 0.3m
        BoundingBox wallBounds = findWallByRtree(opening, model);
        if (wallBounds != null) {
            VertexCheckResult vcr = checkVertexAlignment(openingBox, wallBounds);
            return new OpeningFallbackResult(true, vcr.aligned, vcr.depthAligned, vcr.protrusion);
        }

        // No wall found at all
        return new OpeningFallbackResult(false, false, true, 0);
    }

    /**
     * Rtree fallback: query elements_rtree for nearest IfcWall within 0.3m.
     */
    private BoundingBox findWallByRtree(Element opening, SanityModel model) {
        BoundingBox ob = opening.bbox();
        if (ob == null) return null;

        double margin = 0.3; // 300mm search radius

        String sql = """
            SELECT r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = 'IfcWall'
              AND r.minX < ? AND r.maxX > ?
              AND r.minY < ? AND r.maxY > ?
              AND r.minZ < ? AND r.maxZ > ?
            ORDER BY ABS((r.minX+r.maxX)/2 - ?) + ABS((r.minY+r.maxY)/2 - ?)
            LIMIT 1
            """;

        try (Connection conn = model.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, ob.maxX() + margin);
            ps.setDouble(2, ob.minX() - margin);
            ps.setDouble(3, ob.maxY() + margin);
            ps.setDouble(4, ob.minY() - margin);
            ps.setDouble(5, ob.maxZ() + margin);
            ps.setDouble(6, ob.minZ() - margin);
            ps.setDouble(7, ob.centerX());
            ps.setDouble(8, ob.centerY());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BoundingBox(
                        rs.getDouble("minX"), rs.getDouble("minY"), rs.getDouble("minZ"),
                        rs.getDouble("maxX"), rs.getDouble("maxY"), rs.getDouble("maxZ")
                    );
                }
            }
        } catch (SQLException e) {
            // Silently fail — fallback is best-effort
        }
        return null;
    }

    /**
     * Fallback: Find the wall that hosts this opening by geometric intersection.
     */
    private Element findHostWall(Element opening, List<Element> walls) {
        BoundingBox openingBox = opening.bbox();
        if (openingBox == null) return null;

        BoundingBox expanded = openingBox.expand(WALL_TOLERANCE);

        for (Element wall : walls) {
            if (wall.bbox() == null) continue;

            if (expanded.intersects(wall.bbox())) {
                if (isOpeningOnWallFace(openingBox, wall.bbox())) {
                    return wall;
                }
            }
        }
        return null;
    }

    /**
     * Check if opening is on the face of the wall.
     */
    private boolean isOpeningOnWallFace(BoundingBox opening, BoundingBox wall) {
        double openCX = opening.centerX();
        double openCY = opening.centerY();

        double wallWidth = wall.width();
        double wallDepth = wall.depth();

        if (wallWidth > wallDepth * 2) {
            return Math.abs(openCY - wall.centerY()) < WALL_TOLERANCE + wallDepth / 2;
        } else if (wallDepth > wallWidth * 2) {
            return Math.abs(openCX - wall.centerX()) < WALL_TOLERANCE + wallWidth / 2;
        }

        return true;
    }
}
