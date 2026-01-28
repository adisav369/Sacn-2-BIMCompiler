package com.bim.compiler.factory;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

/**
 * Specification for library-based element placement.
 * Used for LOD400 components: sprinklers, lights, columns, fittings.
 */
public class LibraryPlacementSpec extends ElementSpec {

    private String libraryComponentId;  // geometry_hash or component name
    private Point3D position;
    private double rotation;  // radians around Z axis
    private String attachmentFace;  // TOP, BOTTOM, SIDE, CENTER
    private String hostElementId;  // What it attaches to (optional)

    public LibraryPlacementSpec() {
        super();
    }

    public LibraryPlacementSpec(String id, BIMObjectType type, Discipline discipline,
                                 String libraryComponentId, Point3D position, double rotation) {
        super(id, type, discipline);
        this.libraryComponentId = libraryComponentId;
        this.position = position;
        this.rotation = rotation;
    }

    public static Builder builder() { return new Builder(); }

    public String getLibraryComponentId() { return libraryComponentId; }
    public Point3D getPosition() { return position; }
    public double getRotation() { return rotation; }
    public String getAttachmentFace() { return attachmentFace; }
    public String getHostElementId() { return hostElementId; }

    @Override
    public boolean isParametric() { return false; }

    @Override
    public boolean isLibraryBased() { return true; }

    public static class Builder {
        private final LibraryPlacementSpec spec = new LibraryPlacementSpec();

        public Builder id(String id) { spec.id = id; return this; }
        public Builder type(BIMObjectType type) { spec.type = type; return this; }
        public Builder discipline(Discipline discipline) { spec.discipline = discipline; return this; }
        public Builder libraryComponentId(String id) { spec.libraryComponentId = id; return this; }
        public Builder position(Point3D pos) { spec.position = pos; return this; }
        public Builder rotation(double rot) { spec.rotation = rot; return this; }
        public Builder attachmentFace(String face) { spec.attachmentFace = face; return this; }
        public Builder hostElementId(String hostId) { spec.hostElementId = hostId; return this; }

        public LibraryPlacementSpec build() { return spec; }
    }
}
