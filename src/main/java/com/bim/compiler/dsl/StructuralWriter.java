package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.ElementPersistence.BoxGeometry;
import com.bim.compiler.geometry.Point3D;

import java.sql.*;
import java.util.*;

/**
 * Phase 103: Structural element writing — walls, columns, beams, roof.
 * Extracted from BuildingWriter for bus-factor reduction. Zero behavioral change.
 */
class StructuralWriter {

    private final ElementPersistence ep;
    private final Connection conn;

    /** Phase 88: Wall assembly index for direct opening->wall linking */
    final List<WallRegion> wallAssemblyIndex = new ArrayList<>();

    /** Phase 88: Wall region for spatial indexing of wall assemblies */
    record WallRegion(String assemblyGuid, String storey,
                      double minX, double maxX, double minY, double maxY,
                      double minZ, double maxZ) {}

    StructuralWriter(ElementPersistence ep, Connection conn) {
        this.ep = ep;
        this.conn = conn;
    }

    void writeWallAssembly(WallAssemblySpec wall, String storeyName,
                           ConstructionSystem constructionSystem) throws SQLException {
        // Phase 65: Extract fire rating in hours (null if NONE)
        Double fireRatingHr = null;
        if (wall.fireRating() != null && wall.fireRating() != FireRating.NONE) {
            fireRatingHr = wall.fireRating().getRatingHours();
        }

        // Phase 50B.1: Branch on construction system
        if (constructionSystem == ConstructionSystem.MASONRY) {
            // MASONRY: Single IfcWall element (no frame/cladding decomposition)
            String wallGuid = "WALL_" + wall.assemblyName() + "_" + storeyName;
            CladdingSpec cladding = wall.cladding();
            ep.writeElement(
                wallGuid,
                "IfcWall",
                cladding.material(),
                wall.wallType() != null ? wall.wallType().name() : "WALL",
                storeyName,
                ep.createBoxGeometry(
                    cladding.minX(), cladding.minY(), cladding.minZ(),
                    cladding.maxX(), cladding.maxY(), cladding.maxZ()
                ),
                fireRatingHr
            );
            return;
        }

        // FRAMED: Frame members + cladding plate (existing behavior)
        String assemblyGuid = "ASSEMBLY_" + wall.assemblyName() + "_" + storeyName.toUpperCase();

        // Write assembly (ifc_class=NULL for BOM-only wall assemblies)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_assemblies VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, wall.assemblyType());
            ps.setNull(3, java.sql.Types.VARCHAR);  // ifc_class (BOM-only)
            ps.setString(4, wall.assemblyName());
            ps.setDouble(5, wall.length());
            ps.setDouble(6, wall.thickness());
            ps.setDouble(7, wall.height());
            ps.setString(8, storeyName);
            ps.execute();
        }

        int seq = 0;

        // Write frames - include wall name for uniqueness
        int frameIdx = 0;
        for (FrameSpec frame : wall.frames()) {
            String frameGuid = "FRAME_" + wall.assemblyName() + "_" + frame.role() + "_" + (frameIdx++) + "_" + storeyName;
            ep.writeElement(
                frameGuid,
                "IfcMember",
                frame.profile(),
                "FRAME",
                storeyName,
                ep.createBoxGeometry(
                    frame.minX(), frame.minY(), frame.minZ(),
                    frame.maxX(), frame.maxY(), frame.maxZ()
                )
            );

            ep.writeAssemblyComponent(assemblyGuid, frameGuid, "FRAME", 0, 0, 0, seq++);
        }

        // Write cladding - include wall name for uniqueness
        // Phase 65: Include fire rating on cladding (main wall surface)
        String claddingGuid = "CLADDING_" + wall.assemblyName() + "_" + storeyName;
        ep.writeElement(
            claddingGuid,
            "IfcPlate",
            wall.cladding().material(),
            "CLADDING",
            storeyName,
            ep.createBoxGeometry(
                wall.cladding().minX(), wall.cladding().minY(), wall.cladding().minZ(),
                wall.cladding().maxX(), wall.cladding().maxY(), wall.cladding().maxZ()
            ),
            fireRatingHr
        );

