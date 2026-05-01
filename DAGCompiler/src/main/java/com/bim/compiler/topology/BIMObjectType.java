/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.topology;

/**
 * IFC object types extracted from enhanced_federation_GI.db
 * Query: SELECT DISTINCT ifc_class FROM elements_meta ORDER BY ifc_class;
 *
 * EXTRACTED - DO NOT INVENT ADDITIONAL TYPES
 */
public enum BIMObjectType {
    // MEP - HVAC
    IFC_AIR_TERMINAL,
    IFC_DUCT_FITTING,
    IFC_DUCT_SEGMENT,

    // MEP - Fire Protection
    IFC_FIRE_SUPPRESSION_TERMINAL,

    // MEP - Piping
    IFC_PIPE_FITTING,
    IFC_PIPE_SEGMENT,
    IFC_VALVE,
    IFC_FLOW_CONTROLLER,
    IFC_FLOW_TERMINAL,

    // MEP - Electrical
    IFC_ELECTRIC_APPLIANCE,
    IFC_LIGHT_FIXTURE,
    IFC_ALARM,
    IFC_CONTROLLER,
    IFC_SENSOR,

    // Structure
    IFC_BEAM,
    IFC_COLUMN,
    IFC_SLAB,
    IFC_ROOF,
    IFC_MEMBER,
    IFC_PLATE,
    IFC_REINFORCING_BAR,

    // Architecture
    IFC_WALL,
    IFC_DOOR,
    IFC_WINDOW,
    IFC_COVERING,
    IFC_RAILING,
    IFC_STAIR_FLIGHT,
    IFC_RAMP_FLIGHT,
    IFC_FURNITURE,

    // Generic
    IFC_BUILDING_ELEMENT_PROXY,
    IFC_OPENING_ELEMENT;

    /**
     * Convert from IFC class name string to enum
     * @param ifcClass IFC class name (e.g., "IfcWall")
     * @return corresponding enum value
     */
    public static BIMObjectType fromIfcClass(String ifcClass) {
        if (ifcClass == null) return IFC_BUILDING_ELEMENT_PROXY;

        return switch (ifcClass) {
            case "IfcAirTerminal" -> IFC_AIR_TERMINAL;
            case "IfcAlarm" -> IFC_ALARM;
            case "IfcBeam" -> IFC_BEAM;
            case "IfcBuildingElementProxy" -> IFC_BUILDING_ELEMENT_PROXY;
            case "IfcColumn" -> IFC_COLUMN;
            case "IfcController" -> IFC_CONTROLLER;
            case "IfcCovering" -> IFC_COVERING;
            case "IfcDoor" -> IFC_DOOR;
            case "IfcDuctFitting" -> IFC_DUCT_FITTING;
            case "IfcDuctSegment" -> IFC_DUCT_SEGMENT;
            case "IfcElectricAppliance" -> IFC_ELECTRIC_APPLIANCE;
            case "IfcFireSuppressionTerminal" -> IFC_FIRE_SUPPRESSION_TERMINAL;
            case "IfcFlowController" -> IFC_FLOW_CONTROLLER;
            case "IfcFlowTerminal" -> IFC_FLOW_TERMINAL;
            case "IfcFurniture" -> IFC_FURNITURE;
            case "IfcLightFixture" -> IFC_LIGHT_FIXTURE;
            case "IfcMember" -> IFC_MEMBER;
            case "IfcOpeningElement" -> IFC_OPENING_ELEMENT;
            case "IfcPipeFitting" -> IFC_PIPE_FITTING;
            case "IfcPipeSegment" -> IFC_PIPE_SEGMENT;
            case "IfcPlate" -> IFC_PLATE;
            case "IfcRailing" -> IFC_RAILING;
            case "IfcRampFlight" -> IFC_RAMP_FLIGHT;
            case "IfcReinforcingBar" -> IFC_REINFORCING_BAR;
            case "IfcRoof" -> IFC_ROOF;
            case "IfcSensor" -> IFC_SENSOR;
            case "IfcSlab" -> IFC_SLAB;
            case "IfcStairFlight" -> IFC_STAIR_FLIGHT;
            case "IfcValve" -> IFC_VALVE;
            case "IfcWall" -> IFC_WALL;
            case "IfcWindow" -> IFC_WINDOW;
            default -> IFC_BUILDING_ELEMENT_PROXY;
        };
    }
}
