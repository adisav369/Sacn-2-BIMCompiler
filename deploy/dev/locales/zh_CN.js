// zh_CN.js — Simplified Chinese (China)
// ISO 3166-1: CN — Flag: 🇨🇳
// Based on en_MY.js structure — rates reflect Tier 1 city construction costs
//
// _TRL_LOCALE overrides _TRL_DEFAULTS at runtime.
// Every key is shown here so you can see and edit the full set.

var _TRL_LOCALE = {

  // ── Identity ──
  iso: 'CN',            // ISO 3166-1 alpha-2 (drives flag emoji)
  lang: 'zh',           // ISO 639-1 language
  locale: 'zh_CN',      // combined
  source_app: 'BIM OOTB — Frictionless BIM',

  // ── Currency ──
  cur: 'CNY',
  cur2: 'USD',
  cur_rate: 7.24,         // 1 USD = 7.24 CNY
  cur_name: 'Chinese Yuan',
  cur2_name: '美元',

  // ── Rate source attribution ──
  rate_source:       'GB/T 50500-2013 / China Engineering Cost Information Network 2024',
  rate_year:         '2024',
  rate_mat_source:   '中国工程造价信息网 2024',
  rate_mat_ref:      '住建部工程造价指导价 2022-2024（上浮 +3%）',
  rate_mat_includes: '含运输费、损耗（按材料类型5-10%）',
  rate_lab_source:   '住建部建筑业人工费指导价 2024',
  rate_lab_basis:    '基本工资 + 30%（社保18%、公积金12%）',
  rate_lab_prod:     '按各工种定额标准',
  rate_lab_crew:     '技术工 + 普工按工种标准配置',
  rate_eq_source:    '住建部施工机械台班费用定额 2024',
  rate_eq_alloc:     '按工程类型及工期需求配置',
  rate_eq_basis:     '每台班（8小时），含操作人员费用（注明处）',

  // ── Material Rates (GB/T 50500 2024, CNY, Tier 1 city) ──
  rates: {
    IfcDuct:{rate:268,unit:'M',desc:'镀锌钢板风管（平均400mm）'},
    IfcDuctSegment:{rate:268,unit:'M',desc:'风管段'},
    IfcDuctFitting:{rate:620,unit:'EA',desc:'风管配件（弯头、三通）'},
    IfcPipe:{rate:78,unit:'M',desc:'PVC/HDPE管道（平均100mm）'},
    IfcPipeSegment:{rate:78,unit:'M',desc:'管道段'},
    IfcPipeFitting:{rate:155,unit:'EA',desc:'管道配件'},
    IfcCableCarrier:{rate:126,unit:'M',desc:'电缆桥架（300mm）'},
    IfcCableCarrierSegment:{rate:126,unit:'M',desc:'电缆桥架段'},
    IfcBeam:{rate:1105,unit:'M',desc:'结构钢工字梁'},
    IfcColumn:{rate:2030,unit:'M',desc:'结构钢柱'},
    IfcSlab:{rate:462,unit:'M2',desc:'钢筋混凝土楼板 250mm'},
    IfcWall:{rate:235,unit:'M2',desc:'砌块墙 150mm'},
    IfcWallStandardCase:{rate:235,unit:'M2',desc:'标准墙体'},
    IfcCurtainWall:{rate:1220,unit:'M2',desc:'幕墙'},
    IfcCovering:{rate:300,unit:'M2',desc:'地面/天棚饰面'},
    IfcRoof:{rate:386,unit:'M2',desc:'金属屋面'},
    IfcLightFixture:{rate:788,unit:'EA',desc:'LED灯具'},
    IfcOutlet:{rate:203,unit:'EA',desc:'电源插座'},
    IfcDoor:{rate:4630,unit:'EA',desc:'门套装'},
    IfcWindow:{rate:2565,unit:'EA',desc:'窗户'},
    IfcBuildingElementProxy:{rate:1380,unit:'EA',desc:'其他构件'},
    IfcFlowTerminal:{rate:5685,unit:'EA',desc:'暖通末端设备'},
    IfcFurnishingElement:{rate:1950,unit:'EA',desc:'家具'},
    IfcFurniture:{rate:2436,unit:'EA',desc:'家具'},
    IfcPlate:{rate:154,unit:'M2',desc:'钢板'},
    IfcMember:{rate:520,unit:'M',desc:'钢构件'},
    IfcRailing:{rate:455,unit:'M',desc:'栏杆'},
    IfcStair:{rate:7308,unit:'EA',desc:'楼梯'},
    IfcStairFlight:{rate:3572,unit:'EA',desc:'楼梯梯段'},
    IfcFooting:{rate:520,unit:'EA',desc:'基础'},
    IfcPile:{rate:1380,unit:'EA',desc:'桩基'},
    IfcReinforcingBar:{rate:73,unit:'KG',desc:'钢筋'},
    IfcFlowSegment:{rate:195,unit:'M',desc:'流体管段'},
    IfcFlowFitting:{rate:325,unit:'EA',desc:'流体配件'},
    IfcFlowController:{rate:731,unit:'EA',desc:'流体控制设备'},
    IfcEnergyConversionDevice:{rate:13804,unit:'EA',desc:'能量转换设备'},
    IfcFlowTreatmentDevice:{rate:1950,unit:'EA',desc:'流体处理设备'},
    IfcFlowMovingDevice:{rate:5685,unit:'EA',desc:'流体输送设备'},
    IfcFlowStorageDevice:{rate:8120,unit:'EA',desc:'流体储存设备'},
    IfcElectricAppliance:{rate:788,unit:'EA',desc:'电气设备'},
  },
  rates_default: {rate:812,unit:'EA',desc:'其他构件'},

  // ── Labor Rates (CNY, Tier 1 city daily rates) ──
  labor_rates: {
    HVAC_TECH:     {rate_per_day:450, crew_size:2, trade:'暖通技工（高级）',
                    productivity:{IfcDuct:18,IfcDuctSegment:18,IfcDuctFitting:12}},
    PLUMBER:       {rate_per_day:400, crew_size:2, trade:'管道工（高级）',
                    productivity:{IfcPipe:25,IfcPipeSegment:25,IfcPipeFitting:15}},
    ELECTRICIAN:   {rate_per_day:420, crew_size:2, trade:'电工（高级）',
                    productivity:{IfcCableCarrier:30,IfcCableCarrierSegment:30,IfcLightFixture:20,IfcOutlet:25}},
    STEEL_ERECTOR: {rate_per_day:480, crew_size:4, trade:'钢结构安装工（高级）',
                    productivity:{IfcBeam:8,IfcColumn:6}},
    CONCRETE_GANG: {rate_per_day:350, crew_size:6, trade:'混凝土施工班组',
                    productivity:{IfcSlab:35}},
    MASON:         {rate_per_day:380, crew_size:3, trade:'砌筑工（高级）+ 普工',
                    productivity:{IfcWall:12,IfcWallStandardCase:12}},
    LABORER:       {rate_per_day:250, crew_size:1, trade:'普通工',
                    productivity:{}},
  },

  // ── Equipment Rates (CNY per day) ──
  equipment_rates: {
    MOBILE_CRANE_20T: {rate_per_day:3200, desc:'汽车起重机 20吨'},
    TOWER_CRANE:      {rate_per_day:3800, desc:'塔式起重机'},
    CONCRETE_PUMP:    {rate_per_day:1650, desc:'混凝土泵车'},
    SCISSOR_LIFT_8M:  {rate_per_day:500,  desc:'剪叉式升降平台 8m'},
    WELDING_MACHINE:  {rate_per_day:120,  desc:'电焊机 300A'},
    GENERATOR_5KVA:   {rate_per_day:180,  desc:'发电机 5KVA'},
  },
  equipment_allocation: {
    IfcBeam:         {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcColumn:       {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcSlab:         {equipment:'CONCRETE_PUMP',    duration_factor:0.3},
    IfcDuct:         {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.4},
    IfcCableCarrier: {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.3},
  },

  // ── Column Headers ──
  h_discipline: '专业', h_ifc_class: 'IFC类型', h_quantity: '数量',
  h_uom: '单位', h_description: '描述', h_storey: '楼层',
  h_phase: '阶段', h_item: '项目',
  h_material: '材料', h_labour: '人工', h_equipment: '机械',
  h_total: '合计', h_unit_rate: '综合单价', h_mat_rate: '材料单价',
  h_lab_rate: '人工单价', h_rate_day: '台班费', h_trade: '工种',
  h_crew_size: '班组人数', h_man_days: '工日', h_duration: '工期（天）',
  h_grand_total: '总计', h_subtotal: '小计',

  // ── 4D Headers ──
  h_wbs: 'WBS', h_task_name: '任务名称', h_productivity: '生产率',
  h_gangs: '班组数', h_start_date: '开始日期', h_finish_date: '完成日期',
  h_labor_resource: '劳务资源', h_status: '状态',
  h_pct_complete: '完成百分比', h_day_offset: '天数偏移', h_date: '日期',
  h_milestone: '里程碑', h_task: '任务', h_task_count: '任务数',
  h_total_days: '总天数', h_total_man_days: '总工日',
  h_week: '周', h_cumulative_pct: '累计百分比',

  // ── Chart Titles ──
  t_cost_by_disc: '5D — 各专业造价',
  t_cost_components: '各专业费用组成',
  t_task_dist_phase: '各阶段任务分布',
  t_task_count_disc: '各专业任务数量',
  t_phase_duration: '4D — 各阶段工期（总天数）',
  t_resource_workload: '4D — 资源负荷（各阶段工日）',
  t_s_curve: '4D — S曲线进度',
  t_milestone: '4D — 里程碑时间线',
  t_gantt: '4D — 甘特图（各阶段关键任务）',
  t_vo_impact: '5D — 变更令影响',

  // ── Excel Sheet Names ──
  s_cover: '封面', s_exec_summary: '项目概要',
  s_material: '材料汇总', s_labour: '人工汇总',
  s_equipment: '机械汇总', s_boq: '工程量清单',
  s_prov: '暂估价', s_schedule: '施工进度计划',
  s_proj_summary: '项目总结', s_dashboard: 'BIM 4D 看板',

  // ── Section Titles ──
  t_comp_boq: '工程量清单',
  t_cost_method: '费用分解方法',
  t_mat_summary: '材料费汇总',
  t_lab_summary: '人工费汇总',
  t_equip_summary: '机械费汇总',
  t_prov_sums: '暂估价',
  t_phase_dur_analysis: '各阶段工期分析',
  t_resource_analysis: '资源负荷分析',
  t_s_curve_progress: 'S曲线进度',
  t_milestone_timeline: '里程碑时间线',
  t_gantt_timeline: '甘特图（关键任务）',

  // ── Misc ──
  not_started: '未开始',
};
