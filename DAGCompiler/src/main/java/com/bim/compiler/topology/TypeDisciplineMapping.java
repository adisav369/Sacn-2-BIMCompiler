/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.topology;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Type-to-Discipline mapping extracted from enhanced_federation_GI.db
 * Query: SELECT discipline, ifc_class, COUNT(*) FROM elements_meta
 *        GROUP BY 1, 2 ORDER BY 1, 3 DESC;
 *
 * NOTE: Some types (IfcPipeFitting, IfcPipeSegment) appear in multiple disciplines.
 * The discipline is determined by the element's discipline field, not just its type.
 *
 * EXTRACTED - DO NOT INVENT ADDITIONAL MAPPINGS
 */
public final class TypeDisciplineMapping {

    private TypeDisciplineMapping() {} // Prevent instantiation

    private static final Map<Discipline, Set<BIMObjectType>> DISCIPLINE_TO_TYPES;
    private static final Map<BIMObjectType, Set<Discipline>> TYPE_TO_DISCIPLINES;

    static {
        DISCIPLINE_TO_TYPES = new EnumMap<>(Discipline.class);
        TYPE_TO_DISCIPLINES = new EnumMap<>(BIMObjectType.class);

        // Initialize all sets
        for (Discipline d : Discipline.values()) {
            DISCIPLINE_TO_TYPES.put(d, EnumSet.noneOf(BIMObjectType.class));
        }
        for (BIMObjectType t : BIMObjectType.values()) {
            TYPE_TO_DISCIPLINES.put(t, EnumSet.noneOf(Discipline.class));
        }

        // ACMV owns: IfcDuctFitting, IfcDuctSegment, IfcAirTerminal
        map(Discipline.ACMV, BIMObjectType.IFC_DUCT_FITTING);
        map(Discipline.ACMV, BIMObjectType.IFC_DUCT_SEGMENT);
        map(Discipline.ACMV, BIMObjectType.IFC_AIR_TERMINAL);

        // ARC owns: IfcPlate, IfcWall, IfcWindow, IfcDoor, IfcFurniture, IfcCovering,
        //           IfcRailing, IfcStairFlight, IfcRampFlight, IfcRoof
        map(Discipline.ARC, BIMObjectType.IFC_PLATE);
        map(Discipline.ARC, BIMObjectType.IFC_WALL);
        map(Discipline.ARC, BIMObjectType.IFC_WINDOW);
        map(Discipline.ARC, BIMObjectType.IFC_DOOR);
        map(Discipline.ARC, BIMObjectType.IFC_FURNITURE);
        map(Discipline.ARC, BIMObjectType.IFC_COVERING);
        map(Discipline.ARC, BIMObjectType.IFC_RAILING);
        map(Discipline.ARC, BIMObjectType.IFC_STAIR_FLIGHT);
        map(Discipline.ARC, BIMObjectType.IFC_RAMP_FLIGHT);
        map(Discipline.ARC, BIMObjectType.IFC_ROOF);
        map(Discipline.ARC, BIMObjectType.IFC_OPENING_ELEMENT);
        map(Discipline.ARC, BIMObjectType.IFC_SLAB); // ARC finish slabs

        // CW owns: IfcPipeFitting, IfcPipeSegment (curtain wall services)
        map(Discipline.CW, BIMObjectType.IFC_PIPE_FITTING);
        map(Discipline.CW, BIMObjectType.IFC_PIPE_SEGMENT);

        // ELEC owns: IfcLightFixture, IfcElectricAppliance, IfcController, IfcSensor
        map(Discipline.ELEC, BIMObjectType.IFC_LIGHT_FIXTURE);
        map(Discipline.ELEC, BIMObjectType.IFC_ELECTRIC_APPLIANCE);
        map(Discipline.ELEC, BIMObjectType.IFC_CONTROLLER);
        map(Discipline.ELEC, BIMObjectType.IFC_SENSOR);

        // FP owns: IfcPipeFitting, IfcPipeSegment, IfcFireSuppressionTerminal, IfcAlarm
        map(Discipline.FP, BIMObjectType.IFC_PIPE_FITTING);
        map(Discipline.FP, BIMObjectType.IFC_PIPE_SEGMENT);
        map(Discipline.FP, BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL);
        map(Discipline.FP, BIMObjectType.IFC_ALARM);
        map(Discipline.FP, BIMObjectType.IFC_VALVE);
        map(Discipline.FP, BIMObjectType.IFC_FLOW_CONTROLLER);

        // LPG owns: IfcPipeFitting, IfcPipeSegment, IfcValve
        map(Discipline.LPG, BIMObjectType.IFC_PIPE_FITTING);
        map(Discipline.LPG, BIMObjectType.IFC_PIPE_SEGMENT);
        map(Discipline.LPG, BIMObjectType.IFC_VALVE);

        // REB owns: IfcReinforcingBar
        map(Discipline.REB, BIMObjectType.IFC_REINFORCING_BAR);

        // SP owns: IfcPipeFitting, IfcPipeSegment, IfcFlowTerminal
        map(Discipline.SP, BIMObjectType.IFC_PIPE_FITTING);
        map(Discipline.SP, BIMObjectType.IFC_PIPE_SEGMENT);
        map(Discipline.SP, BIMObjectType.IFC_FLOW_TERMINAL);

        // STR owns: IfcSlab, IfcBeam, IfcMember, IfcColumn
        map(Discipline.STR, BIMObjectType.IFC_SLAB);
        map(Discipline.STR, BIMObjectType.IFC_BEAM);
        map(Discipline.STR, BIMObjectType.IFC_MEMBER);
        map(Discipline.STR, BIMObjectType.IFC_COLUMN);

        // Generic types can appear in multiple disciplines
        map(Discipline.ARC, BIMObjectType.IFC_BUILDING_ELEMENT_PROXY);
        map(Discipline.STR, BIMObjectType.IFC_BUILDING_ELEMENT_PROXY);
    }

    private static void map(Discipline discipline, BIMObjectType type) {
        DISCIPLINE_TO_TYPES.get(discipline).add(type);
        TYPE_TO_DISCIPLINES.get(type).add(discipline);
    }

    /**
     * Get all types that belong to a discipline.
     * @param discipline the discipline
     * @return unmodifiable set of types
     */
    public static Set<BIMObjectType> getTypesForDiscipline(Discipline discipline) {
        return Set.copyOf(DISCIPLINE_TO_TYPES.get(discipline));
    }

    /**
     * Get all disciplines that use a type.
     * NOTE: Some types (pipes) appear in multiple disciplines.
     * @param type the BIM object type
     * @return unmodifiable set of disciplines
     */
    public static Set<Discipline> getDisciplinesForType(BIMObjectType type) {
        return Set.copyOf(TYPE_TO_DISCIPLINES.get(type));
    }

    /**
     * Check if a type belongs to a discipline.
     * @param discipline the discipline
     * @param type the type
     * @return true if the type can belong to the discipline
     */
    public static boolean isValidCombination(Discipline discipline, BIMObjectType type) {
        return DISCIPLINE_TO_TYPES.get(discipline).contains(type);
    }

    /**
     * Check if a type is shared across multiple disciplines.
     * Shared types: IfcPipeFitting, IfcPipeSegment, IfcValve, IfcSlab
     * @param type the type
     * @return true if type appears in more than one discipline
     */
    public static boolean isSharedType(BIMObjectType type) {
        return TYPE_TO_DISCIPLINES.get(type).size() > 1;
    }
}
