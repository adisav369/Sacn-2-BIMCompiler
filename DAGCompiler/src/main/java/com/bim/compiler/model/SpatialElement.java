/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.model;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Concrete implementation of ISpatialElement.
 * Populated from elements_meta + elements_rtree tables.
 */
public class SpatialElement implements ISpatialElement {

    private final String guid;
    private final BIMObjectType type;
    private final Discipline discipline;
    private final String name;
    private final String geometryHash;
    private final BoundingBox boundingBox;
    private final Point3D position;

    // Lazy supplier for spatial queries (set by DB reader)
    private Supplier<List<ISpatialElement>> allElementsSupplier;

    public SpatialElement(String guid, BIMObjectType type, Discipline discipline,
                          String name, String geometryHash, BoundingBox boundingBox) {
        this.guid = guid;
        this.type = type;
        this.discipline = discipline;
        this.name = name;
        this.geometryHash = geometryHash;
        this.boundingBox = boundingBox;
        this.position = boundingBox.center();
    }

    // For DB reader to inject the element list for spatial queries
    public void setAllElementsSupplier(Supplier<List<ISpatialElement>> supplier) {
        this.allElementsSupplier = supplier;
    }

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
        return geometryHash != null;
    }

    @Override
    public String getGeometryHash() {
        return geometryHash;
    }

    @Override
    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    @Override
    public Point3D getPosition() {
        return position;
    }

    @Override
    public List<ISpatialElement> findOverlapping() {
        List<ISpatialElement> result = new ArrayList<>();
        if (allElementsSupplier == null) return result;

        for (ISpatialElement other : allElementsSupplier.get()) {
            if (other == this) continue;
            if (boundingBox.overlaps(other.getBoundingBox())) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    public List<ISpatialElement> findOverlapping(BIMObjectType filterType) {
        List<ISpatialElement> result = new ArrayList<>();
        if (allElementsSupplier == null) return result;

        for (ISpatialElement other : allElementsSupplier.get()) {
            if (other == this) continue;
            if (other.getType() != filterType) continue;
            if (boundingBox.overlaps(other.getBoundingBox())) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    public List<ISpatialElement> findOverlapping(Discipline filterDiscipline) {
        List<ISpatialElement> result = new ArrayList<>();
        if (allElementsSupplier == null) return result;

        for (ISpatialElement other : allElementsSupplier.get()) {
            if (other == this) continue;
            if (other.getDiscipline() != filterDiscipline) continue;
            if (boundingBox.overlaps(other.getBoundingBox())) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    public List<ISpatialElement> findContaining() {
        List<ISpatialElement> result = new ArrayList<>();
        if (allElementsSupplier == null) return result;

        for (ISpatialElement other : allElementsSupplier.get()) {
            if (other == this) continue;
            if (other.getBoundingBox().contains(boundingBox)) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    public List<ISpatialElement> findContained() {
        List<ISpatialElement> result = new ArrayList<>();
        if (allElementsSupplier == null) return result;

        for (ISpatialElement other : allElementsSupplier.get()) {
            if (other == this) continue;
            if (boundingBox.contains(other.getBoundingBox())) {
                result.add(other);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format("SpatialElement[%s, %s, %s]", guid, type, discipline);
    }
}
