package com.bim.compiler.contract;

import com.bim.compiler.geometry.BoundingBox;

/**
 * Context provided to IValidatable.validate() for domain-specific validation.
 *
 * <p>Contains building-level information needed for validation rules:
 * <ul>
 *   <li>Building metadata (name, type, construction system)</li>
 *   <li>Spatial context (storeys, envelope)</li>
 *   <li>Shared element registry for boundary checks</li>
 * </ul>
 *
 * <p>This is a builder-style class to allow flexible construction.
 */
public class ValidationContext {

    private String buildingName;
    private String buildingType;
    private String constructionSystem;
    private int storeyCount;
    private BoundingBox envelope;
    private SharedElementRegistry registry;

    /**
     * Creates an empty validation context.
     * Use builder methods to populate.
     */
    public ValidationContext() {
    }

    // Builder methods

    public ValidationContext withBuildingName(String name) {
        this.buildingName = name;
        return this;
    }

    public ValidationContext withBuildingType(String type) {
        this.buildingType = type;
        return this;
    }

    public ValidationContext withConstructionSystem(String system) {
        this.constructionSystem = system;
        return this;
    }

    public ValidationContext withStoreyCount(int count) {
        this.storeyCount = count;
        return this;
    }

    public ValidationContext withEnvelope(BoundingBox envelope) {
        this.envelope = envelope;
        return this;
    }

    public ValidationContext withRegistry(SharedElementRegistry registry) {
        this.registry = registry;
        return this;
    }

    // Getters

    public String buildingName() {
        return buildingName;
    }

    public String buildingType() {
        return buildingType;
    }

    public String constructionSystem() {
        return constructionSystem;
    }

    public int storeyCount() {
        return storeyCount;
    }

    public BoundingBox envelope() {
        return envelope;
    }

    public SharedElementRegistry registry() {
        return registry;
    }

    /**
     * Check if this is a multi-storey building.
     */
    public boolean isMultiStorey() {
        return storeyCount > 1;
    }

    /**
     * Check if this is an institutional building (school, hospital, etc.)
     */
    public boolean isInstitutional() {
        return buildingType != null &&
            (buildingType.contains("SCHOOL") ||
             buildingType.contains("HOSPITAL") ||
             buildingType.contains("OFFICE"));
    }

    /**
     * Check if this is a residential building.
     */
    public boolean isResidential() {
        return buildingType != null &&
            (buildingType.contains("HOUSE") ||
             buildingType.contains("DWELLING") ||
             buildingType.contains("RESIDENTIAL"));
    }
}
