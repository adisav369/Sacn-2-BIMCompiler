// th_TH.js — Thai (Thailand) — BIM OOTB locale
// ISO 3166-1: TH — Flag: 🇹🇭
// To create your project locale: copy this file → MyProject_TRL.js → edit what differs
//
// _TRL_LOCALE overrides _TRL_DEFAULTS at runtime.
// Every key is shown here so you can see and edit the full set.

var _TRL_LOCALE = {

  // ── Identity ──
  iso: 'TH',            // ISO 3166-1 alpha-2 (drives flag emoji)
  lang: 'th',           // ISO 639-1 language
  locale: 'th_TH',      // combined
  source_app: 'BIM OOTB — Frictionless BIM',

  // ── Currency ──
  cur: 'THB',
  cur2: 'USD',
  cur_rate: 35.5,         // 1 USD = 35.5 THB
  cur_name: 'Thai Baht',
  cur2_name: 'US Dollar',

  // ── Rate source attribution ──
  rate_source:       'BOQ Thailand Standard / Department of Public Works and Town & Country Planning 2024',
  rate_year:         '2024',
  rate_mat_source:   'Department of Public Works and Town & Country Planning — Construction Material Price Index 2024',
  rate_mat_ref:      'DPT Standard Cost Estimation 2024 (inflated +3%)',
  rate_mat_includes: 'Delivery, wastage allowance (5-10% by material type)',
  rate_lab_source:   'Ministry of Labour — Minimum Wage + Skilled Trade Premium 2024',
  rate_lab_basis:    'Basic wage + 25% (Social Security 5%, compensation fund 1%, benefits 19%)',
  rate_lab_prod:     'DPT productivity standards by trade',
  rate_lab_crew:     'Skilled workers + helpers as per trade standards',
  rate_eq_source:    'DPT Machinery Hire Rates 2024',
  rate_eq_alloc:     'Based on work type and duration requirements',
  rate_eq_basis:     'Per day (8 hours), operator cost included where stated',

  // ── Material Rates (DPT 2024, THB) ──
  rates: {
    IfcDuct:{rate:1350,unit:'M',desc:'ท่อลมเหล็กชุบสังกะสี (เฉลี่ย 400มม.)'},
    IfcDuctSegment:{rate:1350,unit:'M',desc:'ท่อลมส่วนตรง'},
    IfcDuctFitting:{rate:3100,unit:'EA',desc:'อุปกรณ์ข้อต่อท่อลม (ข้องอ, ทีส์)'},
    IfcPipe:{rate:395,unit:'M',desc:'ท่อ PVC/HDPE (เฉลี่ย 100มม.)'},
    IfcPipeSegment:{rate:395,unit:'M',desc:'ท่อส่วนตรง'},
    IfcPipeFitting:{rate:780,unit:'EA',desc:'อุปกรณ์ข้อต่อท่อ'},
    IfcCableCarrier:{rate:640,unit:'M',desc:'รางเคเบิลเทรย์ (300มม.)'},
    IfcCableCarrierSegment:{rate:640,unit:'M',desc:'รางเคเบิลเทรย์ส่วนตรง'},
    IfcBeam:{rate:5600,unit:'M',desc:'คานเหล็กรูปพรรณ I-Beam'},
    IfcColumn:{rate:10200,unit:'M',desc:'เสาเหล็กรูปพรรณ'},
    IfcSlab:{rate:2350,unit:'M2',desc:'พื้น ค.ส.ล. หนา 250มม.'},
    IfcWall:{rate:1180,unit:'M2',desc:'ผนังก่ออิฐบล็อก 150มม.'},
    IfcWallStandardCase:{rate:1180,unit:'M2',desc:'ผนังมาตรฐาน'},
    IfcCurtainWall:{rate:6200,unit:'M2',desc:'ผนังกระจก'},
    IfcCovering:{rate:1520,unit:'M2',desc:'วัสดุปูพื้น/ฝ้าเพดาน'},
    IfcRoof:{rate:1950,unit:'M2',desc:'หลังคาเหล็ก'},
    IfcLightFixture:{rate:3950,unit:'EA',desc:'โคมไฟ LED'},
    IfcOutlet:{rate:1020,unit:'EA',desc:'เต้ารับไฟฟ้า'},
    IfcDoor:{rate:23500,unit:'EA',desc:'ชุดประตู'},
    IfcWindow:{rate:12800,unit:'EA',desc:'หน้าต่าง'},
    IfcBuildingElementProxy:{rate:6950,unit:'EA',desc:'องค์ประกอบอื่นๆ'},
    IfcFlowTerminal:{rate:28500,unit:'EA',desc:'ปลายทางระบบปรับอากาศ'},
    IfcFurnishingElement:{rate:9800,unit:'EA',desc:'เครื่องเรือน'},
    IfcFurniture:{rate:12200,unit:'EA',desc:'เฟอร์นิเจอร์'},
    IfcPlate:{rate:780,unit:'M2',desc:'แผ่นเหล็ก'},
    IfcMember:{rate:2600,unit:'M',desc:'ชิ้นส่วนเหล็ก'},
    IfcRailing:{rate:2300,unit:'M',desc:'ราวกันตก'},
    IfcStair:{rate:36800,unit:'EA',desc:'บันได'},
    IfcStairFlight:{rate:18000,unit:'EA',desc:'ช่วงบันได'},
    IfcFooting:{rate:2600,unit:'EA',desc:'ฐานรากแผ่'},
    IfcPile:{rate:6950,unit:'EA',desc:'เสาเข็ม'},
    IfcReinforcingBar:{rate:370,unit:'KG',desc:'เหล็กเสริม'},
    IfcFlowSegment:{rate:980,unit:'M',desc:'ท่อส่งระบบ'},
    IfcFlowFitting:{rate:1650,unit:'EA',desc:'ข้อต่อระบบท่อ'},
    IfcFlowController:{rate:3680,unit:'EA',desc:'อุปกรณ์ควบคุมการไหล'},
    IfcEnergyConversionDevice:{rate:69500,unit:'EA',desc:'อุปกรณ์แปลงพลังงาน'},
    IfcFlowTreatmentDevice:{rate:9800,unit:'EA',desc:'อุปกรณ์บำบัด'},
    IfcFlowMovingDevice:{rate:28500,unit:'EA',desc:'อุปกรณ์ขับเคลื่อนของไหล'},
    IfcFlowStorageDevice:{rate:41000,unit:'EA',desc:'อุปกรณ์เก็บกักของไหล'},
    IfcElectricAppliance:{rate:3950,unit:'EA',desc:'เครื่องใช้ไฟฟ้า'},
  },
  rates_default: {rate:4100,unit:'EA',desc:'องค์ประกอบอื่นๆ'},

  // ── Labor Rates (Ministry of Labour 2024, THB) ──
  labor_rates: {
    HVAC_TECH:     {rate_per_day:1500, crew_size:2, trade:'ช่างเทคนิคระบบปรับอากาศ (ฝีมือ)',
                    productivity:{IfcDuct:18,IfcDuctSegment:18,IfcDuctFitting:12}},
    PLUMBER:       {rate_per_day:1350, crew_size:2, trade:'ช่างประปา (ฝีมือ)',
                    productivity:{IfcPipe:25,IfcPipeSegment:25,IfcPipeFitting:15}},
    ELECTRICIAN:   {rate_per_day:1450, crew_size:2, trade:'ช่างไฟฟ้า (ฝีมือ)',
                    productivity:{IfcCableCarrier:30,IfcCableCarrierSegment:30,IfcLightFixture:20,IfcOutlet:25}},
    STEEL_ERECTOR: {rate_per_day:1600, crew_size:4, trade:'ช่างเหล็ก (ฝีมือ)',
                    productivity:{IfcBeam:8,IfcColumn:6}},
    CONCRETE_GANG: {rate_per_day:1200, crew_size:6, trade:'ทีมงานคอนกรีต (ผสม)',
                    productivity:{IfcSlab:35}},
    MASON:         {rate_per_day:1280, crew_size:3, trade:'ช่างก่อ (ฝีมือ) + กรรมกร',
                    productivity:{IfcWall:12,IfcWallStandardCase:12}},
    LABORER:       {rate_per_day:770,  crew_size:1, trade:'กรรมกรทั่วไป',
                    productivity:{}},
  },

  // ── Equipment Rates (DPT 2024, THB) ──
  equipment_rates: {
    MOBILE_CRANE_20T: {rate_per_day:15200, desc:'เครน 20 ตัน'},
    TOWER_CRANE:      {rate_per_day:18000, desc:'ทาวเวอร์เครน'},
    CONCRETE_PUMP:    {rate_per_day:7800,  desc:'รถปั๊มคอนกรีต'},
    SCISSOR_LIFT_8M:  {rate_per_day:2350,  desc:'กระเช้าไฮดรอลิก 8ม.'},
    WELDING_MACHINE:  {rate_per_day:530,   desc:'เครื่องเชื่อม 300A'},
    GENERATOR_5KVA:   {rate_per_day:780,   desc:'เครื่องปั่นไฟ 5KVA'},
  },
  equipment_allocation: {
    IfcBeam:         {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcColumn:       {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcSlab:         {equipment:'CONCRETE_PUMP',    duration_factor:0.3},
    IfcDuct:         {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.4},
    IfcCableCarrier: {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.3},
  },

  // ── Column Headers (Thai) ──
  h_discipline: 'สาขา', h_ifc_class: 'ประเภท IFC', h_quantity: 'จำนวน',
  h_uom: 'หน่วย', h_description: 'รายละเอียด', h_storey: 'ชั้น',
  h_phase: 'ระยะ', h_item: 'รายการ',
  h_material: 'วัสดุ', h_labour: 'แรงงาน', h_equipment: 'เครื่องจักร',
  h_total: 'รวม', h_unit_rate: 'ราคาต่อหน่วย', h_mat_rate: 'ราคาวัสดุ',
  h_lab_rate: 'ค่าแรง', h_rate_day: 'ค่าจ้าง/วัน', h_trade: 'สาขาช่าง',
  h_crew_size: 'จำนวนคน', h_man_days: 'คน-วัน', h_duration: 'ระยะเวลา (วัน)',
  h_grand_total: 'รวมทั้งหมด', h_subtotal: 'รวมย่อย',

  // ── 4D Headers (Thai) ──
  h_wbs: 'WBS', h_task_name: 'ชื่องาน', h_productivity: 'ผลิตภาพ',
  h_gangs: 'ทีมงาน', h_start_date: 'วันเริ่ม', h_finish_date: 'วันเสร็จ',
  h_labor_resource: 'ทรัพยากรแรงงาน', h_status: 'สถานะ',
  h_pct_complete: '% เสร็จ', h_day_offset: 'วันห่าง', h_date: 'วันที่',
  h_milestone: 'เหตุการณ์สำคัญ', h_task: 'งาน', h_task_count: 'จำนวนงาน',
  h_total_days: 'จำนวนวันรวม', h_total_man_days: 'คน-วัน รวม',
  h_week: 'สัปดาห์', h_cumulative_pct: 'สะสม %',

  // ── Chart Titles (Thai) ──
  t_cost_by_disc: '5D — ต้นทุนตามสาขา',
  t_cost_components: 'องค์ประกอบต้นทุนตามสาขา',
  t_task_dist_phase: 'การกระจายงานตามระยะ',
  t_task_count_disc: 'จำนวนงานตามสาขา',
  t_phase_duration: '4D — ระยะเวลาตามระยะ (วันรวม)',
  t_resource_workload: '4D — ภาระงานทรัพยากร (คน-วัน ตามระยะ)',
  t_s_curve: '4D — S-Curve ความคืบหน้า',
  t_milestone: '4D — ไทม์ไลน์เหตุการณ์สำคัญ',
  t_gantt: '4D — Gantt ไทม์ไลน์ (งานหลักตามระยะ)',
  t_vo_impact: '5D — ผลกระทบคำสั่งเปลี่ยนแปลง',

  // ── Excel Sheet Names (Thai) ──
  s_cover: 'หน้าปก', s_exec_summary: 'สรุปผู้บริหาร',
  s_material: 'สรุปวัสดุ', s_labour: 'สรุปแรงงาน',
  s_equipment: 'สรุปเครื่องจักร', s_boq: 'BOQ รายละเอียด',
  s_prov: 'เงินสำรอง', s_schedule: 'แผนก่อสร้าง',
  s_proj_summary: 'สรุปโครงการ', s_dashboard: 'BIM 4D แดชบอร์ด',

  // ── Section Titles (Thai) ──
  t_comp_boq: 'บัญชีแสดงปริมาณงานและราคา',
  t_cost_method: 'วิธีการแยกต้นทุน',
  t_mat_summary: 'สรุปต้นทุนวัสดุ',
  t_lab_summary: 'สรุปต้นทุนแรงงาน',
  t_equip_summary: 'สรุปต้นทุนเครื่องจักร',
  t_prov_sums: 'เงินสำรองจ่าย',
  t_phase_dur_analysis: 'วิเคราะห์ระยะเวลาตามระยะ',
  t_resource_analysis: 'วิเคราะห์ภาระงานทรัพยากร',
  t_s_curve_progress: 'ความคืบหน้า S-Curve',
  t_milestone_timeline: 'ไทม์ไลน์เหตุการณ์สำคัญ',
  t_gantt_timeline: 'Gantt ไทม์ไลน์ (งานหลัก)',

  // ── Misc ──
  not_started: 'ยังไม่เริ่ม',

  // ── UI ──
  tagline:           'BIM ไร้แรงเสียดทาน สอง DB หนึ่งเบราว์เซอร์ ไม่ต้องติดตั้ง',
  ui_tools:          'เครื่องมือ',
  ui_storeys:        'ชั้น',
  ui_disciplines:    'สาขา',
  ui_filter:         'กรอง...',
  ui_all_storeys:    'ทุกชั้น',
  ui_cancel:         'ยกเลิก',
  ui_back:           'กลับ',
  ui_save:           'บันทึก',
  ui_undo:           'เลิกทำ',
  ui_clear:          'ล้าง',
  ui_clear_all:      'ล้างทั้งหมด',
  ui_export_excel:   'ส่งออก Excel',
  ui_show_3d:        'แสดงใน 3D',
  ui_class:          'คลาส',
  ui_name:           'ชื่อ',
  ui_building:       'อาคาร',
  ui_buildings:      'อาคาร',
  ui_elements:       'ชิ้นส่วน',
  ui_done:           'เสร็จ',
  ui_variance:       'ความเบี่ยงเบน',
  ui_initializing:   'กำลังเริ่มต้น...',
  ui_walk_title:     'โหมดเดิน',
  ui_walk_stopped:   'หยุดโหมดเดินแล้ว',
  ui_reading_file:   'กำลังอ่านไฟล์...',
  ui_building_dbs:   'กำลังสร้างฐานข้อมูล...',
  ui_imported:       'นำเข้า {n} ชิ้นส่วน',
  ui_issues_title:   'ปัญหา',
  ui_settings:       'การตั้งค่า',
  ui_language:       'ภาษา',
  ui_currency:       'สกุลเงิน',
  ui_reset_defaults: 'รีเซ็ต',
  ui_apply:          'ใช้งาน',

  // ── Landing page ──
  landing_watch_demo:   'ดูเดโม',
  landing_about:        'เกี่ยวกับ',
  landing_drop_ifc:     'วางไฟล์ IFC หรือ 3D ที่นี่',
  landing_my_buildings: 'อาคารของฉัน',
  landing_city:         'อาคารในเมือง',
  landing_landmark:     'อาคารสำคัญ',
  landing_loading:      'กำลังโหลดรายการอาคาร...',
  landing_created_by:   'สร้างโดย',
  landing_live_stats:   'สถิติสด',
};
