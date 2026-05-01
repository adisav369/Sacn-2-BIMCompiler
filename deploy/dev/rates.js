/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// rates.js — Single source of truth for CIDB 2024 cost data
// Used by: boq_charts.html, variation_order.js, nlp.js
// Do NOT duplicate these constants — import this file instead.

// ============================================================================
// CIDB 2024 MATERIAL RATES (from boq_export.py — exact match)
// ============================================================================
var RATES = {
  IfcDuct:{rate:165,unit:'M',desc:'Galvanized Steel Ductwork (avg 400mm)'},
  IfcDuctSegment:{rate:165,unit:'M',desc:'Ductwork Segment'},
  IfcDuctFitting:{rate:380,unit:'EA',desc:'Duct Fittings (elbows, tees)'},
  IfcPipe:{rate:48.5,unit:'M',desc:'PVC/HDPE Pipe (avg 100mm)'},
  IfcPipeSegment:{rate:48.5,unit:'M',desc:'Pipe Segment'},
  IfcPipeFitting:{rate:95,unit:'EA',desc:'Pipe Fittings'},
  IfcCableCarrier:{rate:78,unit:'M',desc:'Cable Tray System (300mm)'},
  IfcCableCarrierSegment:{rate:78,unit:'M',desc:'Cable Tray Segment'},
  IfcBeam:{rate:680,unit:'M',desc:'Structural Steel I-Beam'},
  IfcColumn:{rate:1250,unit:'M',desc:'Structural Steel Column'},
  IfcSlab:{rate:285,unit:'M2',desc:'RC Slab 250mm'},
  IfcWall:{rate:145,unit:'M2',desc:'Blockwork Wall 150mm'},
  IfcWallStandardCase:{rate:145,unit:'M2',desc:'Standard Wall'},
  IfcCurtainWall:{rate:750,unit:'M2',desc:'Curtain Wall'},
  IfcCovering:{rate:185,unit:'M2',desc:'Floor/Ceiling Finish'},
  IfcRoof:{rate:238,unit:'M2',desc:'Metal Roof'},
  IfcLightFixture:{rate:485,unit:'EA',desc:'LED Light Fixture'},
  IfcOutlet:{rate:125,unit:'EA',desc:'Power Outlet'},
  IfcDoor:{rate:2850,unit:'EA',desc:'Door Set'},
  IfcWindow:{rate:1580,unit:'EA',desc:'Window'},
  IfcBuildingElementProxy:{rate:850,unit:'EA',desc:'Misc Element'},
  IfcFlowTerminal:{rate:3500,unit:'EA',desc:'HVAC Terminal'},
  IfcFurnishingElement:{rate:1200,unit:'EA',desc:'Furniture'},
  IfcPlate:{rate:95,unit:'M2',desc:'Steel Plate'},
  IfcMember:{rate:320,unit:'M',desc:'Steel Member'},
  IfcRailing:{rate:280,unit:'M',desc:'Railing'},
  IfcStair:{rate:4500,unit:'EA',desc:'Staircase'},
  IfcStairFlight:{rate:2200,unit:'EA',desc:'Stair Flight'},
  IfcFooting:{rate:320,unit:'EA',desc:'Foundation Footing'},
  IfcPile:{rate:850,unit:'EA',desc:'Foundation Pile'},
  IfcReinforcingBar:{rate:45,unit:'KG',desc:'Reinforcing Steel'},
  IfcFlowSegment:{rate:120,unit:'M',desc:'Flow Segment'},
  IfcFlowFitting:{rate:200,unit:'EA',desc:'Flow Fitting'},
  IfcFlowController:{rate:450,unit:'EA',desc:'Flow Controller'},
  IfcEnergyConversionDevice:{rate:8500,unit:'EA',desc:'Energy Conversion Device'},
  IfcFlowTreatmentDevice:{rate:1200,unit:'EA',desc:'Flow Treatment Device'},
  IfcFlowMovingDevice:{rate:3500,unit:'EA',desc:'Flow Moving Device'},
  IfcFlowStorageDevice:{rate:5000,unit:'EA',desc:'Flow Storage Device'},
  IfcElectricAppliance:{rate:485,unit:'EA',desc:'Electric Appliance'},
  IfcFurniture:{rate:1500,unit:'EA',desc:'Furniture'},
  IfcOpeningElement:{rate:0,unit:'EA',desc:'Opening (void)'},
  IfcBuildingElementPart:{rate:0,unit:'EA',desc:'Building Element Part'},
};
var RATES_DEFAULT = {rate:500,unit:'EA',desc:'Misc Element'};

