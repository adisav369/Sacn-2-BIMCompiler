package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Places sprinkler components from library with correct orientation.
 *
 * Handles:
 * - PENDANT sprinklers: attach at TOP, hang down from ceiling
 * - UPRIGHT sprinklers: attach at BOTTOM, spray up from floor
 *
 * From Terminal DB analysis:
 * - Geometry is in LOCAL coordinates centered at origin
 * - Transform (center_x/y/z) provides world position = bbox center
 * - Pendant: more vertices at -Z (deflector points down)
 * - Upright: more vertices at +Z (deflector points up)
 */
public class SprinklerPlacer {

    private final ComponentLibrary library;
    private final ComponentDefinition pendantDef;
    private final ComponentDefinition uprightDef;

    public SprinklerPlacer(ComponentLibrary library) throws SQLException {
        this.library = library;
        this.pendantDef = library.getPendantSprinkler();
        this.uprightDef = library.getUprightSprinkler();
    }

    /**
     * Place a single pendant sprinkler at ceiling position.
     *
     * @param ceilingPoint Point on ceiling where sprinkler attaches
     * @param rotation Rotation in radians around Z axis (0 = default orientation)
     * @return Placed sprinkler instance
     */
    public SprinklerInstance placePendant(Point3D ceilingPoint, double rotation) throws SQLException {
        if (pendantDef == null) {
            throw new IllegalStateException("No pendant sprinkler in library");
        }

        // Pendant attaches at TOP, so offset down by half height
        // Geometry local bounds: Z from -0.029 to +0.029 (58mm)
        // Attachment face is TOP (maxZ), so center is at attachment - halfHeight
        double halfHeight = pendantDef.localBounds().height() / 2;
        Point3D worldCenter = new Point3D(
            ceilingPoint.x(),
            ceilingPoint.y(),
            ceilingPoint.z() - halfHeight - pendantDef.localBounds().maxZ()
        );

        return new SprinklerInstance(
            pendantDef,
            worldCenter,
            rotation,
            SprinklerType.PENDANT
        );
    }

    /**
     * Place a single upright sprinkler at floor position.
     *
     * @param floorPoint Point on floor where sprinkler stands
     * @param rotation Rotation in radians around Z axis
     * @return Placed sprinkler instance
     */
    public SprinklerInstance placeUpright(Point3D floorPoint, double rotation) throws SQLException {
        if (uprightDef == null) {
            throw new IllegalStateException("No upright sprinkler in library");
        }

        // Upright attaches at BOTTOM, so offset up by half height
        double halfHeight = uprightDef.localBounds().height() / 2;
        Point3D worldCenter = new Point3D(
            floorPoint.x(),
            floorPoint.y(),
            floorPoint.z() + halfHeight - uprightDef.localBounds().minZ()
        );

        return new SprinklerInstance(
            uprightDef,
            worldCenter,
            rotation,
            SprinklerType.UPRIGHT
        );
    }

    /**
     * Place sprinklers in a grid pattern.
     * Uses NFPA 13 spacing rule (4.6m max for light hazard).
     *
     * @param area Rectangular area to cover
     * @param ceilingZ Z-level of ceiling
     * @param spacing Grid spacing in meters (default: 4.6m)
     * @return List of placed sprinkler instances
     */
    public List<SprinklerInstance> placeGrid(BoundingBox area, double ceilingZ, double spacing) throws SQLException {
        List<SprinklerInstance> instances = new ArrayList<>();

        // Use pendant sprinklers for ceiling grid
        if (pendantDef == null) {
            throw new IllegalStateException("No pendant sprinkler in library");
        }

        double startX = area.minX() + spacing / 2;
        double startY = area.minY() + spacing / 2;

        for (double x = startX; x < area.maxX(); x += spacing) {
            for (double y = startY; y < area.maxY(); y += spacing) {
                Point3D ceilingPoint = new Point3D(x, y, ceilingZ);
                instances.add(placePendant(ceilingPoint, 0.0));
            }
        }

        return instances;
    }

    /**
     * Get transformed vertices for an instance.
     */
    public float[] getTransformedVertices(SprinklerInstance instance) throws SQLException {
        ComponentGeometry geom = library.getGeometry(instance.definition().geometryHash());
        if (geom == null) {
            return new float[0];
        }

        // Parse local vertices
        ByteBuffer buffer = ByteBuffer.wrap(geom.vertices()).order(ByteOrder.LITTLE_ENDIAN);
        float[] vertices = new float[geom.vertexCount() * 3];

        double cos = Math.cos(instance.rotation());
        double sin = Math.sin(instance.rotation());
        Point3D center = instance.worldCenter();

        for (int i = 0; i < geom.vertexCount(); i++) {
            float lx = buffer.getFloat();
            float ly = buffer.getFloat();
            float lz = buffer.getFloat();

            // Apply rotation around Z axis
            float rx = (float)(lx * cos - ly * sin);
            float ry = (float)(lx * sin + ly * cos);
            float rz = lz;

            // Translate to world position
            vertices[i * 3] = (float)(rx + center.x());
            vertices[i * 3 + 1] = (float)(ry + center.y());
            vertices[i * 3 + 2] = (float)(rz + center.z());
        }

        return vertices;
    }

    /**
     * Get faces for an instance (unchanged from local geometry).
     */
    public int[] getFaces(SprinklerInstance instance) throws SQLException {
        ComponentGeometry geom = library.getGeometry(instance.definition().geometryHash());
        if (geom == null) {
            return new int[0];
        }

        ByteBuffer buffer = ByteBuffer.wrap(geom.faces()).order(ByteOrder.LITTLE_ENDIAN);
        int[] faces = new int[geom.faceCount() * 3];

        for (int i = 0; i < geom.faceCount() * 3; i++) {
            faces[i] = buffer.getInt();
        }

        return faces;
    }

    // =========================================================================
    // Data structures
    // =========================================================================

    public enum SprinklerType { PENDANT, UPRIGHT }

    public record SprinklerInstance(
        ComponentDefinition definition,
        Point3D worldCenter,
        double rotation,
        SprinklerType type
    ) {
        public BoundingBox getWorldBounds() {
            BoundingBox local = definition.localBounds();
            return new BoundingBox(
                worldCenter.x() + local.minX(),
                worldCenter.x() + local.maxX(),
                worldCenter.y() + local.minY(),
                worldCenter.y() + local.maxY(),
                worldCenter.z() + local.minZ(),
                worldCenter.z() + local.maxZ()
            );
        }
    }
}
