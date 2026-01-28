package com.bim.compiler.factory;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.compiler.library.ComponentLibrary.*;
import com.bim.compiler.library.SprinklerPlacer;
import com.bim.compiler.library.SprinklerPlacer.SprinklerInstance;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.model.SpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.util.BIMLogger;

import java.sql.SQLException;
import java.util.*;

/**
 * Factory for library-based element placement.
 * Uses LOD400 component geometries from the component library.
 */
public class LibraryFactory implements IElementFactory<LibraryPlacementSpec> {

    private final ComponentLibrary library;
    private final SprinklerPlacer sprinklerPlacer;

    public LibraryFactory(ComponentLibrary library) throws SQLException {
        this.library = library;
        this.sprinklerPlacer = new SprinklerPlacer(library);
    }

    @Override
    public ISpatialElement create(LibraryPlacementSpec spec) {
        BIMLogger.debug("LibraryFactory",
            "Creating {} at {} rotation={}°",
            spec.getLibraryComponentId(),
            spec.getPosition(),
            Math.toDegrees(spec.getRotation()));

        try {
            // Route to appropriate placer based on type
            if (spec.getType() == BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL) {
                return createSprinkler(spec);
            }

            // Generic library placement for other types
            return createGeneric(spec);

        } catch (SQLException e) {
            BIMLogger.error("LibraryFactory", "Failed to create: {}", e.getMessage());
            throw new RuntimeException("Library placement failed", e);
        }
    }

    private ISpatialElement createSprinkler(LibraryPlacementSpec spec) throws SQLException {
        // Determine pendant vs upright
        String componentId = spec.getLibraryComponentId();
        boolean isPendant = componentId == null ||
                           componentId.toLowerCase().contains("pendant") ||
                           componentId.toLowerCase().contains("pendent") ||
                           "TOP".equals(spec.getAttachmentFace());

        SprinklerInstance instance;
        if (isPendant) {
            instance = sprinklerPlacer.placePendant(spec.getPosition(), spec.getRotation());
        } else {
            instance = sprinklerPlacer.placeUpright(spec.getPosition(), spec.getRotation());
        }

        BIMLogger.placement("Sprinkler",
            spec.getId(),
            instance.worldCenter(),
            spec.getRotation());

        // Convert to ISpatialElement
        return new LibraryElement(
            spec.getId(),
            spec.getType(),
            spec.getDiscipline(),
            instance.worldCenter(),
            instance.getWorldBounds(),
            instance.definition().geometryHash(),
            spec.getRotation()
        );
    }

    private ISpatialElement createGeneric(LibraryPlacementSpec spec) throws SQLException {
        // Get component definition by ID or name
        ComponentDefinition def = library.getByName(spec.getLibraryComponentId());
        if (def == null) {
            throw new IllegalArgumentException("Component not found: " + spec.getLibraryComponentId());
        }

        BIMLogger.debug("LibraryFactory",
            "Generic placement: {} origin={} attachment={}",
            def.name(), def.localBounds().center(), def.attachmentFace());

        // Calculate world position based on attachment face
        Point3D worldCenter = calculateWorldCenter(spec.getPosition(), def);

        // Calculate world bounds
        BoundingBox worldBounds = new BoundingBox(
            worldCenter.x() + def.localBounds().minX(),
            worldCenter.x() + def.localBounds().maxX(),
            worldCenter.y() + def.localBounds().minY(),
            worldCenter.y() + def.localBounds().maxY(),
            worldCenter.z() + def.localBounds().minZ(),
            worldCenter.z() + def.localBounds().maxZ()
        );

        BIMLogger.placement(def.name(), spec.getId(), worldCenter, spec.getRotation());

        return new LibraryElement(
            spec.getId(),
            spec.getType(),
            spec.getDiscipline(),
            worldCenter,
            worldBounds,
            def.geometryHash(),
            spec.getRotation()
        );
    }

    private Point3D calculateWorldCenter(Point3D attachmentPoint, ComponentDefinition def) {
        BoundingBox local = def.localBounds();
        double halfHeight = local.height() / 2;

        return switch (def.attachmentFace()) {
            case TOP -> new Point3D(
                attachmentPoint.x(),
                attachmentPoint.y(),
                attachmentPoint.z() - halfHeight - local.maxZ()
            );
            case BOTTOM -> new Point3D(
                attachmentPoint.x(),
                attachmentPoint.y(),
                attachmentPoint.z() + halfHeight - local.minZ()
            );
            case CENTER -> attachmentPoint;
            default -> attachmentPoint;
        };
    }

