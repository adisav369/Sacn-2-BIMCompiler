package com.bim.compiler.validation;

import com.bim.compiler.dsl.BuildingSpecs.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 40: Structural-MEP Clash Detection.
 *
 * Detects hard clashes (bounding box intersections) between MEP elements
 * and structural elements. This is the first cross-discipline validation.
 *
 * Conservative approach:
 * - Any bbox intersection = clash
 * - No penetration allowances (future work)
 * - No clearance requirements (use MepStructureClearanceValidator for that)
 *
 * MEP elements checked:
 * - Pipes (PlumbingSpec)
 * - Electrical outlets/switches (ElectricalSpec)
 * - Lights (LightSpec)
 * - Fixtures (FixtureSpec)
 *
 * Structural elements checked:
 * - Beams (BeamSpec)
 * - Columns (ColumnSpec)
 * - Slabs (SlabSpec) - only for vertical MEP penetration
 */
public class ClashDetector {

    /**
     * Clash types.
     */
    public enum ClashType {
        HARD,       // Bounding boxes intersect
        SOFT        // Future: within clearance zone
    }

    /**
     * Clash record containing clash details.
     */
    public record Clash(
        String mepElementId,
        String mepElementType,      // "pipe", "outlet", "light", "fixture"
        String structuralElementId,
        String structuralElementType, // "beam", "column", "slab"
        ClashType type,
        double overlapVolume,       // Approximate overlap in m³
        String storeyName
    ) {
        @Override
        public String toString() {
            return String.format("CLASH: %s (%s) intersects %s (%s) in %s",
                mepElementId, mepElementType,
                structuralElementId, structuralElementType,
                storeyName);
        }
    }

