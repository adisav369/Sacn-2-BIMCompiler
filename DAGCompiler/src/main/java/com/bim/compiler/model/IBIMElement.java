package com.bim.compiler.model;

import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

/**
 * Level 1: Identity - "What is it?"
 * Maps to elements_meta table in database.
 *
 * This is the base interface for all BIM elements.
 * Every element has identity regardless of whether it has geometry.
 *
 * From SESSION_STATE.md: 51,723 total elements, 94.8% have geometry.
 */
public interface IBIMElement {

    /**
     * Globally unique identifier.
     * From elements_meta.guid (IFC GlobalId format).
     * @return the element GUID (22-character base64)
     */
    String getGuid();

    /**
     * IFC class type.
     * From elements_meta.ifc_class.
     * @return the object type (one of 31 extracted types)
     */
    BIMObjectType getType();

    /**
     * Construction discipline.
     * From elements_meta.discipline.
     * @return the discipline (one of 9 extracted disciplines)
     */
    Discipline getDiscipline();

    /**
     * Element name/description.
     * From elements_meta.name.
     * @return the element name, may be null
     */
    String getName();

    /**
     * Check if this element has geometry.
     * From SESSION_STATE.md: 94.8% have geometry, 5.2% do not.
     * @return true if geometry is available
     */
    boolean hasGeometry();

    /**
     * Get the geometry hash for instancing.
     * From elements_meta.geom_hash.
     * Multiple elements may share the same geometry (2.14x reuse ratio).
     * @return geometry hash, or null if no geometry
     */
    String getGeometryHash();
}