// Helper: get rate value for an IFC class
function getRate(ifcClass) {
  var r = RATES[ifcClass];
  return r ? r.rate : RATES_DEFAULT.rate;
}

// ============================================================================
// LABOR RATES (from boq_export.py — full trade/crew/rate_per_day)
// ============================================================================
var LABOR_RATES = {
  HVAC_TECH: {
    rate_per_day: 185, crew_size: 2, trade: 'HVAC Technician (Skilled)',
    productivity: {IfcDuct:18,IfcDuctSegment:18,IfcDuctFitting:12}
  },
  PLUMBER: {
    rate_per_day: 165, crew_size: 2, trade: 'Pipefitter (Skilled)',
    productivity: {IfcPipe:25,IfcPipeSegment:25,IfcPipeFitting:15}
  },
  ELECTRICIAN: {
    rate_per_day: 175, crew_size: 2, trade: 'Electrician (Skilled)',
    productivity: {IfcCableCarrier:30,IfcCableCarrierSegment:30,IfcLightFixture:20,IfcOutlet:25}
  },
  STEEL_ERECTOR: {
    rate_per_day: 195, crew_size: 4, trade: 'Steel Erector (Skilled)',
    productivity: {IfcBeam:8,IfcColumn:6}
  },
  CONCRETE_GANG: {
    rate_per_day: 145, crew_size: 6, trade: 'Concrete Gang (Mixed)',
    productivity: {IfcSlab:35}
  },
  MASON: {
    rate_per_day: 155, crew_size: 3, trade: 'Mason (Skilled) + Laborers',
    productivity: {IfcWall:12,IfcWallStandardCase:12}
  },
  LABORER: {
    rate_per_day: 95, crew_size: 1, trade: 'General Laborer',
    productivity: {}
  },
};

