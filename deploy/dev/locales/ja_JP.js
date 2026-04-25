// ja_JP.js — Japanese (Japan)
// ISO 3166-1: JP — Flag: 🇯🇵
// To create your project locale: copy this file → MyProject_TRL.js → edit what differs
//
// _TRL_LOCALE overrides _TRL_DEFAULTS at runtime.
// Every key is shown here so you can see and edit the full set.

var _TRL_LOCALE = {

  // ── Identity ──
  iso: 'JP',            // ISO 3166-1 alpha-2 (drives flag emoji)
  lang: 'ja',           // ISO 639-1 language
  locale: 'ja_JP',      // combined
  source_app: 'BIM OOTB — Frictionless BIM',

  // ── Currency ──
  cur: 'JPY',
  cur2: 'USD',
  cur_rate: 154.5,        // 1 USD = 154.5 JPY
  cur_name: '日本円',
  cur2_name: '米ドル',

  // ── Rate source attribution ──
  rate_source:       'JBCI Construction Cost Index / Ministry of Land 2024',
  rate_year:         '2024',
  rate_mat_source:   '国土交通省 建設資材価格調査 2024',
  rate_mat_ref:      '建設物価調査会 積算資料 2024 (+2%上昇調整)',
  rate_mat_includes: '配送費、ロス率含む（資材別5-10%）',
  rate_lab_source:   '国土交通省 公共工事設計労務単価 2024',
  rate_lab_basis:    '基本日額 + 30%（社会保険料・福利厚生費）',
  rate_lab_prod:     '国土交通省 標準歩掛り',
  rate_lab_crew:     '技能者 + 普通作業員（職種別標準編成）',
  rate_eq_source:    '日本建設機械施工協会 機械経費積算基準 2024',
  rate_eq_alloc:     '工種・工期に基づく配分',
  rate_eq_basis:     '1日あたり（8時間）、運転手付き記載あり',

  // ── Material Rates (国交省 2024) ──
  rates: {
    IfcDuct:{rate:5800,unit:'M',desc:'亜鉛鋼板ダクト（平均400mm）'},
    IfcDuctSegment:{rate:5800,unit:'M',desc:'ダクトセグメント'},
    IfcDuctFitting:{rate:12500,unit:'EA',desc:'ダクト継手（エルボ、ティー）'},
    IfcPipe:{rate:2200,unit:'M',desc:'配管（SGP/VP 平均100mm）'},
    IfcPipeSegment:{rate:2200,unit:'M',desc:'配管セグメント'},
    IfcPipeFitting:{rate:4500,unit:'EA',desc:'配管継手'},
    IfcCableCarrier:{rate:3800,unit:'M',desc:'ケーブルラック（300mm）'},
    IfcCableCarrierSegment:{rate:3800,unit:'M',desc:'ケーブルラックセグメント'},
    IfcBeam:{rate:28000,unit:'M',desc:'構造用H形鋼梁'},
    IfcColumn:{rate:52000,unit:'M',desc:'構造用鋼柱'},
    IfcSlab:{rate:12800,unit:'M2',desc:'RC床版 250mm'},
    IfcWall:{rate:6500,unit:'M2',desc:'コンクリートブロック壁 150mm'},
    IfcWallStandardCase:{rate:6500,unit:'M2',desc:'標準壁'},
    IfcCurtainWall:{rate:35000,unit:'M2',desc:'カーテンウォール'},
    IfcCovering:{rate:8500,unit:'M2',desc:'床・天井仕上げ'},
    IfcRoof:{rate:11500,unit:'M2',desc:'金属屋根'},
    IfcLightFixture:{rate:18500,unit:'EA',desc:'LED照明器具'},
    IfcOutlet:{rate:5800,unit:'EA',desc:'コンセント'},
    IfcDoor:{rate:125000,unit:'EA',desc:'建具セット'},
    IfcWindow:{rate:72000,unit:'EA',desc:'窓'},
    IfcBuildingElementProxy:{rate:35000,unit:'EA',desc:'その他要素'},
    IfcFlowTerminal:{rate:145000,unit:'EA',desc:'空調端末'},
    IfcFurnishingElement:{rate:48000,unit:'EA',desc:'家具'},
    IfcFurniture:{rate:62000,unit:'EA',desc:'家具'},
    IfcPlate:{rate:4200,unit:'M2',desc:'鋼板'},
    IfcMember:{rate:13500,unit:'M',desc:'鋼部材'},
    IfcRailing:{rate:12800,unit:'M',desc:'手摺り'},
    IfcStair:{rate:185000,unit:'EA',desc:'階段'},
    IfcStairFlight:{rate:92000,unit:'EA',desc:'階段フライト'},
    IfcFooting:{rate:14500,unit:'EA',desc:'基礎フーチング'},
    IfcPile:{rate:38000,unit:'EA',desc:'基礎杭'},
    IfcReinforcingBar:{rate:185,unit:'KG',desc:'鉄筋'},
    IfcFlowSegment:{rate:5200,unit:'M',desc:'流路セグメント'},
    IfcFlowFitting:{rate:8500,unit:'EA',desc:'流路継手'},
    IfcFlowController:{rate:19500,unit:'EA',desc:'流路制御機器'},
    IfcEnergyConversionDevice:{rate:380000,unit:'EA',desc:'エネルギー変換機器'},
    IfcFlowTreatmentDevice:{rate:52000,unit:'EA',desc:'流体処理機器'},
    IfcFlowMovingDevice:{rate:145000,unit:'EA',desc:'流体搬送機器'},
    IfcFlowStorageDevice:{rate:215000,unit:'EA',desc:'流体貯蔵機器'},
    IfcElectricAppliance:{rate:22000,unit:'EA',desc:'電気機器'},
  },
  rates_default: {rate:22000,unit:'EA',desc:'その他要素'},

  // ── Labor Rates ──
  labor_rates: {
    HVAC_TECH:     {rate_per_day:28500, crew_size:2, trade:'空調技能者（熟練）',
                    productivity:{IfcDuct:16,IfcDuctSegment:16,IfcDuctFitting:10}},
    PLUMBER:       {rate_per_day:26000, crew_size:2, trade:'配管工（熟練）',
                    productivity:{IfcPipe:22,IfcPipeSegment:22,IfcPipeFitting:14}},
    ELECTRICIAN:   {rate_per_day:27000, crew_size:2, trade:'電気工（熟練）',
                    productivity:{IfcCableCarrier:28,IfcCableCarrierSegment:28,IfcLightFixture:18,IfcOutlet:22}},
    STEEL_ERECTOR: {rate_per_day:30000, crew_size:4, trade:'鉄骨工（熟練）',
                    productivity:{IfcBeam:7,IfcColumn:5}},
    CONCRETE_GANG: {rate_per_day:22000, crew_size:6, trade:'コンクリート打設班',
                    productivity:{IfcSlab:30}},
    MASON:         {rate_per_day:24000, crew_size:3, trade:'左官・ブロック工（熟練）+ 普通作業員',
                    productivity:{IfcWall:10,IfcWallStandardCase:10}},
    LABORER:       {rate_per_day:17000, crew_size:1, trade:'普通作業員',
                    productivity:{}},
  },

  // ── Equipment Rates ──
  equipment_rates: {
    MOBILE_CRANE_20T: {rate_per_day:85000, desc:'移動式クレーン 20トン'},
    TOWER_CRANE:      {rate_per_day:120000, desc:'タワークレーン'},
    CONCRETE_PUMP:    {rate_per_day:65000,  desc:'コンクリートポンプ車'},
    SCISSOR_LIFT_8M:  {rate_per_day:15000,  desc:'シザースリフト 8m'},
    WELDING_MACHINE:  {rate_per_day:4500,   desc:'溶接機 300A'},
    GENERATOR_5KVA:   {rate_per_day:6500,   desc:'発電機 5KVA'},
  },
  equipment_allocation: {
    IfcBeam:         {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcColumn:       {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcSlab:         {equipment:'CONCRETE_PUMP',    duration_factor:0.3},
    IfcDuct:         {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.4},
    IfcCableCarrier: {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.3},
  },

  // ── Column Headers ──
  h_discipline: '専門分野', h_ifc_class: 'IFCクラス', h_quantity: '数量',
  h_uom: '単位', h_description: '説明', h_storey: '階',
  h_phase: 'フェーズ', h_item: '項目',
  h_material: '材料費', h_labour: '労務費', h_equipment: '機械費',
  h_total: '合計', h_unit_rate: '単価', h_mat_rate: '材料単価',
  h_lab_rate: '労務単価', h_rate_day: '日額', h_trade: '職種',
  h_crew_size: '作業員数', h_man_days: '人工', h_duration: '工期（日）',
  h_grand_total: '総合計', h_subtotal: '小計',

  // ── 4D Headers ──
  h_wbs: 'WBS', h_task_name: '作業名', h_productivity: '歩掛り',
  h_gangs: '班', h_start_date: '着工日', h_finish_date: '完工日',
  h_labor_resource: '労務資源', h_status: '状態',
  h_pct_complete: '進捗率', h_day_offset: '日数オフセット', h_date: '日付',
  h_milestone: 'マイルストーン', h_task: '作業', h_task_count: '作業数',
  h_total_days: '総日数', h_total_man_days: '総人工',
  h_week: '週', h_cumulative_pct: '累積進捗率',

  // ── Chart Titles ──
  t_cost_by_disc: '5D — 専門分野別コスト',
  t_cost_components: '専門分野別コスト構成',
  t_task_dist_phase: 'フェーズ別作業分布',
  t_task_count_disc: '専門分野別作業数',
  t_phase_duration: '4D — フェーズ工期（総日数）',
  t_resource_workload: '4D — 資源投入量（フェーズ別人工）',
  t_s_curve: '4D — Sカーブ進捗',
  t_milestone: '4D — マイルストーンタイムライン',
  t_gantt: '4D — ガントチャート（フェーズ別戦略作業）',
  t_vo_impact: '5D — 変更指示影響',

  // ── Excel Sheet Names ──
  s_cover: '表紙', s_exec_summary: '概要',
  s_material: '材料費集計', s_labour: '労務費集計',
  s_equipment: '機械費集計', s_boq: '詳細数量調書',
  s_prov: '暫定金額', s_schedule: '工程表',
  s_proj_summary: 'プロジェクト概要', s_dashboard: 'BIM 4Dダッシュボード',

  // ── Section Titles ──
  t_comp_boq: '数量調書（総括）',
  t_cost_method: 'コスト積算方法',
  t_mat_summary: '材料費集計',
  t_lab_summary: '労務費集計',
  t_equip_summary: '機械費集計',
  t_prov_sums: '暫定金額',
  t_phase_dur_analysis: 'フェーズ工期分析',
  t_resource_analysis: '資源投入量分析',
  t_s_curve_progress: 'Sカーブ進捗',
  t_milestone_timeline: 'マイルストーンタイムライン',
  t_gantt_timeline: 'ガントチャート（戦略作業）',

  // ── Misc ──
  not_started: '未着手',
};
