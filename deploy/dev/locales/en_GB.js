// en_GB.js — English (United Kingdom) — UK locale
// ISO 3166-1: GB — Flag: 🇬🇧
// To create your project locale: copy this file → MyProject_TRL.js → edit what differs
//
// _TRL_LOCALE overrides _TRL_DEFAULTS at runtime.
// Every key is shown here so you can see and edit the full set.

var _TRL_LOCALE = {

  // ── Identity ──
  iso: 'GB',            // ISO 3166-1 alpha-2 (drives flag emoji)
  lang: 'en',           // ISO 639-1 language
  locale: 'en_GB',      // combined
  source_app: 'BIM OOTB — Frictionless BIM',

  // ── Currency ──
  cur: '£',
  cur2: 'USD',
  cur_rate: 0.79,         // 1 USD = 0.79 GBP
  cur_name: 'British Pound Sterling',
  cur2_name: 'US Dollar',

  // ── Rate source attribution ──
  rate_source:       'BCIS / Spon\'s 2024',
  rate_year:         '2024',
  rate_mat_source:   'BCIS Online — Material Cost Index 2024',
  rate_mat_ref:      'Spon\'s Architects\' and Builders\' Price Book 2024',
  rate_mat_includes: 'Delivery, wastage allowance (5-10% by material type)',
  rate_lab_source:   'BCIS Labour Cost Index 2024',
  rate_lab_basis:    'Basic wage + 35% (NI 13.8%, pension 5%, CITB levy, benefits)',
  rate_lab_prod:     'Spon\'s output standards by trade',
  rate_lab_crew:     'Skilled operatives + labourers as per trade standards',
  rate_eq_source:    'Spon\'s External Works and Landscape Price Book 2024',
  rate_eq_alloc:     'Based on work type and duration requirements',
  rate_eq_basis:     'Per day (8 hours), operator cost included where stated',

  // ── Material Rates (BCIS / Spon's 2024, GBP) ──
  rates: {
    IfcDuct:{rate:58,unit:'M',desc:'Galvanised Steel Ductwork (avg 400mm)'},
    IfcDuctSegment:{rate:58,unit:'M',desc:'Ductwork Segment'},
    IfcDuctFitting:{rate:135,unit:'EA',desc:'Duct Fittings (elbows, tees)'},
    IfcPipe:{rate:18,unit:'M',desc:'PVC/HDPE Pipe (avg 100mm)'},
    IfcPipeSegment:{rate:18,unit:'M',desc:'Pipe Segment'},
    IfcPipeFitting:{rate:35,unit:'EA',desc:'Pipe Fittings'},
    IfcCableCarrier:{rate:28,unit:'M',desc:'Cable Tray System (300mm)'},
    IfcCableCarrierSegment:{rate:28,unit:'M',desc:'Cable Tray Segment'},
    IfcBeam:{rate:240,unit:'M',desc:'Structural Steel I-Beam'},
    IfcColumn:{rate:450,unit:'M',desc:'Structural Steel Column'},
    IfcSlab:{rate:105,unit:'M2',desc:'RC Slab 250mm'},
    IfcWall:{rate:55,unit:'M2',desc:'Blockwork Wall 150mm'},
    IfcWallStandardCase:{rate:55,unit:'M2',desc:'Standard Wall'},
    IfcCurtainWall:{rate:285,unit:'M2',desc:'Curtain Wall'},
    IfcCovering:{rate:68,unit:'M2',desc:'Floor/Ceiling Finish'},
    IfcRoof:{rate:88,unit:'M2',desc:'Metal Roof'},
    IfcLightFixture:{rate:175,unit:'EA',desc:'LED Light Fixture'},
    IfcOutlet:{rate:45,unit:'EA',desc:'Power Outlet'},
    IfcDoor:{rate:1050,unit:'EA',desc:'Door Set'},
    IfcWindow:{rate:580,unit:'EA',desc:'Window'},
    IfcBuildingElementProxy:{rate:310,unit:'EA',desc:'Misc Element'},
    IfcFlowTerminal:{rate:1250,unit:'EA',desc:'HVAC Terminal'},
    IfcFurnishingElement:{rate:450,unit:'EA',desc:'Furniture'},
    IfcFurniture:{rate:550,unit:'EA',desc:'Furniture'},
    IfcPlate:{rate:35,unit:'M2',desc:'Steel Plate'},
    IfcMember:{rate:115,unit:'M',desc:'Steel Member'},
    IfcRailing:{rate:105,unit:'M',desc:'Railing'},
    IfcStair:{rate:1650,unit:'EA',desc:'Staircase'},
    IfcStairFlight:{rate:800,unit:'EA',desc:'Stair Flight'},
    IfcFooting:{rate:120,unit:'EA',desc:'Foundation Footing'},
    IfcPile:{rate:310,unit:'EA',desc:'Foundation Pile'},
    IfcReinforcingBar:{rate:1.65,unit:'KG',desc:'Reinforcing Steel'},
    IfcFlowSegment:{rate:44,unit:'M',desc:'Flow Segment'},
    IfcFlowFitting:{rate:72,unit:'EA',desc:'Flow Fitting'},
    IfcFlowController:{rate:165,unit:'EA',desc:'Flow Controller'},
    IfcEnergyConversionDevice:{rate:3100,unit:'EA',desc:'Energy Conversion Device'},
    IfcFlowTreatmentDevice:{rate:440,unit:'EA',desc:'Flow Treatment Device'},
    IfcFlowMovingDevice:{rate:1280,unit:'EA',desc:'Flow Moving Device'},
    IfcFlowStorageDevice:{rate:1850,unit:'EA',desc:'Flow Storage Device'},
    IfcElectricAppliance:{rate:175,unit:'EA',desc:'Electric Appliance'},
  },
  rates_default: {rate:185,unit:'EA',desc:'Misc Element'},

  // ── Labour Rates (BCIS Labour Cost Index 2024, GBP) ──
  labor_rates: {
    HVAC_TECH:     {rate_per_day:280, crew_size:2, trade:'HVAC Technician (Skilled)',
                    productivity:{IfcDuct:16,IfcDuctSegment:16,IfcDuctFitting:10}},
    PLUMBER:       {rate_per_day:260, crew_size:2, trade:'Plumber (Skilled)',
                    productivity:{IfcPipe:22,IfcPipeSegment:22,IfcPipeFitting:14}},
    ELECTRICIAN:   {rate_per_day:270, crew_size:2, trade:'Electrician (Skilled)',
                    productivity:{IfcCableCarrier:28,IfcCableCarrierSegment:28,IfcLightFixture:18,IfcOutlet:22}},
    STEEL_ERECTOR: {rate_per_day:290, crew_size:4, trade:'Steel Erector (Skilled)',
                    productivity:{IfcBeam:7,IfcColumn:5}},
    CONCRETE_GANG: {rate_per_day:220, crew_size:6, trade:'Concrete Gang (Mixed)',
                    productivity:{IfcSlab:32}},
    MASON:         {rate_per_day:240, crew_size:3, trade:'Bricklayer (Skilled) + Labourers',
                    productivity:{IfcWall:10,IfcWallStandardCase:10}},
    LABORER:       {rate_per_day:150, crew_size:1, trade:'General Labourer',
                    productivity:{}},
  },

  // ── Equipment Rates (Spon's 2024, GBP) ──
  equipment_rates: {
    MOBILE_CRANE_20T: {rate_per_day:650, desc:'Mobile Crane 20 Tonne'},
    TOWER_CRANE:      {rate_per_day:800, desc:'Tower Crane'},
    CONCRETE_PUMP:    {rate_per_day:350, desc:'Concrete Pump Truck'},
    SCISSOR_LIFT_8M:  {rate_per_day:105, desc:'Scissor Lift 8m'},
    WELDING_MACHINE:  {rate_per_day:25,  desc:'Welding Machine 300A'},
    GENERATOR_5KVA:   {rate_per_day:35,  desc:'Generator 5KVA'},
  },
  equipment_allocation: {
    IfcBeam:         {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcColumn:       {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcSlab:         {equipment:'CONCRETE_PUMP',    duration_factor:0.3},
    IfcDuct:         {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.4},
    IfcCableCarrier: {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.3},
  },

  // ── Column Headers ──
  h_discipline: 'Discipline', h_ifc_class: 'IFC Class', h_quantity: 'Quantity',
  h_uom: 'UOM', h_description: 'Description', h_storey: 'Storey',
  h_phase: 'Phase', h_item: 'Item',
  h_material: 'Material', h_labour: 'Labour', h_equipment: 'Equipment',
  h_total: 'Total', h_unit_rate: 'Unit Rate', h_mat_rate: 'Mat Rate',
  h_lab_rate: 'Lab Rate', h_rate_day: 'Rate/Day', h_trade: 'Trade',
  h_crew_size: 'Crew Size', h_man_days: 'Man-Days', h_duration: 'Duration (days)',
  h_grand_total: 'GRAND TOTAL', h_subtotal: 'SUBTOTAL',

  // ── 4D Headers ──
  h_wbs: 'WBS', h_task_name: 'Task Name', h_productivity: 'Productivity',
  h_gangs: 'Gangs', h_start_date: 'Start Date', h_finish_date: 'Finish Date',
  h_labor_resource: 'Labor Resource', h_status: 'Status',
  h_pct_complete: '% Complete', h_day_offset: 'Day Offset', h_date: 'Date',
  h_milestone: 'Milestone', h_task: 'Task', h_task_count: 'Task Count',
  h_total_days: 'Total Days', h_total_man_days: 'Total Man-Days',
  h_week: 'Week', h_cumulative_pct: 'Cumulative %',

  // ── Chart Titles ──
  t_cost_by_disc: '5D — Cost by Discipline',
  t_cost_components: 'Cost Components by Discipline',
  t_task_dist_phase: 'Task Distribution by Phase',
  t_task_count_disc: 'Task Count by Discipline',
  t_phase_duration: '4D — Phase Duration (Total Days)',
  t_resource_workload: '4D — Resource Workload (Man-Days by Phase)',
  t_s_curve: '4D — S-Curve Progress',
  t_milestone: '4D — Milestone Timeline',
  t_gantt: '4D — Gantt Timeline (Strategic Tasks by Phase)',
  t_vo_impact: '5D — Variation Order Impact',

  // ── Excel Sheet Names ──
  s_cover: 'Cover Sheet', s_exec_summary: 'Executive Summary',
  s_material: 'Material Summary', s_labour: 'Labour Summary',
  s_equipment: 'Equipment Summary', s_boq: 'Detailed BOQ',
  s_prov: 'Provisional Sums', s_schedule: 'Construction Schedule',
  s_proj_summary: 'Project Summary', s_dashboard: 'BIM 4D Dashboard',

  // ── Section Titles ──
  t_comp_boq: 'COMPREHENSIVE BILL OF QUANTITIES',
  t_cost_method: 'COST BREAKDOWN METHODOLOGY',
  t_mat_summary: 'MATERIAL COST SUMMARY',
  t_lab_summary: 'LABOUR COST SUMMARY',
  t_equip_summary: 'EQUIPMENT COST SUMMARY',
  t_prov_sums: 'PROVISIONAL SUMS',
  t_phase_dur_analysis: 'Phase Duration Analysis',
  t_resource_analysis: 'Resource Workload Analysis',
  t_s_curve_progress: 'S-Curve Progress',
  t_milestone_timeline: 'Milestone Timeline',
  t_gantt_timeline: 'Gantt Timeline (Strategic Tasks)',

  // ── Misc ──
  not_started: 'Not Started',
};