        ep.writeAssemblyComponent(assemblyGuid, claddingGuid, "CLADDING", 0, 0, 0, seq);

        // Phase 88: Index wall assembly for direct opening->wall linking
        // Normalize bounds (cladding may have swapped min/max for west/south walls)
        CladdingSpec clad = wall.cladding();
        wallAssemblyIndex.add(new WallRegion(assemblyGuid, storeyName,
            Math.min(clad.minX(), clad.maxX()), Math.max(clad.minX(), clad.maxX()),
            Math.min(clad.minY(), clad.maxY()), Math.max(clad.minY(), clad.maxY()),
            Math.min(clad.minZ(), clad.maxZ()), Math.max(clad.minZ(), clad.maxZ())));
    }

    /**
     * Phase 50B.1: Write structural column.
     * Uses IfcColumn for proper IFC classification.
     */
    void writeColumn(ColumnSpec column, String storeyName) throws SQLException {
        String guid = "COLUMN_" + column.id().toUpperCase() + "_" + storeyName;

        // Column bounds: centered at (x, y), extending from z to z+height
        double halfW = column.width() / 2;
        double halfD = column.depth() / 2;

        double minX = column.x() - halfW;
        double maxX = column.x() + halfW;
        double minY = column.y() - halfD;
        double maxY = column.y() + halfD;
        double minZ = column.z();
        double maxZ = column.z() + column.height();

        ep.writeElement(
            guid,
            "IfcColumn",
            String.format("%.0fx%.0f", column.width() * 1000, column.depth() * 1000),
            column.columnType().toUpperCase(),
            storeyName,
            ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ)
        );
    }

    /**
     * Phase 4 Contract Architecture: Write spanning column that crosses multiple storeys.
     * Uses continuityId for identification. Single INSERT with combined height.
     *
     * <p>Per Watchdog watch brief:
     * - Column base Z = storey.level (lowest storey)
     * - Column top Z = base + totalHeight (continuous through slabs)
     * - Single INSERT per continuityId
     */
    void writeSpanningColumn(SpanningColumnInfo spanning) throws SQLException {
        // Use continuityId as unique identifier across storeys
        String guid = "COLUMN_" + spanning.continuityId().toUpperCase();

        // Column bounds: centered at (x, y), spanning from baseZ to baseZ + totalHeight
        double halfW = spanning.width() / 2;
        double halfD = spanning.depth() / 2;

        double minX = spanning.x() - halfW;
        double maxX = spanning.x() + halfW;
        double minY = spanning.y() - halfD;
        double maxY = spanning.y() + halfD;
        double minZ = spanning.baseZ();
        double maxZ = spanning.baseZ() + spanning.totalHeight();

        System.out.printf("[PHASE4] Spanning column %s: Z[%.2f-%.2f] = %.2fm (%d storeys)%n",
            spanning.continuityId(), minZ, maxZ, spanning.totalHeight(), spanning.storeyCount());

        ep.writeElement(
            guid,
            "IfcColumn",
            String.format("%.0fx%.0f", spanning.width() * 1000, spanning.depth() * 1000),
            spanning.columnType().toUpperCase(),
            spanning.lowestStorey(),  // Attribute to lowest storey for containment
            ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ)
        );
    }

    /**
     * Phase 50B.1: Write structural beam or lintel.
     * Per IFC 4x3: IfcBeam for all beam types including lintels (PredefinedType=LINTEL).
     * IfcMember reserved for wall studs, bracing, generic members.
     */
    void writeBeam(BeamSpec beam, String storeyName) throws SQLException {
        String guid = "BEAM_" + beam.id().toUpperCase() + "_" + storeyName;

        // Beam bounds: centered at (x, y, z), with length along rotation axis
        double halfW = beam.width() / 2;
        double halfH = beam.height() / 2;
        double halfL = beam.length() / 2;

        // Apply rotation for beam direction
        double cos = Math.cos(beam.rotation());
        double sin = Math.sin(beam.rotation());

        // For simplicity, axis-aligned bounding box
        double extentX = Math.abs(halfL * cos) + halfW * Math.abs(sin);
        double extentY = Math.abs(halfL * sin) + halfW * Math.abs(cos);

        double minX = beam.x() - extentX;
        double maxX = beam.x() + extentX;
        double minY = beam.y() - extentY;
        double maxY = beam.y() + extentY;
        double minZ = beam.z() - halfH;
        double maxZ = beam.z() + halfH;

        // Per IFC 4x3: IfcBeam for all beam types (BEAM, LINTEL, JOIST, SPANDREL, T_BEAM)
        String ifcClass = "IfcBeam";

        ep.writeElement(
            guid,
            ifcClass,
            String.format("%.0fx%.0f L=%.0f", beam.width() * 1000, beam.height() * 1000, beam.length() * 1000),
            beam.beamType().toUpperCase(),
            storeyName,
            ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ)
        );
    }

    void writeRoof(RoofSpec roof, String storeyName) throws SQLException {
        String roofGuid = "ROOF_" + roof.type();

        float[] vertices = new float[roof.vertices().size() * 3];
        for (int i = 0; i < roof.vertices().size(); i++) {
            Point3D v = roof.vertices().get(i);
            vertices[i * 3] = (float) v.x();
            vertices[i * 3 + 1] = (float) v.y();
            vertices[i * 3 + 2] = (float) v.z();
        }

        int[] faces = new int[roof.faces().size() * 3];
        for (int i = 0; i < roof.faces().size(); i++) {
            int[] face = roof.faces().get(i);
            faces[i * 3] = face[0];
            faces[i * 3 + 1] = face[1];
            faces[i * 3 + 2] = face[2];
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : roof.vertices()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }

        String geoHash = ep.writeGeometry(vertices, faces);
        ep.writeElementMeta(roofGuid, "IfcRoof", "Gable Roof", "PITCH_" + (int) roof.pitchDegrees(), storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(roofGuid, geoHash);
    }

    // =========================================================================
    // Inner type: SpanningColumnInfo (used by writeSpanningColumn)
    // Extracted from BuildingWriter — same structure, package-private visibility
    // =========================================================================

    static class SpanningColumnInfo {
        private final String continuityId;
        private final String columnType;
        private final double x, y;
        private final double baseZ;          // Z of lowest storey
        private double totalHeight;          // Accumulated height
        private final double width, depth;
        private final String geometryHash;
        private final String lowestStorey;
        private final List<String> storeys;

        SpanningColumnInfo(String continuityId, String columnType,
                          double x, double y, double baseZ, double height,
                          double width, double depth, String geometryHash,
                          String lowestStorey) {
            this.continuityId = continuityId;
            this.columnType = columnType;
            this.x = x;
            this.y = y;
            this.baseZ = baseZ;
            this.totalHeight = height;
            this.width = width;
            this.depth = depth;
            this.geometryHash = geometryHash;
            this.lowestStorey = lowestStorey;
            this.storeys = new ArrayList<>();
            this.storeys.add(lowestStorey);
        }

        void extendHeight(double additionalHeight) {
            this.totalHeight += additionalHeight;
        }

        void addStorey(String storeyName) {
            if (!storeys.contains(storeyName)) {
                storeys.add(storeyName);
            }
        }

        int storeyCount() { return storeys.size(); }
        String continuityId() { return continuityId; }
        String columnType() { return columnType; }
        double x() { return x; }
        double y() { return y; }
        double baseZ() { return baseZ; }
        double totalHeight() { return totalHeight; }
        double width() { return width; }
        double depth() { return depth; }
        String geometryHash() { return geometryHash; }
        String lowestStorey() { return lowestStorey; }
    }
}
