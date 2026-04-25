// ms_MY.js — Bahasa Melayu (Malaysia) — locale
// ISO 3166-1: MY — Flag: 🇲🇾
// To create your project locale: copy this file → MyProject_TRL.js → edit what differs
//
// _TRL_LOCALE overrides _TRL_DEFAULTS at runtime.
// Every key is shown here so you can see and edit the full set.

var _TRL_LOCALE = {

  // ── Identity ──
  iso: 'MY',            // ISO 3166-1 alpha-2 (drives flag emoji)
  lang: 'ms',           // ISO 639-1 language
  locale: 'ms_MY',      // combined
  source_app: 'BIM OOTB — Frictionless BIM',

  // ── Currency ──
  cur: 'RM',
  cur2: 'USD',
  cur_rate: 4.45,         // 1 USD = 4.45 RM
  cur_name: 'Ringgit Malaysia',
  cur2_name: 'Dolar AS',

  // ── Rate source attribution ──
  rate_source:       'CIDB Malaysia 2024 / Buku Kos BCISM',
  rate_year:         '2024',
  rate_mat_source:   'Pusat Kos Pembinaan Kebangsaan CIDB (N3C) 2024',
  rate_mat_ref:      'Buku Kos BCISM 2022-2024 (inflasi +3%)',
  rate_mat_includes: 'Penghantaran, elaun pembaziran (5-10% mengikut jenis bahan)',
  rate_lab_source:   'Tinjauan Gaji Pekerja MBAM-CIDB 2024',
  rate_lab_basis:    'Gaji asas + 30% (KWSP 13%, PERKESO 2%, manfaat 15%)',
  rate_lab_prod:     'Piawaian produktiviti CIDB mengikut tred',
  rate_lab_crew:     'Pekerja mahir + pembantu mengikut piawaian tred',
  rate_eq_source:    'Kadar Sewaan Jentera CIDB N3C 2024',
  rate_eq_alloc:     'Berdasarkan jenis kerja dan keperluan tempoh',
  rate_eq_basis:     'Sehari (8 jam), kos pengendali termasuk jika dinyatakan',

  // ── Material Rates (CIDB 2024) ──
  rates: {
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
    IfcFurniture:{rate:1500,unit:'EA',desc:'Furniture'},
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
  },
  rates_default: {rate:500,unit:'EA',desc:'Misc Element'},

  // ── Labor Rates ──
  labor_rates: {
    HVAC_TECH:     {rate_per_day:185, crew_size:2, trade:'Juruteknik HVAC (Mahir)',
                    productivity:{IfcDuct:18,IfcDuctSegment:18,IfcDuctFitting:12}},
    PLUMBER:       {rate_per_day:165, crew_size:2, trade:'Tukang Paip (Mahir)',
                    productivity:{IfcPipe:25,IfcPipeSegment:25,IfcPipeFitting:15}},
    ELECTRICIAN:   {rate_per_day:175, crew_size:2, trade:'Juruelektrik (Mahir)',
                    productivity:{IfcCableCarrier:30,IfcCableCarrierSegment:30,IfcLightFixture:20,IfcOutlet:25}},
    STEEL_ERECTOR: {rate_per_day:195, crew_size:4, trade:'Pemasang Keluli (Mahir)',
                    productivity:{IfcBeam:8,IfcColumn:6}},
    CONCRETE_GANG: {rate_per_day:145, crew_size:6, trade:'Kumpulan Konkrit (Campuran)',
                    productivity:{IfcSlab:35}},
    MASON:         {rate_per_day:155, crew_size:3, trade:'Tukang Batu (Mahir) + Pekerja Am',
                    productivity:{IfcWall:12,IfcWallStandardCase:12}},
    LABORER:       {rate_per_day:95,  crew_size:1, trade:'Pekerja Am',
                    productivity:{}},
  },

  // ── Equipment Rates ──
  equipment_rates: {
    MOBILE_CRANE_20T: {rate_per_day:1850, desc:'Kren Mudah Alih 20 Tan'},
    TOWER_CRANE:      {rate_per_day:2200, desc:'Kren Menara'},
    CONCRETE_PUMP:    {rate_per_day:950,  desc:'Lori Pam Konkrit'},
    SCISSOR_LIFT_8M:  {rate_per_day:285,  desc:'Lif Gunting 8m'},
    WELDING_MACHINE:  {rate_per_day:65,   desc:'Mesin Kimpalan 300A'},
    GENERATOR_5KVA:   {rate_per_day:95,   desc:'Penjana Kuasa 5KVA'},
  },
  equipment_allocation: {
    IfcBeam:         {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcColumn:       {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcSlab:         {equipment:'CONCRETE_PUMP',    duration_factor:0.3},
    IfcDuct:         {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.4},
    IfcCableCarrier: {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.3},
  },

  // ── Column Headers ──
  h_discipline: 'Disiplin', h_ifc_class: 'Kelas IFC', h_quantity: 'Kuantiti',
  h_uom: 'UOM', h_description: 'Keterangan', h_storey: 'Tingkat',
  h_phase: 'Fasa', h_item: 'Item',
  h_material: 'Bahan', h_labour: 'Buruh', h_equipment: 'Peralatan',
  h_total: 'Jumlah', h_unit_rate: 'Kadar Unit', h_mat_rate: 'Kadar Bahan',
  h_lab_rate: 'Kadar Buruh', h_rate_day: 'Kadar/Hari', h_trade: 'Tred',
  h_crew_size: 'Saiz Kumpulan', h_man_days: 'Hari-Pekerja', h_duration: 'Tempoh (hari)',
  h_grand_total: 'JUMLAH BESAR', h_subtotal: 'JUMLAH KECIL',

  // ── 4D Headers ──
  h_wbs: 'WBS', h_task_name: 'Nama Tugas', h_productivity: 'Produktiviti',
  h_gangs: 'Kumpulan', h_start_date: 'Tarikh Mula', h_finish_date: 'Tarikh Siap',
  h_labor_resource: 'Sumber Buruh', h_status: 'Status',
  h_pct_complete: '% Siap', h_day_offset: 'Ofset Hari', h_date: 'Tarikh',
  h_milestone: 'Batu Penanda', h_task: 'Tugas', h_task_count: 'Bilangan Tugas',
  h_total_days: 'Jumlah Hari', h_total_man_days: 'Jumlah Hari-Pekerja',
  h_week: 'Minggu', h_cumulative_pct: 'Kumulatif %',

  // ── Chart Titles ──
  t_cost_by_disc: '5D — Kos Mengikut Disiplin',
  t_cost_components: 'Komponen Kos Mengikut Disiplin',
  t_task_dist_phase: 'Taburan Tugas Mengikut Fasa',
  t_task_count_disc: 'Bilangan Tugas Mengikut Disiplin',
  t_phase_duration: '4D — Tempoh Fasa (Jumlah Hari)',
  t_resource_workload: '4D — Beban Kerja Sumber (Hari-Pekerja Mengikut Fasa)',
  t_s_curve: '4D — Keluk-S Kemajuan',
  t_milestone: '4D — Garis Masa Batu Penanda',
  t_gantt: '4D — Garis Masa Gantt (Tugas Strategik Mengikut Fasa)',
  t_vo_impact: '5D — Kesan Arahan Perubahan',

  // ── Excel Sheet Names ──
  s_cover: 'Muka Depan', s_exec_summary: 'Ringkasan Eksekutif',
  s_material: 'Ringkasan Bahan', s_labour: 'Ringkasan Buruh',
  s_equipment: 'Ringkasan Peralatan', s_boq: 'BOQ Terperinci',
  s_prov: 'Peruntukan Sementara', s_schedule: 'Jadual Pembinaan',
  s_proj_summary: 'Ringkasan Projek', s_dashboard: 'Papan Pemuka BIM 4D',

  // ── Section Titles ──
  t_comp_boq: 'SENARAI KUANTITI KOMPREHENSIF',
  t_cost_method: 'METODOLOGI PECAHAN KOS',
  t_mat_summary: 'RINGKASAN KOS BAHAN',
  t_lab_summary: 'RINGKASAN KOS BURUH',
  t_equip_summary: 'RINGKASAN KOS PERALATAN',
  t_prov_sums: 'PERUNTUKAN SEMENTARA',
  t_phase_dur_analysis: 'Analisis Tempoh Fasa',
  t_resource_analysis: 'Analisis Beban Kerja Sumber',
  t_s_curve_progress: 'Kemajuan Keluk-S',
  t_milestone_timeline: 'Garis Masa Batu Penanda',
  t_gantt_timeline: 'Garis Masa Gantt (Tugas Strategik)',

  // ── Misc ──
  not_started: 'Belum Dimulakan',
};
