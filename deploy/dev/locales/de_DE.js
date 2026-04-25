// de_DE.js — Deutsch (Deutschland) — German locale
// ISO 3166-1: DE — Flag: 🇩🇪
// Based on en_MY.js structure — rates from DIN 276 / BKI 2024
//
// _TRL_LOCALE overrides _TRL_DEFAULTS at runtime.
// Every key is shown here so you can see and edit the full set.

var _TRL_LOCALE = {

  // ── Identity ──
  iso: 'DE',            // ISO 3166-1 alpha-2 (drives flag emoji)
  lang: 'de',           // ISO 639-1 language
  locale: 'de_DE',      // combined
  source_app: 'BIM OOTB — Frictionless BIM',

  // ── Currency ──
  cur: 'EUR',
  cur2: 'USD',
  cur_rate: 0.93,         // 1 USD = 0.93 EUR
  cur_name: 'Euro',
  cur2_name: 'US Dollar',

  // ── Rate source attribution ──
  rate_source:       'DIN 276 / BKI Baukosteninformationszentrum 2024',
  rate_year:         '2024',
  rate_mat_source:   'BKI Baukosteninformationszentrum Objektdaten 2024',
  rate_mat_ref:      'BKI Baukosten Positionen Neubau 2024 (inkl. MwSt.)',
  rate_mat_includes: 'Lieferung frei Baustelle, Verschnitt (5-10% je Materialart)',
  rate_lab_source:   'Bau-Tarifvertrag (BRTV) West 2024',
  rate_lab_basis:    'Tariflohn + 80% (SV-Beitraege 21%, Zulagen, BG, Urlaub, 13. Monatseinkommen)',
  rate_lab_prod:     'BKI Leistungswerte nach Gewerk',
  rate_lab_crew:     'Facharbeiter + Helfer nach Tarifgruppen',
  rate_eq_source:    'Baugeraete-Liste BGL 2020 (fortgeschrieben 2024)',
  rate_eq_alloc:     'Zuordnung nach Arbeitsart und Einsatzdauer',
  rate_eq_basis:     'Pro Tag (8 Stunden), Bedienerkosten separat ausgewiesen',

  // ── Material Rates (BKI 2024, EUR) ──
  rates: {
    IfcDuct:{rate:52,unit:'M',desc:'Lueftungskanal verzinkt (Durchschn. 400mm)'},
    IfcDuctSegment:{rate:52,unit:'M',desc:'Kanalsegment'},
    IfcDuctFitting:{rate:115,unit:'EA',desc:'Kanalformteile (Boegen, T-Stuecke)'},
    IfcPipe:{rate:18,unit:'M',desc:'Rohr PP/PE (Durchschn. 100mm)'},
    IfcPipeSegment:{rate:18,unit:'M',desc:'Rohrsegment'},
    IfcPipeFitting:{rate:32,unit:'EA',desc:'Rohrformteile'},
    IfcCableCarrier:{rate:28,unit:'M',desc:'Kabelrinne (300mm)'},
    IfcCableCarrierSegment:{rate:28,unit:'M',desc:'Kabelrinnensegment'},
    IfcBeam:{rate:220,unit:'M',desc:'Stahltraeger IPE'},
    IfcColumn:{rate:385,unit:'M',desc:'Stahlstuetze HEB'},
    IfcSlab:{rate:95,unit:'M2',desc:'Stahlbetondecke 250mm'},
    IfcWall:{rate:68,unit:'M2',desc:'Mauerwerk KS 150mm'},
    IfcWallStandardCase:{rate:68,unit:'M2',desc:'Standardwand'},
    IfcCurtainWall:{rate:480,unit:'M2',desc:'Vorhangfassade'},
    IfcCovering:{rate:62,unit:'M2',desc:'Boden-/Deckenbelag'},
    IfcRoof:{rate:85,unit:'M2',desc:'Metalldach'},
    IfcLightFixture:{rate:165,unit:'EA',desc:'LED-Leuchte'},
    IfcOutlet:{rate:42,unit:'EA',desc:'Steckdose'},
    IfcDoor:{rate:1450,unit:'EA',desc:'Tuerelement komplett'},
    IfcWindow:{rate:680,unit:'EA',desc:'Fenster'},
    IfcBuildingElementProxy:{rate:280,unit:'EA',desc:'Sonstiges Bauteil'},
    IfcFlowTerminal:{rate:1150,unit:'EA',desc:'HLK-Endgeraet'},
    IfcFurnishingElement:{rate:420,unit:'EA',desc:'Einrichtungsgegenstand'},
    IfcFurniture:{rate:520,unit:'EA',desc:'Moebel'},
    IfcPlate:{rate:38,unit:'M2',desc:'Stahlblech'},
    IfcMember:{rate:110,unit:'M',desc:'Stahlprofil'},
    IfcRailing:{rate:145,unit:'M',desc:'Gelaender'},
    IfcStair:{rate:3800,unit:'EA',desc:'Treppe'},
    IfcStairFlight:{rate:1850,unit:'EA',desc:'Treppenlauf'},
    IfcFooting:{rate:185,unit:'EA',desc:'Fundament'},
    IfcPile:{rate:520,unit:'EA',desc:'Gruendungspfahl'},
    IfcReinforcingBar:{rate:1.85,unit:'KG',desc:'Betonstahl'},
    IfcFlowSegment:{rate:42,unit:'M',desc:'Leitungssegment'},
    IfcFlowFitting:{rate:68,unit:'EA',desc:'Leitungsformteil'},
    IfcFlowController:{rate:155,unit:'EA',desc:'Absperrorgan'},
    IfcEnergyConversionDevice:{rate:3200,unit:'EA',desc:'Energiewandler'},
    IfcFlowTreatmentDevice:{rate:420,unit:'EA',desc:'Aufbereitungsgeraet'},
    IfcFlowMovingDevice:{rate:1250,unit:'EA',desc:'Pumpe / Ventilator'},
    IfcFlowStorageDevice:{rate:1850,unit:'EA',desc:'Speichergeraet'},
    IfcElectricAppliance:{rate:165,unit:'EA',desc:'Elektrogeraet'},
  },
  rates_default: {rate:180,unit:'EA',desc:'Sonstiges Bauteil'},

  // ── Labor Rates (Bau-Tarifvertrag 2024, EUR) ──
  labor_rates: {
    HVAC_TECH:     {rate_per_day:420, crew_size:2, trade:'HLK-Techniker (Facharbeiter)',
                    productivity:{IfcDuct:14,IfcDuctSegment:14,IfcDuctFitting:10}},
    PLUMBER:       {rate_per_day:385, crew_size:2, trade:'Rohrleger / Anlagenmechaniker SHK',
                    productivity:{IfcPipe:20,IfcPipeSegment:20,IfcPipeFitting:12}},
    ELECTRICIAN:   {rate_per_day:395, crew_size:2, trade:'Elektriker (Facharbeiter)',
                    productivity:{IfcCableCarrier:25,IfcCableCarrierSegment:25,IfcLightFixture:16,IfcOutlet:20}},
    STEEL_ERECTOR: {rate_per_day:440, crew_size:4, trade:'Stahlbaumonteur (Facharbeiter)',
                    productivity:{IfcBeam:6,IfcColumn:5}},
    CONCRETE_GANG: {rate_per_day:360, crew_size:6, trade:'Betonbaukolonne (gemischt)',
                    productivity:{IfcSlab:30}},
    MASON:         {rate_per_day:375, crew_size:3, trade:'Maurer (Facharbeiter) + Helfer',
                    productivity:{IfcWall:10,IfcWallStandardCase:10}},
    LABORER:       {rate_per_day:220, crew_size:1, trade:'Bauhelfer',
                    productivity:{}},
  },

  // ── Equipment Rates (BGL 2024, EUR) ──
  equipment_rates: {
    MOBILE_CRANE_20T: {rate_per_day:850, desc:'Mobilkran 20 Tonnen'},
    TOWER_CRANE:      {rate_per_day:980, desc:'Turmdrehkran'},
    CONCRETE_PUMP:    {rate_per_day:520, desc:'Autobetonpumpe'},
    SCISSOR_LIFT_8M:  {rate_per_day:135, desc:'Scherenhubbühne 8m'},
    WELDING_MACHINE:  {rate_per_day:35,  desc:'Schweissgeraet 300A'},
    GENERATOR_5KVA:   {rate_per_day:45,  desc:'Stromerzeuger 5kVA'},
  },
  equipment_allocation: {
    IfcBeam:         {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcColumn:       {equipment:'MOBILE_CRANE_20T', duration_factor:0.5},
    IfcSlab:         {equipment:'CONCRETE_PUMP',    duration_factor:0.3},
    IfcDuct:         {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.4},
    IfcCableCarrier: {equipment:'SCISSOR_LIFT_8M',  duration_factor:0.3},
  },

  // ── Column Headers ──
  h_discipline: 'Gewerk', h_ifc_class: 'IFC-Klasse', h_quantity: 'Menge',
  h_uom: 'ME', h_description: 'Beschreibung', h_storey: 'Geschoss',
  h_phase: 'Phase', h_item: 'Position',
  h_material: 'Material', h_labour: 'Arbeit', h_equipment: 'Geraete',
  h_total: 'Gesamt', h_unit_rate: 'Einheitspreis', h_mat_rate: 'Materialpreis',
  h_lab_rate: 'Lohnpreis', h_rate_day: 'Tagessatz', h_trade: 'Gewerk/Beruf',
  h_crew_size: 'Kolonnenstaerke', h_man_days: 'Manntage', h_duration: 'Dauer (Tage)',
  h_grand_total: 'GESAMTSUMME', h_subtotal: 'ZWISCHENSUMME',

  // ── 4D Headers ──
  h_wbs: 'PSP', h_task_name: 'Vorgangsname', h_productivity: 'Leistung',
  h_gangs: 'Kolonnen', h_start_date: 'Anfangsdatum', h_finish_date: 'Enddatum',
  h_labor_resource: 'Arbeitskraft', h_status: 'Status',
  h_pct_complete: '% Fertigstellung', h_day_offset: 'Tagesversatz', h_date: 'Datum',
  h_milestone: 'Meilenstein', h_task: 'Vorgang', h_task_count: 'Vorgangsanzahl',
  h_total_days: 'Gesamttage', h_total_man_days: 'Gesamt-Manntage',
  h_week: 'Woche', h_cumulative_pct: 'Kumuliert %',

  // ── Chart Titles ──
  t_cost_by_disc: '5D — Kosten nach Gewerk',
  t_cost_components: 'Kostenbestandteile nach Gewerk',
  t_task_dist_phase: 'Vorgangsverteilung nach Phase',
  t_task_count_disc: 'Vorgangsanzahl nach Gewerk',
  t_phase_duration: '4D — Phasendauer (Gesamttage)',
  t_resource_workload: '4D — Ressourcenauslastung (Manntage nach Phase)',
  t_s_curve: '4D — S-Kurve Fortschritt',
  t_milestone: '4D — Meilensteinplan',
  t_gantt: '4D — Balkenplan (Strategische Vorgaenge nach Phase)',
  t_vo_impact: '5D — Nachtragswirkung',

  // ── Excel Sheet Names ──
  s_cover: 'Deckblatt', s_exec_summary: 'Zusammenfassung',
  s_material: 'Materialuebersicht', s_labour: 'Lohnuebersicht',
  s_equipment: 'Geraeteuebersicht', s_boq: 'Detailliertes LV',
  s_prov: 'Eventualkosten', s_schedule: 'Bauzeitenplan',
  s_proj_summary: 'Projektuebersicht', s_dashboard: 'BIM 4D Dashboard',

  // ── Section Titles ──
  t_comp_boq: 'LEISTUNGSVERZEICHNIS',
  t_cost_method: 'KOSTENAUFGLIEDERUNG — METHODIK',
  t_mat_summary: 'MATERIALKOSTEN — ZUSAMMENFASSUNG',
  t_lab_summary: 'LOHNKOSTEN — ZUSAMMENFASSUNG',
  t_equip_summary: 'GERAETEKOSTEN — ZUSAMMENFASSUNG',
  t_prov_sums: 'EVENTUALKOSTEN',
  t_phase_dur_analysis: 'Phasendaueranalyse',
  t_resource_analysis: 'Ressourcenauslastungsanalyse',
  t_s_curve_progress: 'S-Kurve Fortschritt',
  t_milestone_timeline: 'Meilensteinplan',
  t_gantt_timeline: 'Balkenplan (Strategische Vorgaenge)',

  // ── Misc ──
  not_started: 'Nicht begonnen',
};
