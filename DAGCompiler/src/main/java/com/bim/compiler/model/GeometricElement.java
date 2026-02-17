package com.bim.compiler.model;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

import java.util.List;
import java.util.function.Supplier;

/**
 * Concrete implementation of IGeometricElement.
 * Stores actual vertex and face data from base_geometries table.
 *
 * From FACT 10 and CODE PATTERN 7:
 * - Vertices: float32[3] per vertex (x, y, z) = 12 bytes/vertex
 * - Faces: int32[3] per face (v1, v2, v3 indices) = 12 bytes/face
 */
public class GeometricElement implements IGeometricElement {

    private final String guid;
    private final BIMObjectType type;
    private final Discipline discipline;
    private final String name;
    private final String geometryHash;
    private final BoundingBox boundingBox;
    private final float[] vertices;
    private final int[] faces;
    private final int instanceCount;

    private Supplier<List<ISpatialElement>> allElementsSupplier;

    public GeometricElement(
            String guid,
            BIMObjectType type,
            Discipline discipline,
            String name,
            String geometryHash,
            BoundingBox boundingBox,
            float[] vertices,
            int[] faces,
            int instanceCount
    ) {
        this.guid = guid;
        this.type = type;
        this.discipline = discipline;
        this.name = name;
        this.geometryHash = geometryHash;
        this.boundingBox = boundingBox;
        this.vertices = vertices;
        this.faces = faces;
        this.instanceCount = instanceCount;
    }

    // IBIMElement methods
    @Override
    public String getGuid() {
        return guid;
    }

    @Override
    public BIMObjectType getType() {
        return type;
    }

    @Override
    public Discipline getDiscipline() {
        return discipline;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean hasGeometry() {
        return vertices != null && vertices.length > 0;
    }

    @Override
    public String getGeometryHash() {
        return geometryHash;
    }

    // ISpatialElement methods
    @Override
    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    @Override
    public Point3D getPosition() {
        return boundingBox.center();
    }

    @Override
    public List<ISpatialElement> findOverlapping() {
        if (allElementsSupplier == null) {
            return List.of();
        }
        return allElementsSupplier.get().stream()
            .filter(e -> !e.getGuid().equals(this.guid))
            .filter(e -> this.boundingBox.overlaps(e.getBoundingBox()))
            .toList();
    }

    @Override
    public List<ISpatialElement> findOverlapping(BIMObjectType targetType) {
        if (allElementsSupplier == null) {
            return List.of();
        }
        return allElementsSupplier.get().stream()
            .filter(e -> e.getType() == targetType)
            .filter(e -> !e.getGuid().equals(this.guid))
            .filter(e -> this.boundingBox.overlaps(e.getBoundingBox()))
            .toList();
    }

    @Override
    public List<ISpatialElement> findOverlapping(Discipline targetDiscipline) {
        if (allElementsSupplier == null) {
            return List.of();
        }
        return allElementsSupplier.get().stream()
            .filter(e -> e.getDiscipline() == targetDiscipline)
            .filter(e -> !e.getGuid().equals(this.guid))
            .filter(e -> this.boundingBox.overlaps(e.getBoundingBox()))
            .toList();
    }

    @Override
    public List<ISpatialElement> findContaining() {
        if (allElementsSupplier == null) {
            return List.of();
        }
        return allElementsSupplier.get().stream()
            .filter(e -> !e.getGuid().equals(this.guid))
            .filter(e -> e.getBoundingBox().contains(this.boundingBox))
            .toList();
    }

    @Override
    public List<ISpatialElement> findContained() {
        if (allElementsSupplier == null) {
            return List.of();
        }
        return allElementsSupplier.get().stream()
            .filter(e -> !e.getGuid().equals(this.guid))
            .filter(e -> this.boundingBox.contains(e.getBoundingBox()))
            .toList();
    }

    // IGeometricElement methods
    @Override
    public int getVertexCount() {
        return vertices != null ? vertices.length / 3 : 0;
    }

    @Override
    public int getFaceCount() {
        return faces != null ? faces.length / 3 : 0;
    }

    @Override
    public float[] getVertices() {
        return vertices;
    }

    @Override
    public int[] getFaces() {
        return faces;
    }

    @Override
    public boolean isInstanced() {
        return instanceCount > 1;
    }

    @Override
    public int getInstanceCount() {
        return instanceCount;
    }

    /**
     * Set the supplier for all elements (used for spatial queries).
     */
    public void setAllElementsSupplier(Supplier<List<ISpatialElement>> supplier) {
        this.allElementsSupplier = supplier;
    }

    /**
     * Compute bounding box from vertices (for verification).
     */
    public BoundingBox computeBBoxFromVertices() {
        if (vertices == null || vertices.length < 3) {
            return null;
        }

        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;

        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        return new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);
    }

    @Override
    public String toString() {
        return String.format("GeometricElement[guid=%s, type=%s, vertices=%d, faces=%d]",
            guid, type, getVertexCount(), getFaceCount());
    }
}