    /**
     * Create multiple elements from a grid placement spec.
     */
    public List<ISpatialElement> createGrid(GridPlacementSpec spec) throws SQLException {
        List<ISpatialElement> elements = new ArrayList<>();

        BIMLogger.debug("LibraryFactory",
            "Grid placement: area={} spacing={}m",
            spec.getArea(), spec.getSpacing());

        // For sprinklers, use the SprinklerPlacer's grid method
        if (spec.getType() == BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL) {
            List<SprinklerInstance> instances = sprinklerPlacer.placeGrid(
                spec.getArea(),
                spec.getAttachmentZ(),
                spec.getSpacing()
            );

            int index = 0;
            for (SprinklerInstance inst : instances) {
                String id = spec.getId() + "_" + (++index);
                elements.add(new LibraryElement(
                    id,
                    spec.getType(),
                    spec.getDiscipline(),
                    inst.worldCenter(),
                    inst.getWorldBounds(),
                    inst.definition().geometryHash(),
                    0.0  // No rotation for grid
                ));
            }

            BIMLogger.info("LibraryFactory",
                "Grid created: {} sprinklers", elements.size());
        }

        return elements;
    }

    /**
     * Create a scaled library element.
     * Used for stairs where target dimensions may differ from library component.
     *
     * @param id Element ID
     * @param def Component definition
     * @param position World position
     * @param rotation Rotation in radians
     * @param scaleX X scale factor
     * @param scaleY Y scale factor
     * @param scaleZ Z scale factor
     */
    public ISpatialElement createScaled(String id, ComponentDefinition def,
                                        Point3D position, double rotation,
                                        double scaleX, double scaleY, double scaleZ) {

        BIMLogger.debug("LibraryFactory",
            "Scaled placement: {} at {} scale=({:.2f}, {:.2f}, {:.2f})",
            def.name(), position, scaleX, scaleY, scaleZ);

        // Calculate scaled bounds
        BoundingBox local = def.localBounds();
        double scaledWidth = local.width() * scaleX;
        double scaledDepth = local.depth() * scaleY;
        double scaledHeight = local.height() * scaleZ;

        // World bounds centered on position
        BoundingBox worldBounds = new BoundingBox(
            position.x() - scaledWidth / 2,
            position.x() + scaledWidth / 2,
            position.y() - scaledDepth / 2,
            position.y() + scaledDepth / 2,
            position.z(),
            position.z() + scaledHeight
        );

        BIMLogger.placement(def.name(), id, position, rotation);

        return new ScaledLibraryElement(
            id,
            BIMObjectType.IFC_STAIR_FLIGHT,  // Could be parameterized
            com.bim.compiler.topology.Discipline.ARC,
            position,
            worldBounds,
            def.geometryHash(),
            rotation,
            scaleX, scaleY, scaleZ
        );
    }

    /**
     * Element class for scaled library components.
     */
    public static class ScaledLibraryElement extends LibraryElement {
        private final double scaleX, scaleY, scaleZ;

        public ScaledLibraryElement(String guid, BIMObjectType type,
                                    com.bim.compiler.topology.Discipline discipline,
                                    Point3D position, BoundingBox bounds,
                                    String geometryHash, double rotation,
                                    double scaleX, double scaleY, double scaleZ) {
            super(guid, type, discipline, position, bounds, geometryHash, rotation);
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }

        public double getScaleX() { return scaleX; }
        public double getScaleY() { return scaleY; }
        public double getScaleZ() { return scaleZ; }

        public boolean isScaled() {
            return scaleX != 1.0 || scaleY != 1.0 || scaleZ != 1.0;
        }
    }

    @Override
    public boolean canHandle(LibraryPlacementSpec spec) {
        try {
            if (spec.getLibraryComponentId() != null) {
                return library.getByName(spec.getLibraryComponentId()) != null;
            }
            // Sprinklers can always be handled
            if (spec.getType() == BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL) {
                return sprinklerPlacer != null;
            }
            return false;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String getFactoryType() {
        return "LIBRARY";
    }

    /**
     * Internal element class for library-placed elements.
     */
    public static class LibraryElement extends SpatialElement {
        private final double rotation;

        public LibraryElement(String guid, BIMObjectType type,
                              com.bim.compiler.topology.Discipline discipline,
                              Point3D position, BoundingBox bounds,
                              String geometryHash, double rotation) {
            super(guid, type, discipline, guid, geometryHash, bounds);
            this.rotation = rotation;
        }

        public double getRotation() { return rotation; }

        @Override
        public Point3D getPosition() {
            return getBoundingBox().center();
        }
    }
}
