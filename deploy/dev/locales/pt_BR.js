// pt_BR.js — Portuguese (Brazil)
// ISO 3166-1: BR — Flag: 🇧🇷
// To create your project locale: copy this file → MyProject_TRL.js → edit what differs
//
// _TRL_LOCALE overrides _TRL_DEFAULTS at runtime.
// Every key is shown here so you can see and edit the full set.

var _TRL_LOCALE = {

  // ── Identity ──
  iso: 'BR',            // ISO 3166-1 alpha-2 (drives flag emoji)
  lang: 'pt',           // ISO 639-1 language
  locale: 'pt_BR',      // combined
  source_app: 'BIM OOTB — Frictionless BIM',

  // ── Currency ──
  cur: 'BRL',
  cur2: 'USD',
  cur_rate: 5.05,         // 1 USD = 5.05 BRL
  cur_name: 'Real Brasileiro',
  cur2_name: 'Dólar Americano',

  // ── Rate source attribution ──
  rate_source:       'SINAPI / TCPO Tabela 2024',
  rate_year:         '2024',
  rate_mat_source:   'SINAPI — Sistema Nacional de Pesquisa de Custos e Índices 2024',
  rate_mat_ref:      'SINAPI Caixa/IBGE — Preços de insumos 2024 (+3% INCC)',
  rate_mat_includes: 'Frete, perdas e desperdícios (5-10% por tipo de material)',
  rate_lab_source:   'SINAPI — Composições de custo mão de obra 2024',
  rate_lab_basis:    'Salário base + 80% (encargos sociais: INSS, FGTS, férias, 13º)',
  rate_lab_prod:     'TCPO — Tabelas de Composições de Preços para Orçamentos',
  rate_lab_crew:     'Oficiais + serventes conforme composição padrão',
  rate_eq_source:    'SINAPI — Equipamentos e máquinas 2024',
  rate_eq_alloc:     'Alocação conforme tipo de serviço e duração',
  rate_eq_basis:     'Por dia (8 horas), operador incluso quando indicado',

  // ── Material Rates (SINAPI 2024) ──
  rates: {
    IfcDuct:{rate:195,unit:'M',desc:'Duto em chapa galvanizada (média 400mm)'},
    IfcDuctSegment:{rate:195,unit:'M',desc:'Segmento de duto'},
    IfcDuctFitting:{rate:450,unit:'EA',desc:'Conexões de duto (curvas, tês)'},
    IfcPipe:{rate:58,unit:'M',desc:'Tubo PVC/PEAD (média 100mm)'},
    IfcPipeSegment:{rate:58,unit:'M',desc:'Segmento de tubo'},
    IfcPipeFitting:{rate:115,unit:'EA',desc:'Conexões de tubulação'},
    IfcCableCarrier:{rate:92,unit:'M',desc:'Eletrocalha (300mm)'},
    IfcCableCarrierSegment:{rate:92,unit:'M',desc:'Segmento de eletrocalha'},
    IfcBeam:{rate:810,unit:'M',desc:'Viga metálica perfil I'},
    IfcColumn:{rate:1480,unit:'M',desc:'Pilar metálico'},
    IfcSlab:{rate:340,unit:'M2',desc:'Laje de concreto armado 250mm'},
    IfcWall:{rate:175,unit:'M2',desc:'Alvenaria de bloco 150mm'},
    IfcWallStandardCase:{rate:175,unit:'M2',desc:'Parede padrão'},
    IfcCurtainWall:{rate:890,unit:'M2',desc:'Pele de vidro'},
    IfcCovering:{rate:220,unit:'M2',desc:'Revestimento piso/teto'},
    IfcRoof:{rate:285,unit:'M2',desc:'Cobertura metálica'},
    IfcLightFixture:{rate:580,unit:'EA',desc:'Luminária LED'},
    IfcOutlet:{rate:148,unit:'EA',desc:'Tomada elétrica'},
    IfcDoor:{rate:3400,unit:'EA',desc:'Conjunto de porta'},
    IfcWindow:{rate:1880,unit:'EA',desc:'Janela'},
    IfcBuildingElementProxy:{rate:1020,unit:'EA',desc:'Elemento diverso'},
    IfcFlowTerminal:{rate:4150,unit:'EA',desc:'Terminal HVAC'},
    IfcFurnishingElement:{rate:1450,unit:'EA',desc:'Mobiliário'},
    IfcFurniture:{rate:1780,unit:'EA',desc:'Mobiliário'},
    IfcPlate:{rate:115,unit:'M2',desc:'Chapa de aço'},
    IfcMember:{rate:385,unit:'M',desc:'Perfil metálico'},
    IfcRailing:{rate:335,unit:'M',desc:'Guarda-corpo'},
    IfcStair:{rate:5350,unit:'EA',desc:'Escada'},
    IfcStairFlight:{rate:2650,unit:'EA',desc:'Lance de escada'},
    IfcFooting:{rate:385,unit:'EA',desc:'Sapata de fundação'},
    IfcPile:{rate:1020,unit:'EA',desc:'Estaca de fundação'},
    IfcReinforcingBar:{rate:52,unit:'KG',desc:'Aço para armadura'},
    IfcFlowSegment:{rate:145,unit:'M',desc:'Segmento de fluxo'},
    IfcFlowFitting:{rate:240,unit:'EA',desc:'Conexão de fluxo'},
    IfcFlowController:{rate:535,unit:'EA',desc:'Controlador de fluxo'},
    IfcEnergyConversionDevice:{rate:10200,unit:'EA',desc:'Equipamento de conversão de energia'},
    IfcFlowTreatmentDevice:{rate:1450,unit:'EA',desc:'Equipamento de tratamento'},
    IfcFlowMovingDevice:{rate:4150,unit:'EA',desc:'Equipamento de movimentação'},
    IfcFlowStorageDevice:{rate:5950,unit:'EA',desc:'Equipamento de armazenamento'},
    IfcElectricAppliance:{rate:580,unit:'EA',desc:'Aparelho elétrico'},
  },
  rates_default: {rate:600,unit:'EA',desc:'Elemento diverso'},

  // ── Labor Rates ──
  labor_rates: {
    HVAC_TECH:     {rate_per_day:225, crew_size:2, trade:'Mecânico de refrigeração (oficial)',
                    productivity:{IfcDuct:17,IfcDuctSegment:17,IfcDuctFitting:11}},
    PLUMBER:       {rate_per_day:198, crew_size:2, trade:'Encanador (oficial)',
                    productivity:{IfcPipe:24,IfcPipeSegment:24,IfcPipeFitting:14}},
    ELECTRICIAN:   {rate_per_day:210, crew_size:2, trade:'Eletricista (oficial)',
                    productivity:{IfcCableCarrier:28,IfcCableCarrierSegment:28,IfcLightFixture:19,IfcOutlet:24}},
    STEEL_ERECTOR: {rate_per_day:235, crew_size:4, trade:'Montador de estrutura metálica (oficial)',
                    productivity:{IfcBeam:7,IfcColumn:5}},
    CONCRETE_GANG: {rate_per_day:175, crew_size:6, trade:'Equipe de concretagem',
                    productivity:{IfcSlab:33}},
    MASON:         {rate_per_day:185, crew_size:3, trade:'Pedreiro (oficial) + serventes',
                    productivity:{IfcWall:11,IfcWallStandardCase:11}},
    LABORER:       {rate_per_day:115, crew_size:1, trade:'Servente',
                    productivity:{}},
  },

  // ── Equipment Rates ──
  equipment_rates: {
    MOBILE_CRANE_20T: {rate_per_day:2200, desc:'Guindaste móvel 20 toneladas'},
    TOWER_CRANE:      {rate_per_day:2650, desc:'Grua torre'},
    CONCRETE_PUMP:    {rate_per_day:1150, desc:'Bomba de concreto'},
    SCISSOR_LIFT_8M:  {rate_per_day:340,  desc:'Plataforma elevatória 8m'},
    WELDING_MACHINE:  {rate_per_day:78,   desc:'Máquina de solda 300A'},
    GENERATOR_5KVA:   {rate_per_day:115,  desc:'Gerador 5KVA'},
  },
  equipment_allocation: {
    IfcBeam:         {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcColumn:       {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcSlab:         {equipment:'CONCRETE_PUMP',    duration_factor:0.3},
    IfcDuct:         {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.4},
    IfcCableCarrier: {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.3},
  },

  // ── Column Headers ──
  h_discipline: 'Disciplina', h_ifc_class: 'Classe IFC', h_quantity: 'Quantidade',
  h_uom: 'Unidade', h_description: 'Descrição', h_storey: 'Pavimento',
  h_phase: 'Fase', h_item: 'Item',
  h_material: 'Material', h_labour: 'Mão de Obra', h_equipment: 'Equipamento',
  h_total: 'Total', h_unit_rate: 'Preço Unitário', h_mat_rate: 'Preço Material',
  h_lab_rate: 'Preço M.O.', h_rate_day: 'Diária', h_trade: 'Ofício',
  h_crew_size: 'Equipe', h_man_days: 'Homens-Dia', h_duration: 'Duração (dias)',
  h_grand_total: 'TOTAL GERAL', h_subtotal: 'SUBTOTAL',

  // ── 4D Headers ──
  h_wbs: 'EAP', h_task_name: 'Nome da Tarefa', h_productivity: 'Produtividade',
  h_gangs: 'Equipes', h_start_date: 'Data Início', h_finish_date: 'Data Término',
  h_labor_resource: 'Recurso de M.O.', h_status: 'Status',
  h_pct_complete: '% Concluído', h_day_offset: 'Defasagem (dias)', h_date: 'Data',
  h_milestone: 'Marco', h_task: 'Tarefa', h_task_count: 'Qtd Tarefas',
  h_total_days: 'Total de Dias', h_total_man_days: 'Total Homens-Dia',
  h_week: 'Semana', h_cumulative_pct: '% Acumulado',

  // ── Chart Titles ──
  t_cost_by_disc: '5D — Custo por Disciplina',
  t_cost_components: 'Composição de Custos por Disciplina',
  t_task_dist_phase: 'Distribuição de Tarefas por Fase',
  t_task_count_disc: 'Quantidade de Tarefas por Disciplina',
  t_phase_duration: '4D — Duração das Fases (Total de Dias)',
  t_resource_workload: '4D — Carga de Trabalho (Homens-Dia por Fase)',
  t_s_curve: '4D — Curva S de Progresso',
  t_milestone: '4D — Linha do Tempo dos Marcos',
  t_gantt: '4D — Gráfico de Gantt (Tarefas Estratégicas por Fase)',
  t_vo_impact: '5D — Impacto de Aditivos Contratuais',

  // ── Excel Sheet Names ──
  s_cover: 'Capa', s_exec_summary: 'Resumo Executivo',
  s_material: 'Resumo de Materiais', s_labour: 'Resumo de Mão de Obra',
  s_equipment: 'Resumo de Equipamentos', s_boq: 'Planilha Orçamentária Detalhada',
  s_prov: 'Verbas Provisórias', s_schedule: 'Cronograma',
  s_proj_summary: 'Resumo do Projeto', s_dashboard: 'Painel BIM 4D',

  // ── Section Titles ──
  t_comp_boq: 'PLANILHA ORÇAMENTÁRIA COMPLETA',
  t_cost_method: 'METODOLOGIA DE COMPOSIÇÃO DE CUSTOS',
  t_mat_summary: 'RESUMO DE CUSTOS DE MATERIAIS',
  t_lab_summary: 'RESUMO DE CUSTOS DE MÃO DE OBRA',
  t_equip_summary: 'RESUMO DE CUSTOS DE EQUIPAMENTOS',
  t_prov_sums: 'VERBAS PROVISÓRIAS',
  t_phase_dur_analysis: 'Análise de Duração das Fases',
  t_resource_analysis: 'Análise de Carga de Trabalho',
  t_s_curve_progress: 'Curva S de Progresso',
  t_milestone_timeline: 'Linha do Tempo dos Marcos',
  t_gantt_timeline: 'Gráfico de Gantt (Tarefas Estratégicas)',

  // ── Misc ──
  not_started: 'Não Iniciado',

  // ── UI ──
  tagline:           'BIM sem atrito. Dois BDs. Um navegador. Zero instalação.',
  ui_tools:          'Ferramentas',
  ui_storeys:        'Pavimentos',
  ui_disciplines:    'Disciplinas',
  ui_filter:         'Filtrar...',
  ui_all_storeys:    'Todos os Pavimentos',
  ui_cancel:         'Cancelar',
  ui_back:           'Voltar',
  ui_save:           'Salvar',
  ui_undo:           'Desfazer',
  ui_clear:          'Limpar',
  ui_clear_all:      'Limpar Tudo',
  ui_export_excel:   'Exportar Excel',
  ui_show_3d:        'Mostrar em 3D',
  ui_class:          'Classe',
  ui_name:           'Nome',
  ui_building:       'Edificação',
  ui_buildings:      'Edificações',
  ui_elements:       'Elementos',
  ui_done:           'Concluído',
  ui_variance:       'Variação',
  ui_initializing:   'Inicializando...',
  ui_walk_title:     'Modo Caminhada',
  ui_walk_stopped:   'Modo Caminhada parado.',
  ui_reading_file:   'Lendo arquivo...',
  ui_building_dbs:   'Construindo bancos de dados...',
  ui_imported:       '{n} elementos importados',
  ui_issues_title:   'Problemas',
  ui_settings:       'Configurações',
  ui_language:       'Idioma',
  ui_currency:       'Moeda',
  ui_reset_defaults: 'Restaurar Padrões',
  ui_apply:          'Aplicar',

  // ── Landing page ──
  landing_watch_demo:   'Assistir Demo',
  landing_about:        'Sobre',
  landing_drop_ifc:     'Solte arquivo IFC ou 3D aqui',
  landing_my_buildings: 'Meus Edif\u00edcios',
  landing_city:         'Edif\u00edcios da Cidade',
  landing_landmark:     'Edif\u00edcios Not\u00e1veis',
  landing_loading:      'Carregando manifesto de edif\u00edcios...',
  landing_created_by:   'Criado por',
  landing_live_stats:   'Estat\u00edsticas ao Vivo',
};