    /**
     * Simple bounding box for clash detection.
     */
    private record BBox(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {
        boolean intersects(BBox other) {
            return minX < other.maxX && maxX > other.minX &&
                   minY < other.maxY && maxY > other.minY &&
                   minZ < other.maxZ && maxZ > other.minZ;
        }

        double overlapVolume(BBox other) {
            if (!intersects(other)) return 0;
            double overlapX = Math.min(maxX, other.maxX) - Math.max(minX, other.minX);
            double overlapY = Math.min(maxY, other.maxY) - Math.max(minY, other.minY);
            double overlapZ = Math.min(maxZ, other.maxZ) - Math.max(minZ, other.minZ);
            return overlapX * overlapY * overlapZ;
        }
    }

    /**
     * Detect hard clashes in a storey.
     *
     * @param storey The storey specification containing all elements
     * @return List of detected clashes (empty if no clashes)
     */
    public List<Clash> detectClashes(StoreySpec storey) {
        List<Clash> clashes = new ArrayList<>();
        String storeyName = storey.name();

        // Collect structural bounding boxes
        List<StructuralBBox> structural = new ArrayList<>();

        // Beams
        for (BeamSpec beam : storey.beams()) {
            structural.add(new StructuralBBox(
                beam.id(),
                "beam",
                beamToBBox(beam)
            ));
        }

        // Columns
        for (ColumnSpec column : storey.columns()) {
            structural.add(new StructuralBBox(
                column.id(),
                "column",
                columnToBBox(column)
            ));
        }

        // Slab (floor) - check for vertical penetrations
        if (storey.slab() != null) {
            structural.add(new StructuralBBox(
                storey.slab().name(),
                "slab",
                slabToBBox(storey.slab())
            ));
        }

        // Check each MEP element against structural

        // 1. Pipes - with penetration allowances
        for (PlumbingSpec pipe : storey.plumbing()) {
            BBox pipeBBox = pipeToBBox(pipe);
            for (StructuralBBox s : structural) {
                if (pipeBBox.intersects(s.bbox)) {
                    // Check if this is an allowed penetration
                    if (isAllowedPenetration(pipe, s.type)) {
                        continue;  // Expected penetration, not a clash
                    }
                    clashes.add(new Clash(
                        pipe.id(),
                        "pipe",
                        s.id,
                        s.type,
                        ClashType.HARD,
                        pipeBBox.overlapVolume(s.bbox),
                        storeyName
                    ));
                }
            }
        }

        // 2. Electrical outlets/switches
        for (ElectricalSpec elec : storey.electricals()) {
            BBox elecBBox = electricalToBBox(elec);
            for (StructuralBBox s : structural) {
                if (elecBBox.intersects(s.bbox)) {
                    clashes.add(new Clash(
                        elec.id(),
                        elec.elementType(),
                        s.id,
                        s.type,
                        ClashType.HARD,
                        elecBBox.overlapVolume(s.bbox),
                        storeyName
                    ));
                }
            }
        }

        // 3. Lights
        for (LightSpec light : storey.lights()) {
            BBox lightBBox = lightToBBox(light);
            for (StructuralBBox s : structural) {
                if (lightBBox.intersects(s.bbox)) {
                    clashes.add(new Clash(
                        light.id(),
                        "light",
                        s.id,
                        s.type,
                        ClashType.HARD,
                        lightBBox.overlapVolume(s.bbox),
                        storeyName
                    ));
                }
            }
        }

        // 4. Fixtures (plumbing fixtures like toilets, sinks)
        for (FixtureSpec fixture : storey.fixtures()) {
            BBox fixtureBBox = fixtureToBBox(fixture);
            for (StructuralBBox s : structural) {
                if (fixtureBBox.intersects(s.bbox)) {
                    clashes.add(new Clash(
                        fixture.id(),
                        fixture.fixtureType(),
                        s.id,
                        s.type,
                        ClashType.HARD,
                        fixtureBBox.overlapVolume(s.bbox),
                        storeyName
                    ));
                }
            }
        }

        return clashes;
    }

    /**
     * Detect clashes across all storeys.
     */
    public List<Clash> detectAllClashes(List<StoreySpec> storeys) {
        List<Clash> allClashes = new ArrayList<>();
        for (StoreySpec storey : storeys) {
            allClashes.addAll(detectClashes(storey));
        }
        return allClashes;
    }

    // =========================================================================
    // Penetration allowance logic
    // =========================================================================

    /**
     * Check if a pipe-structural intersection is an allowed penetration.
     *
     * Allowed penetrations (Phase 41):
     * - Pipes through slabs: waste risers, vents, and branches penetrate floor slabs
     *   to connect to drainage below. This is expected design, not a clash.
     *
     * Future extensions:
     * - Sleeves through fire-rated slabs
     * - Conduit through walls
     * - Duct penetrations
     *
     * @param pipe The plumbing pipe
     * @param structuralType Type of structural element ("slab", "beam", "column")
     * @return true if this is an allowed penetration, false if it's a clash
     */
    private boolean isAllowedPenetration(PlumbingSpec pipe, String structuralType) {
        // Pipes through slabs are allowed (drainage penetrations)
        if ("slab".equals(structuralType)) {
            // All plumbing pipes can penetrate floor slabs
            // Waste risers, vents, and branches all need to go through
            return true;
        }

        // Pipes should NOT go through beams or columns
        return false;
    }

    // =========================================================================
    // Bounding box conversion helpers
    // =========================================================================

    private record StructuralBBox(String id, String type, BBox bbox) {}

    private BBox beamToBBox(BeamSpec beam) {
        // Beam is centered at (x, y, z) with dimensions length x width x height
        // Rotation affects orientation
        double halfLength = beam.length() / 2;
        double halfWidth = beam.width() / 2;
        double halfHeight = beam.height() / 2;

        // Simplified: assume axis-aligned for now
        // TODO: Handle rotation properly
        return new BBox(
            beam.x() - halfLength,
            beam.y() - halfWidth,
            beam.z() - halfHeight,
            beam.x() + halfLength,
            beam.y() + halfWidth,
            beam.z() + halfHeight
        );
    }

    private BBox columnToBBox(ColumnSpec column) {
        // Column at (x, y, z) with section width x depth, extends up by height
        double halfWidth = column.width() / 2;
        double halfDepth = column.depth() / 2;

        return new BBox(
            column.x() - halfWidth,
            column.y() - halfDepth,
            column.z(),
            column.x() + halfWidth,
            column.y() + halfDepth,
            column.z() + column.height()
        );
    }

    private BBox slabToBBox(SlabSpec slab) {
        return new BBox(
            slab.minX(), slab.minY(), slab.minZ(),
            slab.maxX(), slab.maxY(), slab.maxZ()
        );
    }

    private BBox pipeToBBox(PlumbingSpec pipe) {
        // Pipe from (startX, startY, startZ) to (endX, endY, endZ) with diameter
        double radius = pipe.diameterM() / 2;

        return new BBox(
            Math.min(pipe.startX(), pipe.endX()) - radius,
            Math.min(pipe.startY(), pipe.endY()) - radius,
            Math.min(pipe.startZ(), pipe.endZ()) - radius,
            Math.max(pipe.startX(), pipe.endX()) + radius,
            Math.max(pipe.startY(), pipe.endY()) + radius,
            Math.max(pipe.startZ(), pipe.endZ()) + radius
        );
    }

    private BBox electricalToBBox(ElectricalSpec elec) {
        // Electrical element at (x, y, z) with dimensions
        double halfWidth = elec.width() / 2;
        double halfDepth = elec.depth() / 2;
        double halfHeight = elec.height() / 2;

        return new BBox(
            elec.x() - halfWidth,
            elec.y() - halfDepth,
            elec.z() - halfHeight,
            elec.x() + halfWidth,
            elec.y() + halfDepth,
            elec.z() + halfHeight
        );
    }

    private BBox lightToBBox(LightSpec light) {
        // Light at (x, y, z) with dimensions
        double halfWidth = light.width() / 2;
        double halfDepth = light.depth() / 2;
        double halfHeight = light.height() / 2;

        return new BBox(
            light.x() - halfWidth,
            light.y() - halfDepth,
            light.z() - halfHeight,
            light.x() + halfWidth,
            light.y() + halfDepth,
            light.z() + halfHeight
        );
    }

    private BBox fixtureToBBox(FixtureSpec fixture) {
        // Fixture at (x, y, z) with dimensions
        double halfWidth = fixture.width() / 2;
        double halfDepth = fixture.depth() / 2;

        return new BBox(
            fixture.x() - halfWidth,
            fixture.y() - halfDepth,
            fixture.z(),
            fixture.x() + halfWidth,
            fixture.y() + halfDepth,
            fixture.z() + fixture.height()
        );
    }
}
