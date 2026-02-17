package com.bim.compiler.factory;

import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

/**
 * Base class for all element specifications.
 *
 * Two main subtypes:
 * - ParametricSpec: dynamic dimensions (walls, pipes, ducts)
 * - LibraryPlacementSpec: fixed LOD400 geometry (sprinklers, lights, columns)
 */
public abstract class ElementSpec {

    protected String id;
    protected BIMObjectType type;
    protected Discipline discipline;

    protected ElementSpec() {}

    protected ElementSpec(String id, BIMObjectType type, Discipline discipline) {
        this.id = id;
        this.type = type;
        this.discipline = discipline;
    }

    public String getId() { return id; }
    public BIMObjectType getType() { return type; }
    public Discipline getDiscipline() { return discipline; }

    /**
     * Check if this spec requires parametric generation.
     */
    public abstract boolean isParametric();

    /**
     * Check if this spec uses library placement.
     */
    public abstract boolean isLibraryBased();
}