// ============================================================================
// EQUIPMENT RATES & ALLOCATION (from boq_export.py)
// ============================================================================
var EQUIPMENT_RATES = {
  MOBILE_CRANE_20T: {rate_per_day:1850, desc:'Mobile Crane 20 Tonne'},
  TOWER_CRANE: {rate_per_day:2200, desc:'Tower Crane'},
  CONCRETE_PUMP: {rate_per_day:950, desc:'Concrete Pump Truck'},
  SCISSOR_LIFT_8M: {rate_per_day:285, desc:'Scissor Lift 8m'},
  WELDING_MACHINE: {rate_per_day:65, desc:'Welding Machine 300A'},
  GENERATOR_5KVA: {rate_per_day:95, desc:'Generator 5KVA'},
};
var EQUIPMENT_ALLOCATION = {
  IfcBeam: {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
  IfcColumn: {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
  IfcSlab: {equipment:'CONCRETE_PUMP', duration_factor:0.3},
  IfcDuct: {equipment:'SCISSOR_LIFT_8M', duration_factor:0.4},
  IfcCableCarrier: {equipment:'SCISSOR_LIFT_8M', duration_factor:0.3},
};

// ============================================================================
// SEQUENCE RULES (from schedule_generator.py — for 4D schedule)
// ============================================================================
var SEQUENCE_RULES = {
  // Substructure
  IfcFooting:{phase:'Substructure',sequence:1,resource:'CONCRETE_GANG'},
  IfcReinforcingBar:{phase:'Substructure',sequence:1,resource:'CONCRETE_GANG'},
  // Superstructure
  IfcColumn:{phase:'Superstructure',sequence:2,resource:'STEEL_ERECTOR'},
  IfcBeam:{phase:'Superstructure',sequence:3,resource:'STEEL_ERECTOR'},
  IfcSlab:{phase:'Superstructure',sequence:4,resource:'CONCRETE_GANG'},
  IfcPlate:{phase:'Superstructure',sequence:4,resource:'STEEL_ERECTOR'},
  IfcMember:{phase:'Superstructure',sequence:3,resource:'STEEL_ERECTOR'},
  // MEP Rough-in
  IfcDuct:{phase:'MEP Rough-in',sequence:5,resource:'HVAC_TECH'},
  IfcDuctSegment:{phase:'MEP Rough-in',sequence:5,resource:'HVAC_TECH'},
  IfcDuctFitting:{phase:'MEP Rough-in',sequence:5,resource:'HVAC_TECH'},
  IfcPipe:{phase:'MEP Rough-in',sequence:5,resource:'PLUMBER'},
  IfcPipeSegment:{phase:'MEP Rough-in',sequence:5,resource:'PLUMBER'},
  IfcPipeFitting:{phase:'MEP Rough-in',sequence:5,resource:'PLUMBER'},
  IfcCableCarrier:{phase:'MEP Rough-in',sequence:5,resource:'ELECTRICIAN'},
  IfcCableCarrierSegment:{phase:'MEP Rough-in',sequence:5,resource:'ELECTRICIAN'},
  IfcFlowSegment:{phase:'MEP Rough-in',sequence:5,resource:'PLUMBER'},
  IfcFlowFitting:{phase:'MEP Rough-in',sequence:5,resource:'PLUMBER'},
  IfcFlowController:{phase:'MEP Rough-in',sequence:5,resource:'ELECTRICIAN'},
  IfcFlowMovingDevice:{phase:'MEP Rough-in',sequence:5,resource:'HVAC_TECH'},
  IfcFlowStorageDevice:{phase:'MEP Rough-in',sequence:5,resource:'PLUMBER'},
  IfcFlowTreatmentDevice:{phase:'MEP Rough-in',sequence:5,resource:'PLUMBER'},
  IfcEnergyConversionDevice:{phase:'MEP Rough-in',sequence:5,resource:'HVAC_TECH'},
  // Architecture
  IfcWall:{phase:'Architecture',sequence:6,resource:'MASON'},
  IfcWallStandardCase:{phase:'Architecture',sequence:6,resource:'MASON'},
  IfcOpeningElement:{phase:'Architecture',sequence:6,resource:'MASON'},
  IfcBuildingElementPart:{phase:'Architecture',sequence:6,resource:'MASON'},
  IfcDoor:{phase:'Architecture',sequence:7,resource:'CARPENTER'},
  IfcWindow:{phase:'Architecture',sequence:7,resource:'CARPENTER'},
  IfcStair:{phase:'Architecture',sequence:7,resource:'CARPENTER'},
  IfcStairFlight:{phase:'Architecture',sequence:7,resource:'CARPENTER'},
  IfcRailing:{phase:'Architecture',sequence:7,resource:'CARPENTER'},
  IfcRoof:{phase:'Architecture',sequence:8,resource:'ROOFER'},
  IfcBuildingElementProxy:{phase:'Architecture',sequence:6,resource:null},
  IfcCurtainWall:{phase:'Architecture',sequence:7,resource:'CARPENTER'},
  // MEP Final
  IfcLightFixture:{phase:'MEP Final',sequence:9,resource:'ELECTRICIAN'},
  IfcOutlet:{phase:'MEP Final',sequence:9,resource:'ELECTRICIAN'},
  IfcElectricAppliance:{phase:'MEP Final',sequence:9,resource:'ELECTRICIAN'},
  IfcFlowTerminal:{phase:'MEP Final',sequence:9,resource:'HVAC_TECH'},
  // Finishes
  IfcCovering:{phase:'Finishes',sequence:10,resource:'FINISHER'},
  IfcFurniture:{phase:'Finishes',sequence:11,resource:'FINISHER'},
  IfcFurnishingElement:{phase:'Finishes',sequence:11,resource:'FINISHER'},
};
var SEQUENCE_DEFAULT = {phase:'Architecture',sequence:6,resource:null};

// Helper: get phase for an IFC class
function getPhase(ifcClass) {
  var r = SEQUENCE_RULES[ifcClass];
  return r ? r.phase : SEQUENCE_DEFAULT.phase;
}

// Helper: get productivity (elements/day) for an IFC class
function getProductivity(ifcClass) {
  // Derive from LABOR_RATES productivity maps
  for (var key in LABOR_RATES) {
    var lr = LABOR_RATES[key];
    if (lr.productivity && lr.productivity[ifcClass] !== undefined) {
      return lr.productivity[ifcClass];
    }
  }
  return 10; // default
}

// ============================================================================
// WORK PACKAGES — IFC class → construction phase mapping
// ============================================================================
var WORK_PACKAGES = [
  { id: 'PACKAGE 1', name: 'SUBSTRUCTURE', color: '8E44AD',
    classes: ['IfcFooting','IfcPile','IfcReinforcingBar'] },
  { id: 'PACKAGE 2', name: 'SUPERSTRUCTURE', color: '2980B9',
    classes: ['IfcColumn','IfcBeam','IfcSlab','IfcWall','IfcWallStandardCase','IfcCurtainWall','IfcRoof','IfcPlate','IfcMember'] },
  { id: 'PACKAGE 3', name: 'MEP ROUGH-IN', color: 'D35400',
    classes: ['IfcDuct','IfcDuctSegment','IfcDuctFitting','IfcPipe','IfcPipeSegment','IfcPipeFitting','IfcCableCarrier','IfcCableCarrierSegment','IfcFlowSegment','IfcFlowFitting','IfcFlowController','IfcFlowMovingDevice','IfcFlowStorageDevice','IfcFlowTreatmentDevice','IfcEnergyConversionDevice'] },
  { id: 'PACKAGE 4', name: 'FINISHES', color: '27AE60',
    classes: ['IfcCovering','IfcDoor','IfcWindow','IfcFurniture','IfcFurnishingElement','IfcStair','IfcStairFlight','IfcRailing'] },
  { id: 'PACKAGE 5', name: 'MEP FINAL FIX', color: 'C0392B',
    classes: ['IfcFlowTerminal','IfcLightFixture','IfcOutlet','IfcElectricAppliance'] },
];

// ============================================================================
// DISCIPLINE + PHASE COLORS
// ============================================================================
var DISC_COLORS = {
  ARC:'#4488ff',STR:'#44cccc',MEP:'#44cc44',ELEC:'#cccc44',FP:'#cc8844',
  ACMV:'#cc4444',PLB:'#8844cc',HVAC:'#44aacc',SAN:'#aa44aa',VENT:'#88ccaa',
};
var PHASE_COLORS = {
  'Substructure':'#A5A5A5','Superstructure':'#4472C4','MEP Rough-in':'#70AD47',
  'Architecture':'#ED7D31','MEP Final':'#5B9BD5','Finishes':'#FFC000',
  'Commissioning':'#C55A11','Unknown':'#888888',
};

// ============================================================================
// COST CALCULATION FUNCTIONS
// ============================================================================
function calcLabor(ifcClass, qty) {
  for (var key in LABOR_RATES) {
    var lr = LABOR_RATES[key];
    if (lr.productivity && lr.productivity[ifcClass] !== undefined) {
      var prod = lr.productivity[ifcClass];
      var days = qty / prod;
      var cost = days * lr.crew_size * lr.rate_per_day;
      return {cost: Math.round(cost), days: days, crew: lr.crew_size, trade: lr.trade, tradeKey: key, prod: prod};
    }
  }
  return {cost: 0, days: 0, crew: 0, trade: '', tradeKey: null, prod: 10};
}

function calcEquipment(ifcClass, laborDays) {
  var alloc = EQUIPMENT_ALLOCATION[ifcClass];
  if (!alloc) return {cost: 0, desc: '', days: 0};
  var er = EQUIPMENT_RATES[alloc.equipment];
  var days = laborDays * alloc.duration_factor;
  return {cost: Math.round(days * er.rate_per_day), desc: er.desc, days: days};
}
