// Universal Acoustic Code Dictionary — shared by all endpoints
// 3 hex digits = 4096 codes, organised by category prefix
// Checksum = 4th hex digit (XOR of first 3 nibbles & 0xF)
//
// Format: CODE → { short, detail, severity, category }
// Severity: 0=info, 1=low, 2=medium, 3=high, 4=critical
//
// CATEGORY PREFIXES:
//   0xx = System / protocol
//   1xx = Person status (self-report)
//   2xx = Person status (observed by rescuer)
//   3xx = Medical conditions
//   4xx = Environmental hazards
//   5xx = Structural conditions
//   6xx = Resource requests
//   7xx = Navigation / location
//   8xx = Team coordination
//   9xx = Confirmation / ACK
//   Axx = Water/maritime specific
//   Bxx = Fire specific
//   Cxx = Chemical/HAZMAT
//   Dxx = Infrastructure
//   Exx = Civilian/evacuee
//   Fxx = Reserved / custom

const SAR_CODES = {

    // === 0xx: SYSTEM ===
    '000': { short: 'NULL',           detail: 'Empty / test transmission',     severity: 0, category: 'System' },
    '001': { short: 'ACK',            detail: 'Message received',              severity: 0, category: 'System' },
    '002': { short: 'NAK',            detail: 'Message not understood',        severity: 0, category: 'System' },
    '003': { short: 'REPEAT',         detail: 'Please retransmit',            severity: 0, category: 'System' },
    '004': { short: 'STANDBY',        detail: 'Standby, preparing response',  severity: 0, category: 'System' },
    '005': { short: 'ROGER',          detail: 'Understood, will comply',       severity: 0, category: 'System' },
    '006': { short: 'NEGATIVE',       detail: 'Cannot comply',                severity: 1, category: 'System' },
    '007': { short: 'MAYDAY',         detail: 'Distress — all stations',      severity: 4, category: 'System' },
    '008': { short: 'PAN-PAN',        detail: 'Urgent — not life-threatening', severity: 3, category: 'System' },
    '009': { short: 'SECURITE',       detail: 'Safety announcement',          severity: 2, category: 'System' },
    '00A': { short: 'RELAY',          detail: 'Relay this message onward',    severity: 1, category: 'System' },
    '00B': { short: 'CANCEL',         detail: 'Cancel previous message',      severity: 0, category: 'System' },
    '00C': { short: 'ALL CLEAR',      detail: 'Situation resolved',           severity: 0, category: 'System' },
    '00D': { short: 'COMMS CHECK',    detail: 'Testing communication link',   severity: 0, category: 'System' },
    '00E': { short: 'TIME SYNC',      detail: 'Synchronise clocks',           severity: 0, category: 'System' },
    '00F': { short: 'END SESSION',    detail: 'Closing communication',        severity: 0, category: 'System' },

    // === 1xx: SELF-REPORT STATUS ===
    '100': { short: 'SOS',                detail: 'Life-threatening emergency',          severity: 4, category: 'Self' },
    '101': { short: 'TRAPPED IMMOBILE',   detail: 'Trapped, cannot move',               severity: 4, category: 'Self' },
    '102': { short: 'TRAPPED MOBILE',     detail: 'Trapped but can move locally',        severity: 3, category: 'Self' },
    '103': { short: 'TRAPPED STABLE',     detail: 'Trapped, stable, can wait',           severity: 2, category: 'Self' },
    '104': { short: 'OK TRAPPED',         detail: 'No injuries, need extraction',        severity: 1, category: 'Self' },
    '105': { short: 'OK FREE',            detail: 'Uninjured, moving freely',            severity: 0, category: 'Self' },
    '106': { short: 'INJURED MINOR',      detail: 'Minor injuries, ambulatory',          severity: 2, category: 'Self' },
    '107': { short: 'INJURED SERIOUS',    detail: 'Serious injuries, need medical',      severity: 4, category: 'Self' },
    '108': { short: 'UNCONSCIOUS NEARBY', detail: 'Unconscious person near me',          severity: 4, category: 'Self' },
    '109': { short: 'MULTIPLE TRAPPED',   detail: 'Multiple people trapped here',        severity: 4, category: 'Self' },
    '10A': { short: 'CHILDREN PRESENT',   detail: 'Children in this location',           severity: 3, category: 'Self' },
    '10B': { short: 'ELDERLY PRESENT',    detail: 'Elderly persons here',                severity: 3, category: 'Self' },
    '10C': { short: 'DISABLED PRESENT',   detail: 'Disabled persons need assistance',    severity: 3, category: 'Self' },
    '10D': { short: 'PREGNANT',           detail: 'Pregnant person needs help',           severity: 3, category: 'Self' },
    '10E': { short: 'LOSING CONSCIOUSNESS', detail: 'Fading in and out',                 severity: 4, category: 'Self' },
    '10F': { short: 'AIR RUNNING LOW',    detail: 'Limited breathable air',              severity: 4, category: 'Self' },

    // === 2xx: OBSERVED STATUS (by rescuer) ===
    '200': { short: 'SURVIVOR FOUND',     detail: 'Located a survivor',                  severity: 3, category: 'Observed' },
    '201': { short: 'SURVIVOR CONSCIOUS', detail: 'Survivor is conscious',               severity: 2, category: 'Observed' },
    '202': { short: 'SURVIVOR UNCONSCIOUS', detail: 'Survivor unconscious',              severity: 4, category: 'Observed' },
    '203': { short: 'BODY FOUND',         detail: 'Deceased person found',               severity: 3, category: 'Observed' },
    '204': { short: 'VOICE HEARD',        detail: 'Heard voice/tapping',                 severity: 3, category: 'Observed' },
    '205': { short: 'SIGNAL DETECTED',    detail: 'Detected electronic signal',          severity: 2, category: 'Observed' },
    '206': { short: 'DOG ALERT',          detail: 'Search dog indicated',                severity: 3, category: 'Observed' },
    '207': { short: 'MULTIPLE SURVIVORS', detail: 'Multiple survivors at location',      severity: 3, category: 'Observed' },
    '208': { short: 'SURVIVOR EXTRACTED', detail: 'Successfully extracted survivor',     severity: 0, category: 'Observed' },
    '209': { short: 'AREA CLEARED',       detail: 'No more survivors in area',           severity: 0, category: 'Observed' },
    '20A': { short: 'SURVIVOR MOBILE',    detail: 'Survivor can walk',                   severity: 1, category: 'Observed' },
    '20B': { short: 'NEEDS STRETCHER',    detail: 'Survivor needs stretcher',            severity: 3, category: 'Observed' },

    // === 3xx: MEDICAL ===
    '300': { short: 'BLEEDING SEVERE',    detail: 'Severe uncontrolled bleeding',        severity: 4, category: 'Medical' },
    '301': { short: 'FRACTURE',           detail: 'Suspected bone fracture',             severity: 3, category: 'Medical' },
    '302': { short: 'CRUSH INJURY',       detail: 'Crush injury / compartment risk',     severity: 4, category: 'Medical' },
    '303': { short: 'BURNS',              detail: 'Burn injuries',                       severity: 3, category: 'Medical' },
    '304': { short: 'BREATHING DIFFICULTY', detail: 'Respiratory distress',              severity: 4, category: 'Medical' },
    '305': { short: 'CHEST PAIN',         detail: 'Cardiac symptoms',                    severity: 4, category: 'Medical' },
    '306': { short: 'HEAD INJURY',        detail: 'Head/spinal injury suspected',        severity: 4, category: 'Medical' },
    '307': { short: 'HYPOTHERMIA',        detail: 'Hypothermia / exposure',              severity: 3, category: 'Medical' },
    '308': { short: 'DEHYDRATION',        detail: 'Severe dehydration',                  severity: 3, category: 'Medical' },
    '309': { short: 'ALLERGIC REACTION',  detail: 'Anaphylaxis / severe allergy',        severity: 4, category: 'Medical' },
    '30A': { short: 'SEIZURE',            detail: 'Active seizure',                      severity: 4, category: 'Medical' },
    '30B': { short: 'DIABETIC EMERGENCY', detail: 'Diabetic crisis',                     severity: 4, category: 'Medical' },
    '30C': { short: 'DROWNING',           detail: 'Near-drowning / water inhalation',    severity: 4, category: 'Medical' },
    '30D': { short: 'SHOCK',              detail: 'Patient in shock',                    severity: 4, category: 'Medical' },
    '30E': { short: 'AMPUTATION',         detail: 'Traumatic amputation',                severity: 4, category: 'Medical' },
    '30F': { short: 'STABLE VITALS',      detail: 'Vitals stable, monitoring',           severity: 1, category: 'Medical' },

    // === 4xx: ENVIRONMENTAL HAZARDS ===
    '400': { short: 'FIRE ACTIVE',        detail: 'Active fire at location',             severity: 4, category: 'Hazard' },
    '401': { short: 'SMOKE DENSE',        detail: 'Dense smoke, low visibility',         severity: 3, category: 'Hazard' },
    '402': { short: 'GAS LEAK',           detail: 'Gas leak detected',                   severity: 4, category: 'Hazard' },
    '403': { short: 'FLOODING',           detail: 'Water rising at location',            severity: 3, category: 'Hazard' },
    '404': { short: 'AFTERSHOCK',         detail: 'Aftershock / tremor felt',            severity: 3, category: 'Hazard' },
    '405': { short: 'UNSTABLE GROUND',    detail: 'Ground unstable / sinkhole risk',     severity: 3, category: 'Hazard' },
    '406': { short: 'LIVE WIRES',         detail: 'Exposed electrical wires',            severity: 4, category: 'Hazard' },
    '407': { short: 'TOXIC AIR',          detail: 'Toxic fumes / bad air quality',       severity: 4, category: 'Hazard' },
    '408': { short: 'LANDSLIDE RISK',     detail: 'Landslide / rockfall imminent',       severity: 3, category: 'Hazard' },
    '409': { short: 'EXTREME COLD',       detail: 'Extreme cold conditions',             severity: 2, category: 'Hazard' },
    '40A': { short: 'EXTREME HEAT',       detail: 'Extreme heat conditions',             severity: 2, category: 'Hazard' },
    '40B': { short: 'RADIATION',          detail: 'Radiation hazard detected',           severity: 4, category: 'Hazard' },
    '40C': { short: 'HAZARD CLEARED',     detail: 'Previously reported hazard resolved', severity: 0, category: 'Hazard' },

    // === 5xx: STRUCTURAL ===
    '500': { short: 'COLLAPSE IMMINENT',  detail: 'Structure about to collapse',         severity: 4, category: 'Structure' },
    '501': { short: 'PARTIAL COLLAPSE',   detail: 'Partial structural collapse',         severity: 3, category: 'Structure' },
    '502': { short: 'TOTAL COLLAPSE',     detail: 'Total structural collapse',           severity: 4, category: 'Structure' },
    '503': { short: 'VOID FOUND',         detail: 'Survivable void space found',         severity: 2, category: 'Structure' },
    '504': { short: 'ACCESS BLOCKED',     detail: 'Route blocked / impassable',          severity: 2, category: 'Structure' },
    '505': { short: 'ACCESS OPEN',        detail: 'Route open / passable',               severity: 0, category: 'Structure' },
    '506': { short: 'SHORING NEEDED',     detail: 'Needs structural shoring',            severity: 3, category: 'Structure' },
    '507': { short: 'STAIRS INTACT',      detail: 'Stairwell usable',                    severity: 0, category: 'Structure' },
    '508': { short: 'ELEVATOR STUCK',     detail: 'Persons trapped in elevator',         severity: 3, category: 'Structure' },
    '509': { short: 'FLOOR WEAK',         detail: 'Floor structurally compromised',      severity: 3, category: 'Structure' },

    // === 6xx: RESOURCE REQUESTS ===
    '600': { short: 'NEED MEDICAL',       detail: 'Medical team needed',                 severity: 3, category: 'Resource' },
    '601': { short: 'NEED WATER',         detail: 'Drinking water needed',               severity: 2, category: 'Resource' },
    '602': { short: 'NEED STRETCHER',     detail: 'Stretcher / spine board needed',      severity: 3, category: 'Resource' },
    '603': { short: 'NEED CUTTING TOOL',  detail: 'Heavy cutting equipment needed',      severity: 2, category: 'Resource' },
    '604': { short: 'NEED LIFTING EQUIP', detail: 'Crane / jack / airbag needed',        severity: 3, category: 'Resource' },
    '605': { short: 'NEED LIGHT',         detail: 'Lighting equipment needed',           severity: 1, category: 'Resource' },
    '606': { short: 'NEED AIR SUPPLY',    detail: 'Breathing apparatus needed',          severity: 4, category: 'Resource' },
    '607': { short: 'NEED ROPE/HARNESS',  detail: 'Rope rescue equipment needed',        severity: 2, category: 'Resource' },
    '608': { short: 'NEED BOAT',          detail: 'Water rescue craft needed',           severity: 3, category: 'Resource' },
    '609': { short: 'NEED HELICOPTER',    detail: 'Air evacuation needed',               severity: 4, category: 'Resource' },
    '60A': { short: 'NEED FOOD',          detail: 'Food supplies needed',                severity: 1, category: 'Resource' },
    '60B': { short: 'NEED BLANKETS',      detail: 'Thermal protection needed',           severity: 2, category: 'Resource' },
    '60C': { short: 'NEED COMMS RELAY',   detail: 'Communication relay needed',          severity: 2, category: 'Resource' },

    // === 7xx: NAVIGATION / LOCATION ===
    '700': { short: 'I AM HERE',          detail: 'Marking my position',                 severity: 1, category: 'Location' },
    '701': { short: 'MOVE NORTH',         detail: 'Proceed north',                       severity: 0, category: 'Location' },
    '702': { short: 'MOVE SOUTH',         detail: 'Proceed south',                       severity: 0, category: 'Location' },
    '703': { short: 'MOVE EAST',          detail: 'Proceed east',                        severity: 0, category: 'Location' },
    '704': { short: 'MOVE WEST',          detail: 'Proceed west',                        severity: 0, category: 'Location' },
    '705': { short: 'GO UP',             detail: 'Proceed to higher floor/ground',       severity: 0, category: 'Location' },
    '706': { short: 'GO DOWN',           detail: 'Proceed to lower floor/ground',        severity: 0, category: 'Location' },
    '707': { short: 'RALLY POINT',        detail: 'Gathering point here',                severity: 0, category: 'Location' },
    '708': { short: 'LANDING ZONE',       detail: 'Helicopter landing zone here',        severity: 1, category: 'Location' },
    '709': { short: 'TRIAGE POINT',       detail: 'Triage station here',                 severity: 1, category: 'Location' },
    '70A': { short: 'SAFE ZONE',          detail: 'Area confirmed safe',                 severity: 0, category: 'Location' },
    '70B': { short: 'DANGER ZONE',        detail: 'Do not enter this area',              severity: 3, category: 'Location' },
    '70C': { short: 'PERIMETER SET',      detail: 'Safety perimeter established',        severity: 0, category: 'Location' },

    // === 8xx: TEAM COORDINATION ===
    '800': { short: 'SEND HELP',          detail: 'Requesting team to position',         severity: 3, category: 'Team' },
    '801': { short: 'TEAM EN ROUTE',      detail: 'Team dispatched to you',              severity: 1, category: 'Team' },
    '802': { short: 'TEAM ON SCENE',      detail: 'Team has arrived',                    severity: 0, category: 'Team' },
    '803': { short: 'TEAM WITHDRAWING',   detail: 'Team pulling back',                   severity: 2, category: 'Team' },
    '804': { short: 'SHIFT CHANGE',       detail: 'Crew rotation in progress',           severity: 0, category: 'Team' },
    '805': { short: 'OPERATION PAUSED',   detail: 'Operations temporarily halted',       severity: 1, category: 'Team' },
    '806': { short: 'OPERATION RESUMED',  detail: 'Operations continuing',               severity: 0, category: 'Team' },
    '807': { short: 'NEED BACKUP',        detail: 'Additional personnel needed',         severity: 2, category: 'Team' },
    '808': { short: 'BUDDY CHECK',        detail: 'Confirm team member status',          severity: 1, category: 'Team' },
    '809': { short: 'ETA 5MIN',           detail: 'Arriving in ~5 minutes',              severity: 0, category: 'Team' },
    '80A': { short: 'ETA 15MIN',          detail: 'Arriving in ~15 minutes',             severity: 0, category: 'Team' },
    '80B': { short: 'ETA 30MIN',          detail: 'Arriving in ~30 minutes',             severity: 0, category: 'Team' },
    '80C': { short: 'ETA 1HR',            detail: 'Arriving in ~1 hour',                 severity: 0, category: 'Team' },

    // === 9xx: CONFIRMATIONS ===
    '900': { short: 'CONFIRM ALIVE',      detail: 'Confirming I am alive',               severity: 2, category: 'Confirm' },
    '901': { short: 'CONFIRM RECEIVED',   detail: 'Help has reached me',                 severity: 0, category: 'Confirm' },
    '902': { short: 'CONFIRM EXTRACTED',  detail: 'Successfully extracted',              severity: 0, category: 'Confirm' },
    '903': { short: 'CONFIRM SAFE',       detail: 'Confirmed safe location',             severity: 0, category: 'Confirm' },
    '904': { short: 'SITUATION WORSE',    detail: 'Condition deteriorating',             severity: 4, category: 'Confirm' },
    '905': { short: 'SITUATION BETTER',   detail: 'Condition improving',                 severity: 1, category: 'Confirm' },
    '906': { short: 'STILL WAITING',      detail: 'No change, still waiting',            severity: 2, category: 'Confirm' },

    // === Axx: WATER / MARITIME ===
    'A00': { short: 'MAN OVERBOARD',      detail: 'Person in water',                     severity: 4, category: 'Maritime' },
    'A01': { short: 'VESSEL SINKING',     detail: 'Vessel taking on water',              severity: 4, category: 'Maritime' },
    'A02': { short: 'ADRIFT',             detail: 'Adrift, no propulsion',               severity: 3, category: 'Maritime' },
    'A03': { short: 'DIVER DOWN',         detail: 'Diver in water at location',          severity: 2, category: 'Maritime' },
    'A04': { short: 'DIVER OK',           detail: 'Diver confirms OK',                   severity: 0, category: 'Maritime' },
    'A05': { short: 'DIVER DISTRESS',     detail: 'Diver in trouble',                    severity: 4, category: 'Maritime' },
    'A06': { short: 'CURRENT STRONG',     detail: 'Dangerous currents',                  severity: 3, category: 'Maritime' },
    'A07': { short: 'VISIBILITY ZERO',    detail: 'Zero underwater visibility',           severity: 2, category: 'Maritime' },
    'A08': { short: 'AIR LOW',            detail: 'Dive air supply low',                 severity: 4, category: 'Maritime' },
    'A09': { short: 'SURFACING',          detail: 'Coming to surface',                   severity: 0, category: 'Maritime' },
    'A0A': { short: 'OBJECT FOUND',       detail: 'Located search object underwater',    severity: 2, category: 'Maritime' },

    // === Bxx: FIRE ===
    'B00': { short: 'FIRE SPREADING',     detail: 'Fire actively spreading',             severity: 4, category: 'Fire' },
    'B01': { short: 'FIRE CONTAINED',     detail: 'Fire contained to area',              severity: 2, category: 'Fire' },
    'B02': { short: 'FIRE OUT',           detail: 'Fire extinguished',                   severity: 0, category: 'Fire' },
    'B03': { short: 'FLASHOVER RISK',     detail: 'Flashover conditions developing',     severity: 4, category: 'Fire' },
    'B04': { short: 'BACKDRAFT RISK',     detail: 'Backdraft conditions present',        severity: 4, category: 'Fire' },
    'B05': { short: 'VENTILATE',          detail: 'Ventilation needed',                  severity: 2, category: 'Fire' },
    'B06': { short: 'EVACUATE NOW',       detail: 'Immediate evacuation of building',    severity: 4, category: 'Fire' },

    // === Cxx: CHEMICAL / HAZMAT ===
    'C00': { short: 'HAZMAT PRESENT',     detail: 'Hazardous materials detected',        severity: 4, category: 'HAZMAT' },
    'C01': { short: 'CHEMICAL SPILL',     detail: 'Chemical spill at location',          severity: 4, category: 'HAZMAT' },
    'C02': { short: 'DECONTAM NEEDED',    detail: 'Decontamination required',            severity: 3, category: 'HAZMAT' },
    'C03': { short: 'BIOLOGICAL RISK',    detail: 'Biological hazard present',           severity: 4, category: 'HAZMAT' },
    'C04': { short: 'ASBESTOS',           detail: 'Asbestos exposure risk',              severity: 3, category: 'HAZMAT' },
    'C05': { short: 'CO DETECTED',        detail: 'Carbon monoxide detected',            severity: 4, category: 'HAZMAT' },

    // === Dxx: INFRASTRUCTURE ===
    'D00': { short: 'POWER OUT',          detail: 'No electrical power',                 severity: 1, category: 'Infra' },
    'D01': { short: 'WATER SUPPLY CUT',   detail: 'No water supply',                    severity: 2, category: 'Infra' },
    'D02': { short: 'ROAD BLOCKED',       detail: 'Road impassable',                     severity: 2, category: 'Infra' },
    'D03': { short: 'BRIDGE DAMAGED',     detail: 'Bridge compromised',                  severity: 3, category: 'Infra' },
    'D04': { short: 'COMMS DOWN',         detail: 'All normal communications down',      severity: 2, category: 'Infra' },
    'D05': { short: 'GENERATOR RUNNING',  detail: 'Backup power available',              severity: 0, category: 'Infra' },

    // === Exx: CIVILIAN / EVACUEE ===
    'E00': { short: 'EVACUATING',         detail: 'Civilians evacuating area',           severity: 1, category: 'Civilian' },
    'E01': { short: 'SHELTER IN PLACE',   detail: 'Staying in secured location',         severity: 1, category: 'Civilian' },
    'E02': { short: 'HEAD COUNT',         detail: 'Personnel count at location',         severity: 0, category: 'Civilian' },
    'E03': { short: 'MISSING PERSON',     detail: 'Person unaccounted for',              severity: 3, category: 'Civilian' },
    'E04': { short: 'ALL ACCOUNTED',      detail: 'All persons accounted for',           severity: 0, category: 'Civilian' },
    'E05': { short: 'PET/ANIMAL TRAPPED', detail: 'Animal needs rescue',                severity: 1, category: 'Civilian' },

    // ===================================================================
    // Fxx: DAILY LIFE — Social, Office, Logistics, Fun
    // ===================================================================

    // === F0x: GREETINGS & SOCIAL ===
    'F00': { short: 'HELLO',          detail: 'General greeting',                   severity: 0, category: 'Social' },
    'F01': { short: 'BYE',            detail: 'Goodbye / signing off',              severity: 0, category: 'Social' },
    'F02': { short: 'THANKS',         detail: 'Thank you',                          severity: 0, category: 'Social' },
    'F03': { short: 'SORRY',          detail: 'Apology',                            severity: 0, category: 'Social' },
    'F04': { short: 'PLEASE',         detail: 'Polite request prefix',              severity: 0, category: 'Social' },
    'F05': { short: 'WELCOME',        detail: 'You\'re welcome',                    severity: 0, category: 'Social' },
    'F06': { short: 'CONGRATS',       detail: 'Congratulations!',                   severity: 0, category: 'Social' },
    'F07': { short: 'GOOD MORNING',   detail: 'Morning greeting',                   severity: 0, category: 'Social' },
    'F08': { short: 'GOOD NIGHT',     detail: 'Night / end of day',                 severity: 0, category: 'Social' },
    'F09': { short: 'MISS YOU',       detail: 'Thinking of you',                    severity: 0, category: 'Social' },
    'F0A': { short: 'WELL DONE',      detail: 'Great job',                          severity: 0, category: 'Social' },
    'F0B': { short: 'TAKE CARE',      detail: 'Be safe / take care',               severity: 0, category: 'Social' },
    'F0C': { short: 'SEE YOU',        detail: 'See you later',                      severity: 0, category: 'Social' },
    'F0D': { short: 'HAPPY BIRTHDAY', detail: 'Birthday greeting',                  severity: 0, category: 'Social' },
    'F0E': { short: 'I LOVE YOU',     detail: 'Expression of love',                 severity: 0, category: 'Social' },
    'F0F': { short: 'HUG',            detail: 'Sending a virtual hug',              severity: 0, category: 'Social' },

    // === F1x: QUICK RESPONSES ===
    'F10': { short: 'YES',            detail: 'Affirmative',                        severity: 0, category: 'Response' },
    'F11': { short: 'NO',             detail: 'Negative',                           severity: 0, category: 'Response' },
    'F12': { short: 'MAYBE',          detail: 'Uncertain / considering',            severity: 0, category: 'Response' },
    'F13': { short: 'LATER',          detail: 'Not now, will do later',             severity: 0, category: 'Response' },
    'F14': { short: 'OK',             detail: 'Acknowledged / fine',                severity: 0, category: 'Response' },
    'F15': { short: 'ON IT',          detail: 'Working on it now',                  severity: 0, category: 'Response' },
    'F16': { short: 'NOT SURE',       detail: 'Need to check',                      severity: 0, category: 'Response' },
    'F17': { short: 'AGREE',          detail: 'I agree with you',                   severity: 0, category: 'Response' },
    'F18': { short: 'DISAGREE',       detail: 'I don\'t agree',                     severity: 1, category: 'Response' },
    'F19': { short: 'BUSY',           detail: 'Busy right now',                     severity: 1, category: 'Response' },
    'F1A': { short: 'FREE',           detail: 'Available now',                      severity: 0, category: 'Response' },
    'F1B': { short: 'DONE',           detail: 'Task completed',                     severity: 0, category: 'Response' },
    'F1C': { short: 'WAIT',           detail: 'Hold on / wait',                     severity: 1, category: 'Response' },
    'F1D': { short: 'COMING',         detail: 'On my way',                          severity: 0, category: 'Response' },
    'F1E': { short: 'UNDERSTOOD',     detail: 'Got it, clear',                      severity: 0, category: 'Response' },
    'F1F': { short: 'SAY AGAIN',      detail: 'Didn\'t catch that',                 severity: 0, category: 'Response' },

    // === F2x: OFFICE & WORK ===
    'F20': { short: 'MEETING NOW',    detail: 'Meeting starting / in meeting',      severity: 1, category: 'Office' },
    'F21': { short: 'MEETING OVER',   detail: 'Meeting ended',                      severity: 0, category: 'Office' },
    'F22': { short: 'RUNNING LATE',   detail: 'Going to be late',                   severity: 1, category: 'Office' },
    'F23': { short: 'ON BREAK',       detail: 'Taking a break',                     severity: 0, category: 'Office' },
    'F24': { short: 'BACK AT DESK',   detail: 'Returned to work',                   severity: 0, category: 'Office' },
    'F25': { short: 'REPORT READY',   detail: 'Report / document ready',            severity: 0, category: 'Office' },
    'F26': { short: 'NEED APPROVAL',  detail: 'Waiting for sign-off',               severity: 1, category: 'Office' },
    'F27': { short: 'APPROVED',       detail: 'Approved / signed off',              severity: 0, category: 'Office' },
    'F28': { short: 'REJECTED',       detail: 'Not approved',                       severity: 2, category: 'Office' },
    'F29': { short: 'DEADLINE',       detail: 'Deadline reminder',                  severity: 2, category: 'Office' },
    'F2A': { short: 'URGENT TASK',    detail: 'Priority task assigned',             severity: 3, category: 'Office' },
    'F2B': { short: 'CHECK EMAIL',    detail: 'Important email sent',               severity: 1, category: 'Office' },
    'F2C': { short: 'CALL ME',        detail: 'Please call when free',              severity: 1, category: 'Office' },
    'F2D': { short: 'DO NOT DISTURB', detail: 'In deep focus / DND',               severity: 1, category: 'Office' },
    'F2E': { short: 'LEAVING OFFICE', detail: 'Heading out',                        severity: 0, category: 'Office' },
    'F2F': { short: 'WORKING REMOTE', detail: 'Working from home today',            severity: 0, category: 'Office' },

    // === F3x: FOOD & SOCIAL PLANS ===
    'F30': { short: 'COFFEE?',        detail: 'Want to grab coffee?',               severity: 0, category: 'Food' },
    'F31': { short: 'LUNCH?',         detail: 'Lunch together?',                    severity: 0, category: 'Food' },
    'F32': { short: 'DINNER?',        detail: 'Dinner plans?',                      severity: 0, category: 'Food' },
    'F33': { short: 'YOUR TURN',      detail: 'Your turn to pay/order',             severity: 0, category: 'Food' },
    'F34': { short: 'MY TREAT',       detail: 'I\'m paying',                        severity: 0, category: 'Food' },
    'F35': { short: 'HUNGRY',         detail: 'Need food',                          severity: 1, category: 'Food' },
    'F36': { short: 'DRINKS?',        detail: 'Going for drinks?',                  severity: 0, category: 'Food' },
    'F37': { short: 'ORDER PLACED',   detail: 'Food ordered',                       severity: 0, category: 'Food' },
    'F38': { short: 'FOOD HERE',      detail: 'Food has arrived',                   severity: 0, category: 'Food' },
    'F39': { short: 'LETS GO',        detail: 'Time to head out',                   severity: 0, category: 'Food' },

    // === F4x: LOGISTICS & LOCATION ===
    'F40': { short: 'ARRIVING',       detail: 'Almost there',                       severity: 0, category: 'Logistics' },
    'F41': { short: 'ARRIVED',        detail: 'I\'m here',                          severity: 0, category: 'Logistics' },
    'F42': { short: 'LEAVING NOW',    detail: 'Departing now',                      severity: 0, category: 'Logistics' },
    'F43': { short: 'ETA 5 MIN',      detail: 'About 5 minutes away',              severity: 0, category: 'Logistics' },
    'F44': { short: 'ETA 15 MIN',     detail: 'About 15 minutes away',             severity: 0, category: 'Logistics' },
    'F45': { short: 'ETA 30 MIN',     detail: 'About 30 minutes away',             severity: 0, category: 'Logistics' },
    'F46': { short: 'STUCK TRAFFIC',  detail: 'Stuck in traffic',                   severity: 1, category: 'Logistics' },
    'F47': { short: 'WHERE ARE YOU?', detail: 'Requesting location',                severity: 1, category: 'Logistics' },
    'F48': { short: 'AT HOME',        detail: 'I\'m at home',                       severity: 0, category: 'Logistics' },
    'F49': { short: 'AT OFFICE',      detail: 'I\'m at the office',                 severity: 0, category: 'Logistics' },
    'F4A': { short: 'AT SCHOOL',      detail: 'I\'m at school',                     severity: 0, category: 'Logistics' },
    'F4B': { short: 'PARKING',        detail: 'Looking for parking',                severity: 0, category: 'Logistics' },
    'F4C': { short: 'PICK ME UP',     detail: 'Need a ride',                        severity: 1, category: 'Logistics' },
    'F4D': { short: 'DROPPED OFF',    detail: 'Dropped off successfully',           severity: 0, category: 'Logistics' },
    'F4E': { short: 'GATE OPEN',      detail: 'Gate/door is open',                  severity: 0, category: 'Logistics' },
    'F4F': { short: 'GATE CLOSED',    detail: 'Gate/door is locked',                severity: 0, category: 'Logistics' },

    // === F5x: STATUS & FEELINGS ===
    'F50': { short: 'HAPPY',          detail: 'Feeling good',                       severity: 0, category: 'Status' },
    'F51': { short: 'STRESSED',       detail: 'Under pressure',                     severity: 1, category: 'Status' },
    'F52': { short: 'TIRED',          detail: 'Exhausted / need rest',              severity: 1, category: 'Status' },
    'F53': { short: 'EXCITED',        detail: 'Great news / pumped',                severity: 0, category: 'Status' },
    'F54': { short: 'WORRIED',        detail: 'Something concerning',               severity: 1, category: 'Status' },
    'F55': { short: 'ANGRY',          detail: 'Frustrated / upset',                 severity: 2, category: 'Status' },
    'F56': { short: 'BORED',          detail: 'Nothing happening',                  severity: 0, category: 'Status' },
    'F57': { short: 'SICK',           detail: 'Not feeling well',                   severity: 2, category: 'Status' },
    'F58': { short: 'ALL GOOD',       detail: 'Everything fine, no worries',        severity: 0, category: 'Status' },
    'F59': { short: 'NEED SPACE',     detail: 'Want to be alone',                   severity: 1, category: 'Status' },
    'F5A': { short: 'CELEBRATING',    detail: 'Party / celebration happening',      severity: 0, category: 'Status' },
    'F5B': { short: 'SLEEPING',       detail: 'Do not wake me',                     severity: 0, category: 'Status' },
    'F5C': { short: 'EXERCISING',     detail: 'Working out',                        severity: 0, category: 'Status' },

    // === F6x: HOME & FAMILY ===
    'F60': { short: 'COMING HOME',    detail: 'Heading home now',                   severity: 0, category: 'Home' },
    'F61': { short: 'HOME SAFE',      detail: 'Arrived home safely',                severity: 0, category: 'Home' },
    'F62': { short: 'DINNER READY',   detail: 'Food is served',                     severity: 0, category: 'Home' },
    'F63': { short: 'KIDS OK',        detail: 'Children are fine',                  severity: 0, category: 'Home' },
    'F64': { short: 'KIDS PICKED UP', detail: 'Collected the kids',                 severity: 0, category: 'Home' },
    'F65': { short: 'VISITOR',        detail: 'Someone at the door',                severity: 0, category: 'Home' },
    'F66': { short: 'ALARM',          detail: 'House alarm triggered',              severity: 3, category: 'Home' },
    'F67': { short: 'POWER OUT',      detail: 'Electricity out at home',            severity: 1, category: 'Home' },
    'F68': { short: 'WATER LEAK',     detail: 'Plumbing issue',                     severity: 2, category: 'Home' },
    'F69': { short: 'PET FED',        detail: 'Pet has been fed',                   severity: 0, category: 'Home' },
    'F6A': { short: 'LAUNDRY DONE',   detail: 'Washing finished',                   severity: 0, category: 'Home' },
    'F6B': { short: 'GROCERIES',      detail: 'Need to buy groceries',              severity: 0, category: 'Home' },
    'F6C': { short: 'PACKAGE ARRIVED', detail: 'Delivery at door',                  severity: 0, category: 'Home' },

    // === F7x: NUMBERS (composable) ===
    'F70': { short: '0',    detail: 'Number zero',     severity: 0, category: 'Number' },
    'F71': { short: '1',    detail: 'Number one',      severity: 0, category: 'Number' },
    'F72': { short: '2',    detail: 'Number two',      severity: 0, category: 'Number' },
    'F73': { short: '3',    detail: 'Number three',    severity: 0, category: 'Number' },
    'F74': { short: '4',    detail: 'Number four',     severity: 0, category: 'Number' },
    'F75': { short: '5',    detail: 'Number five',     severity: 0, category: 'Number' },
    'F76': { short: '6',    detail: 'Number six',      severity: 0, category: 'Number' },
    'F77': { short: '7',    detail: 'Number seven',    severity: 0, category: 'Number' },
    'F78': { short: '8',    detail: 'Number eight',    severity: 0, category: 'Number' },
    'F79': { short: '9',    detail: 'Number nine',     severity: 0, category: 'Number' },
    'F7A': { short: '10',   detail: 'Number ten',      severity: 0, category: 'Number' },
    'F7B': { short: '100',  detail: 'Hundred',         severity: 0, category: 'Number' },
    'F7C': { short: '1000', detail: 'Thousand',        severity: 0, category: 'Number' },
    'F7D': { short: 'MANY', detail: 'Large quantity',   severity: 0, category: 'Number' },
    'F7E': { short: 'FEW',  detail: 'Small quantity',   severity: 0, category: 'Number' },

    // === F8x: TIME ===
    'F80': { short: 'NOW',            detail: 'Right now / immediately',            severity: 1, category: 'Time' },
    'F81': { short: 'TODAY',          detail: 'Sometime today',                     severity: 0, category: 'Time' },
    'F82': { short: 'TOMORROW',       detail: 'Next day',                           severity: 0, category: 'Time' },
    'F83': { short: 'THIS WEEK',      detail: 'Within this week',                   severity: 0, category: 'Time' },
    'F84': { short: 'MORNING',        detail: 'In the morning',                     severity: 0, category: 'Time' },
    'F85': { short: 'AFTERNOON',      detail: 'In the afternoon',                   severity: 0, category: 'Time' },
    'F86': { short: 'EVENING',        detail: 'In the evening',                     severity: 0, category: 'Time' },
    'F87': { short: 'NIGHT',          detail: 'At night',                           severity: 0, category: 'Time' },
    'F88': { short: 'ASAP',           detail: 'As soon as possible',                severity: 2, category: 'Time' },
    'F89': { short: 'NO RUSH',        detail: 'Take your time',                     severity: 0, category: 'Time' },
    'F8A': { short: 'OVERDUE',        detail: 'Past deadline',                      severity: 2, category: 'Time' },
    'F8B': { short: 'ON TIME',        detail: 'Running on schedule',                severity: 0, category: 'Time' },
    'F8C': { short: 'EARLY',          detail: 'Ahead of schedule',                  severity: 0, category: 'Time' },
    'F8D': { short: 'DELAYED',        detail: 'Behind schedule',                    severity: 1, category: 'Time' },

    // === F9x: ACTIONS ===
    'F90': { short: 'CALL ME',        detail: 'Please phone me',                    severity: 1, category: 'Action' },
    'F91': { short: 'TEXT ME',        detail: 'Send me a message',                  severity: 0, category: 'Action' },
    'F92': { short: 'CHECK EMAIL',    detail: 'Look at your email',                 severity: 0, category: 'Action' },
    'F93': { short: 'LOOK UP',        detail: 'Look up / look here',               severity: 0, category: 'Action' },
    'F94': { short: 'COME HERE',      detail: 'Come to my location',               severity: 1, category: 'Action' },
    'F95': { short: 'STEP OUTSIDE',   detail: 'Come outside',                       severity: 0, category: 'Action' },
    'F96': { short: 'OPEN DOOR',      detail: 'Open the door please',              severity: 0, category: 'Action' },
    'F97': { short: 'TURN OFF',       detail: 'Turn something off',                severity: 0, category: 'Action' },
    'F98': { short: 'TURN ON',        detail: 'Turn something on',                 severity: 0, category: 'Action' },
    'F99': { short: 'BRING',          detail: 'Bring something',                    severity: 0, category: 'Action' },
    'F9A': { short: 'SEND',           detail: 'Send it / ship it',                 severity: 0, category: 'Action' },
    'F9B': { short: 'PRINT',          detail: 'Print the document',                severity: 0, category: 'Action' },
    'F9C': { short: 'SAVE',           detail: 'Save / back up',                    severity: 0, category: 'Action' },
    'F9D': { short: 'DELETE',         detail: 'Remove / delete it',                severity: 1, category: 'Action' },
    'F9E': { short: 'SHARE',          detail: 'Share this',                         severity: 0, category: 'Action' },
    'F9F': { short: 'SCREENSHOT',     detail: 'Take a screenshot',                 severity: 0, category: 'Action' },

    // === FAx: GAMING & FUN ===
    'FA0': { short: 'GG',             detail: 'Good game',                          severity: 0, category: 'Gaming' },
    'FA1': { short: 'READY',          detail: 'Ready to start',                     severity: 0, category: 'Gaming' },
    'FA2': { short: 'GO!',            detail: 'Start / go now',                     severity: 0, category: 'Gaming' },
    'FA3': { short: 'PAUSE',          detail: 'Pause / hold',                       severity: 0, category: 'Gaming' },
    'FA4': { short: 'HELP ME',        detail: 'Need assistance',                    severity: 1, category: 'Gaming' },
    'FA5': { short: 'FOLLOW ME',      detail: 'Follow my lead',                     severity: 0, category: 'Gaming' },
    'FA6': { short: 'WATCH OUT',      detail: 'Danger / careful',                   severity: 2, category: 'Gaming' },
    'FA7': { short: 'NICE',           detail: 'Great move / impressive',            severity: 0, category: 'Gaming' },
    'FA8': { short: 'LOL',            detail: 'Laughing',                           severity: 0, category: 'Gaming' },
    'FA9': { short: 'OMG',            detail: 'Surprised / shocked',                severity: 0, category: 'Gaming' },
    'FAA': { short: 'REMATCH',        detail: 'Play again?',                        severity: 0, category: 'Gaming' },
    'FAB': { short: 'AFK',            detail: 'Away from keyboard',                 severity: 0, category: 'Gaming' },

    // === FBx: TRANSPORT ===
    'FB0': { short: 'BUS',            detail: 'Taking the bus',                     severity: 0, category: 'Transport' },
    'FB1': { short: 'TRAIN',          detail: 'Taking the train',                   severity: 0, category: 'Transport' },
    'FB2': { short: 'TAXI',           detail: 'In a taxi / rideshare',              severity: 0, category: 'Transport' },
    'FB3': { short: 'DRIVING',        detail: 'I\'m driving',                       severity: 0, category: 'Transport' },
    'FB4': { short: 'WALKING',        detail: 'On foot',                            severity: 0, category: 'Transport' },
    'FB5': { short: 'CYCLING',        detail: 'On a bicycle',                       severity: 0, category: 'Transport' },
    'FB6': { short: 'FLIGHT',         detail: 'At airport / flying',                severity: 0, category: 'Transport' },
    'FB7': { short: 'BOARDING',       detail: 'Boarding now',                       severity: 0, category: 'Transport' },
    'FB8': { short: 'LANDED',         detail: 'Plane has landed',                   severity: 0, category: 'Transport' },
    'FB9': { short: 'DELAYED FLIGHT', detail: 'Flight is delayed',                  severity: 1, category: 'Transport' },
    'FBA': { short: 'FLAT TYRE',      detail: 'Vehicle breakdown',                  severity: 2, category: 'Transport' },
    'FBB': { short: 'ACCIDENT',       detail: 'Road accident',                      severity: 3, category: 'Transport' },

    // === FCx: WEATHER ===
    'FC0': { short: 'RAINING',        detail: 'It\'s raining',                      severity: 0, category: 'Weather' },
    'FC1': { short: 'STORM',          detail: 'Storm approaching',                  severity: 2, category: 'Weather' },
    'FC2': { short: 'HOT',            detail: 'Very hot weather',                   severity: 0, category: 'Weather' },
    'FC3': { short: 'COLD',           detail: 'Very cold weather',                  severity: 0, category: 'Weather' },
    'FC4': { short: 'CLEAR SKY',      detail: 'Nice weather',                       severity: 0, category: 'Weather' },
    'FC5': { short: 'FLOODING',       detail: 'Flood conditions',                   severity: 3, category: 'Weather' },
    'FC6': { short: 'FOGGY',          detail: 'Low visibility fog',                 severity: 1, category: 'Weather' },
    'FC7': { short: 'WINDY',          detail: 'Strong winds',                       severity: 1, category: 'Weather' },

    // === FDx: SHOPPING & MONEY ===
    'FD0': { short: 'BUY THIS',       detail: 'Purchase this item',                severity: 0, category: 'Shopping' },
    'FD1': { short: 'HOW MUCH?',      detail: 'Asking the price',                  severity: 0, category: 'Shopping' },
    'FD2': { short: 'TOO EXPENSIVE',  detail: 'Over budget',                       severity: 1, category: 'Shopping' },
    'FD3': { short: 'GOOD DEAL',      detail: 'Great price, buy it',               severity: 0, category: 'Shopping' },
    'FD4': { short: 'SOLD OUT',       detail: 'Item unavailable',                  severity: 1, category: 'Shopping' },
    'FD5': { short: 'FOUND IT',       detail: 'Located the item',                  severity: 0, category: 'Shopping' },
    'FD6': { short: 'PAID',           detail: 'Payment done',                      severity: 0, category: 'Shopping' },
    'FD7': { short: 'NEED CASH',      detail: 'Need money / ATM',                  severity: 1, category: 'Shopping' },
    'FD8': { short: 'REFUND',         detail: 'Want a refund',                     severity: 1, category: 'Shopping' },

    // === FEx: EDUCATION ===
    'FE0': { short: 'CLASS START',    detail: 'Class is starting',                 severity: 0, category: 'Education' },
    'FE1': { short: 'CLASS OVER',     detail: 'Class has ended',                   severity: 0, category: 'Education' },
    'FE2': { short: 'HOMEWORK',       detail: 'Assignment due',                    severity: 1, category: 'Education' },
    'FE3': { short: 'EXAM',           detail: 'Test / examination',               severity: 2, category: 'Education' },
    'FE4': { short: 'PASSED',         detail: 'Passed the test!',                  severity: 0, category: 'Education' },
    'FE5': { short: 'FAILED',         detail: 'Did not pass',                      severity: 2, category: 'Education' },
    'FE6': { short: 'STUDY GROUP',    detail: 'Group study session',               severity: 0, category: 'Education' },
    'FE7': { short: 'LIBRARY',        detail: 'At the library',                    severity: 0, category: 'Education' },
    'FE8': { short: 'QUESTION',       detail: 'I have a question',                severity: 0, category: 'Education' },
    'FE9': { short: 'PRESENTATION',   detail: 'Presentation time',                severity: 1, category: 'Education' },

    // === FFx: CUSTOM / USER-DEFINED ===
    'FF0': { short: 'CUSTOM 1',       detail: 'User-defined message 1',           severity: 0, category: 'Custom' },
    'FF1': { short: 'CUSTOM 2',       detail: 'User-defined message 2',           severity: 0, category: 'Custom' },
    'FF2': { short: 'CUSTOM 3',       detail: 'User-defined message 3',           severity: 0, category: 'Custom' },
    'FF3': { short: 'CUSTOM 4',       detail: 'User-defined message 4',           severity: 0, category: 'Custom' },
    'FF4': { short: 'CUSTOM 5',       detail: 'User-defined message 5',           severity: 0, category: 'Custom' },
    'FF5': { short: 'CUSTOM 6',       detail: 'User-defined message 6',           severity: 0, category: 'Custom' },
    'FF6': { short: 'CUSTOM 7',       detail: 'User-defined message 7',           severity: 0, category: 'Custom' },
    'FF7': { short: 'CUSTOM 8',       detail: 'User-defined message 8',           severity: 0, category: 'Custom' },
    'FF8': { short: 'CUSTOM 9',       detail: 'User-defined message 9',           severity: 0, category: 'Custom' },
    'FF9': { short: 'CUSTOM 10',      detail: 'User-defined message 10',          severity: 0, category: 'Custom' },
    'FFA': { short: 'CUSTOM 11',      detail: 'User-defined message 11',          severity: 0, category: 'Custom' },
    'FFB': { short: 'CUSTOM 12',      detail: 'User-defined message 12',          severity: 0, category: 'Custom' },
    'FFC': { short: 'PING',           detail: 'Are you there?',                   severity: 0, category: 'Custom' },
    'FFD': { short: 'PONG',           detail: 'Yes I\'m here',                    severity: 0, category: 'Custom' },
    'FFE': { short: 'HEARTBEAT',      detail: 'Still alive / connected',          severity: 0, category: 'Custom' },
    'FFF': { short: 'END',            detail: 'End of all codes',                 severity: 0, category: 'Custom' },
};

// Checksum: XOR of 3 nibbles, masked to 4 bits
function sarChecksum(code3) {
    const n0 = parseInt(code3[0], 16);
    const n1 = parseInt(code3[1], 16);
    const n2 = parseInt(code3[2], 16);
    return ((n0 ^ n1 ^ n2) & 0xF).toString(16).toUpperCase();
}

function sarEncode(code3) {
    return code3.toUpperCase() + sarChecksum(code3);
}

function sarDecode(code4) {
    const data = code4.substring(0, 3).toUpperCase();
    const check = code4[3].toUpperCase();
    const expected = sarChecksum(data);
    return { code: data, valid: check === expected, entry: SAR_CODES[data] || null };
}

// Group codes by category for UI
function sarGrouped() {
    const groups = {};
    for (const [code, entry] of Object.entries(SAR_CODES)) {
        if (!groups[entry.category]) groups[entry.category] = [];
        groups[entry.category].push({ code, ...entry });
    }
    return groups;
}

// Severity colours
const SEV_COLOURS = {
    0: { fg: '#0f0', bg: '#040', border: '#0f0', label: 'INFO' },
    1: { fg: '#48f', bg: '#004', border: '#48f', label: 'LOW' },
    2: { fg: '#ff0', bg: '#340', border: '#ff0', label: 'MEDIUM' },
    3: { fg: '#fa0', bg: '#430', border: '#fa0', label: 'HIGH' },
    4: { fg: '#f44', bg: '#400', border: '#f44', label: 'CRITICAL' },
};

// Super-groups: 6 top-level rings for the dial, each containing sub-categories
const SUPER_GROUPS = {
    'EMERGENCY': {
        colour: '#f44',
        categories: ['System', 'Self', 'Observed', 'Medical', 'Confirm']
    },
    'HAZARD': {
        colour: '#fa0',
        categories: ['Hazard', 'Structure', 'Fire', 'HAZMAT', 'Infra']
    },
    'HELP': {
        colour: '#ff0',
        categories: ['Resource', 'Team', 'Location', 'Maritime', 'Civilian']
    },
    'SOCIAL': {
        colour: '#0f0',
        categories: ['Social', 'Response', 'Status', 'Gaming', 'Custom']
    },
    'WORK': {
        colour: '#48f',
        categories: ['Office', 'Action', 'Time', 'Education', 'Number']
    },
    'LIFE': {
        colour: '#0af',
        categories: ['Food', 'Logistics', 'Home', 'Transport', 'Weather', 'Shopping']
    },
};

// ========== 3-RING GRAMMAR ENGINE ==========
// Ring 1 = sentence pattern (7 options)
// Ring 2 = contextual slot, filtered by Ring 1 (4-12 items)
// Ring 3 = final detail, filtered by Ring 1 + Ring 2 (4-12 items)
// Each entry maps to 1-3 hex codes for transmission.

const SAR_GRAMMAR = {
    STATUS: {
        label: 'STATUS', colour: '#f44',
        ring2: {
            ME:       { codes: ['105'], ring3: { OK: ['105'], TRAPPED: ['101'], INJURED: ['106'], SERIOUS: ['107'], AIR_LOW: ['10F'], UNCONSCIOUS: ['10E'] }},
            TEAM:     { codes: ['802'], ring3: { OK: ['105'], WITHDRAWING: ['803'], ON_SCENE: ['802'], PAUSED: ['805'], NEED_BACKUP: ['807'] }},
            CHILDREN: { codes: ['10A'], ring3: { SAFE: ['903'], TRAPPED: ['10A','101'], INJURED: ['10A','106'] }},
            ELDERLY:  { codes: ['10B'], ring3: { SAFE: ['903'], TRAPPED: ['10B','101'], NEED_HELP: ['10B','600'] }},
            SURVIVOR: { codes: ['200'], ring3: { CONSCIOUS: ['201'], UNCONSCIOUS: ['202'], MOBILE: ['20A'], STRETCHER: ['20B'], EXTRACTED: ['208'] }},
            MULTIPLE: { codes: ['109'], ring3: { TRAPPED: ['109'], VOICE_HEARD: ['204'], DOG_ALERT: ['206'], AREA_CLEARED: ['209'] }},
        }
    },
    REQUEST: {
        label: 'REQUEST', colour: '#ff0',
        ring2: {
            MEDICAL:  { codes: ['600'], ring3: { ASAP: ['600','F88'], STRETCHER: ['602'], BREATHING: ['606'], CUTS_TOOL: ['603'] }},
            WATER:    { codes: ['601'], ring3: { ASAP: ['601','F88'], NO_RUSH: ['601','F89'], FOOD_TOO: ['601','60A'] }},
            TOOLS:    { codes: ['603'], ring3: { CUTTING: ['603'], LIFTING: ['604'], ROPE: ['607'], LIGHT: ['605'] }},
            BOAT:     { codes: ['608'], ring3: { ASAP: ['608','F88'], HELICOPTER: ['609'] }},
            COMMS:    { codes: ['60C'], ring3: { RELAY: ['60C'], BLANKETS: ['60B'] }},
        }
    },
    REPORT: {
        label: 'REPORT', colour: '#fa0',
        ring2: {
            FIRE:     { codes: ['400'], ring3: { SPREADING: ['B00'], CONTAINED: ['B01'], OUT: ['B02'], FLASHOVER: ['B03'], EVACUATE: ['B06'] }},
            FLOOD:    { codes: ['403'], ring3: { RISING: ['403'], STABLE: ['403','905'], ROAD_BLOCKED: ['D02'] }},
            GAS:      { codes: ['402'], ring3: { LEAK: ['402'], TOXIC_AIR: ['407'], CO: ['C05'], HAZMAT: ['C00'] }},
            COLLAPSE: { codes: ['501'], ring3: { IMMINENT: ['500'], PARTIAL: ['501'], TOTAL: ['502'], VOID_FOUND: ['503'], SHORING: ['506'] }},
            BLOCKED:  { codes: ['504'], ring3: { BLOCKED: ['504'], OPEN: ['505'], STAIRS_OK: ['507'], ELEVATOR: ['508'], FLOOR_WEAK: ['509'] }},
            ELECTRIC: { codes: ['406'], ring3: { LIVE_WIRES: ['406'], POWER_OUT: ['D00'], GENERATOR: ['D05'] }},
        }
    },
    NAVIGATE: {
        label: 'NAVIGATE', colour: '#0f0',
        ring2: {
            HERE:     { codes: ['700'], ring3: { SAFE_ZONE: ['70A'], DANGER: ['70B'], RALLY: ['707'], TRIAGE: ['709'], LANDING: ['708'] }},
            NORTH:    { codes: ['701'], ring3: { GO: ['701'], BLOCKED: ['701','504'], CLEAR: ['701','505'] }},
            SOUTH:    { codes: ['702'], ring3: { GO: ['702'], BLOCKED: ['702','504'], CLEAR: ['702','505'] }},
            UP:       { codes: ['705'], ring3: { GO: ['705'], STAIRS: ['705','507'], BLOCKED: ['705','504'] }},
            DOWN:     { codes: ['706'], ring3: { GO: ['706'], STAIRS: ['706','507'], BLOCKED: ['706','504'] }},
            PERIMETER:{ codes: ['70C'], ring3: { SET: ['70C'], DANGER_ZONE: ['70B'] }},
        }
    },
    SOCIAL: {
        label: 'SOCIAL', colour: '#0af',
        ring2: {
            HELLO:    { codes: ['F00'], ring3: { MORNING: ['F07'], NIGHT: ['F08'], THANKS: ['F02'], BYE: ['F01'], TAKE_CARE: ['F0B'] }},
            COFFEE:   { codes: ['F30'], ring3: { NOW: ['F30','F80'], LATER: ['F30','F13'], LUNCH: ['F31'], DINNER: ['F32'], DRINKS: ['F36'] }},
            MEETING:  { codes: ['F20'], ring3: { NOW: ['F20'], OVER: ['F21'], LATE: ['F22'], ON_BREAK: ['F23'], DND: ['F2D'] }},
            COMING:   { codes: ['F1D'], ring3: { '5MIN': ['F43'], '15MIN': ['F44'], '30MIN': ['F45'], TRAFFIC: ['F46'], ARRIVED: ['F41'] }},
            HOME:     { codes: ['F60'], ring3: { COMING: ['F60'], SAFE: ['F61'], DINNER: ['F62'], KIDS_OK: ['F63'] }},
            FEELINGS: { codes: ['F58'], ring3: { HAPPY: ['F50'], TIRED: ['F52'], STRESSED: ['F51'], EXCITED: ['F53'], SICK: ['F57'] }},
        }
    },
    QUICK: {
        label: 'QUICK', colour: '#48f',
        ring2: {
            YES:      { codes: ['F10'], ring3: {} },
            NO:       { codes: ['F11'], ring3: {} },
            OK:       { codes: ['F14'], ring3: {} },
            WAIT:     { codes: ['F1C'], ring3: {} },
            ROGER:    { codes: ['005'], ring3: {} },
            REPEAT:   { codes: ['003'], ring3: {} },
            CANCEL:   { codes: ['00B'], ring3: {} },
            MAYDAY:   { codes: ['007'], ring3: {} },
            ALL_CLEAR:{ codes: ['00C'], ring3: {} },
            DONE:     { codes: ['F1B'], ring3: {} },
            BUSY:     { codes: ['F19'], ring3: {} },
            UNDERSTOOD:{ codes: ['F1E'], ring3: {} },
        }
    },
    CONFIRM: {
        label: 'CONFIRM', colour: '#4f4',
        ring2: {
            ALIVE:    { codes: ['900'], ring3: { STABLE: ['900','905'], WORSE: ['904'], WAITING: ['906'] }},
            RECEIVED: { codes: ['901'], ring3: { SAFE: ['903'], EXTRACTED: ['902'] }},
            SITUATION:{ codes: ['905'], ring3: { BETTER: ['905'], WORSE: ['904'], SAME: ['906'] }},
        }
    },
};

// Get ring options for grammar engine
function sarGrammarRing1() {
    return Object.entries(SAR_GRAMMAR).map(([key, g]) => ({ key, label: g.label, colour: g.colour }));
}

function sarGrammarRing2(ring1Key) {
    const g = SAR_GRAMMAR[ring1Key];
    if (!g) return [];
    return Object.entries(g.ring2).map(([key, r2]) => ({ key, codes: r2.codes, hasRing3: Object.keys(r2.ring3).length > 0 }));
}

function sarGrammarRing3(ring1Key, ring2Key) {
    const g = SAR_GRAMMAR[ring1Key];
    if (!g || !g.ring2[ring2Key]) return [];
    return Object.entries(g.ring2[ring2Key].ring3).map(([key, codes]) => ({ key, codes }));
}

// Resolve grammar selection to display text
function sarGrammarText(codes) {
    return codes.map(c => {
        const tr = typeof sarText === 'function' ? sarText(c) : SAR_CODES[c];
        return tr ? tr.short : c;
    }).join(' + ');
}

// Build super-grouped structure for dial UI
function sarSuperGrouped() {
    const flat = sarGrouped();
    const result = {};
    for (const [superName, def] of Object.entries(SUPER_GROUPS)) {
        const codes = [];
        for (const cat of def.categories) {
            if (flat[cat]) codes.push(...flat[cat]);
        }
        result[superName] = { colour: def.colour, codes };
    }
    return result;
}

// ========== BLE SAR PROTOCOL ==========
const SAR_BLE = {
    SERVICE:  '0000sar0-b1m0-c0de-0000-000000000001',
    TX_CHAR:  '0000sar0-b1m0-c0de-0000-000000000002', // write (phone → relay)
    RX_CHAR:  '0000sar0-b1m0-c0de-0000-000000000003', // notify (relay → phone)
};

// Encode SAR packet to BLE bytes: [code4_hi, code4_lo, ...gps_ascii, 0x00]
function sarPackToBLE(code3, gpsStr) {
    const code4 = sarEncode(code3);
    const val = parseInt(code4, 16);
    const bytes = [val >> 8, val & 0xFF];
    if (gpsStr) {
        for (let i = 0; i < gpsStr.length; i++) bytes.push(gpsStr.charCodeAt(i));
    }
    bytes.push(0); // null terminator
    return new Uint8Array(bytes);
}

// Decode BLE bytes back to { code, entry, gps }
function sarUnpackBLE(bytes) {
    if (bytes.length < 2) return null;
    const val = (bytes[0] << 8) | bytes[1];
    const code4 = val.toString(16).toUpperCase().padStart(4, '0');
    const result = sarDecode(code4);
    let gps = '';
    for (let i = 2; i < bytes.length; i++) {
        if (bytes[i] === 0) break;
        gps += String.fromCharCode(bytes[i]);
    }
    return { ...result, gps };
}

// ========== i18n ==========
// Languages: flag emoji + native name + translations for key SAR terms
// Each entry: { flag, name, codes: { 'HEX': { short, detail } } }
// Only critical/common codes translated — rest fall back to English.

const SAR_LANGUAGES = {
    en: { flag: '🇬🇧', name: 'English' },
    ms: { flag: '🇲🇾', name: 'Bahasa Melayu' },
    zh: { flag: '🇨🇳', name: '中文' },
    ta: { flag: '🇮🇳', name: 'தமிழ்' },
    ja: { flag: '🇯🇵', name: '日本語' },
    ko: { flag: '🇰🇷', name: '한국어' },
    ar: { flag: '🇸🇦', name: 'العربية' },
    es: { flag: '🇪🇸', name: 'Español' },
    fr: { flag: '🇫🇷', name: 'Français' },
    th: { flag: '🇹🇭', name: 'ไทย' },
};

// Translations: code → { short, detail } per language
// English is the base in SAR_CODES — these override for display only.
const SAR_I18N = {
    ms: {
        '000': { short: 'NULL', detail: 'Penghantaran kosong / ujian' },
        '001': { short: 'TERIMA', detail: 'Mesej diterima' },
        '003': { short: 'ULANG', detail: 'Sila hantar semula' },
        '004': { short: 'SEDIA', detail: 'Sedia, menyediakan jawapan' },
        '005': { short: 'FAHAM', detail: 'Difahami, akan patuhi' },
        '007': { short: 'MAYDAY', detail: 'Kecemasan — semua stesen' },
        '008': { short: 'PAN-PAN', detail: 'Mendesak — bukan mengancam nyawa' },
        '100': { short: 'SOS', detail: 'Kecemasan mengancam nyawa' },
        '101': { short: 'TERPERANGKAP', detail: 'Terperangkap, tidak boleh bergerak' },
        '102': { short: 'TERPERANGKAP BOLEH GERAK', detail: 'Terperangkap tetapi boleh bergerak' },
        '105': { short: 'OK BEBAS', detail: 'Tidak cedera, bergerak bebas' },
        '107': { short: 'CEDERA TERUK', detail: 'Cedera serius, perlu perubatan' },
        '109': { short: 'RAMAI TERPERANGKAP', detail: 'Ramai orang terperangkap' },
        '10F': { short: 'UDARA RENDAH', detail: 'Udara boleh dihirup terhad' },
        '200': { short: 'MANGSA DITEMUI', detail: 'Mangsa terselamat dijumpai' },
        '300': { short: 'PENDARAHAN', detail: 'Pendarahan teruk tidak terkawal' },
        '304': { short: 'SESAK NAFAS', detail: 'Kesukaran bernafas' },
        '400': { short: 'KEBAKARAN', detail: 'Kebakaran aktif di lokasi' },
        '402': { short: 'KEBOCORAN GAS', detail: 'Kebocoran gas dikesan' },
        '403': { short: 'BANJIR', detail: 'Air meningkat di lokasi' },
        '500': { short: 'RUNTUH HAMPIR', detail: 'Struktur hampir runtuh' },
        '600': { short: 'PERLU PERUBATAN', detail: 'Pasukan perubatan diperlukan' },
        '601': { short: 'PERLU AIR', detail: 'Air minuman diperlukan' },
        '700': { short: 'SAYA DI SINI', detail: 'Menanda posisi saya' },
        '800': { short: 'HANTAR BANTUAN', detail: 'Minta pasukan ke posisi' },
        'B06': { short: 'PINDAH SEKARANG', detail: 'Pemindahan segera dari bangunan' },
        'F00': { short: 'HELLO', detail: 'Salam' },
        'F02': { short: 'TERIMA KASIH', detail: 'Terima kasih' },
        'F10': { short: 'YA', detail: 'Setuju' },
        'F11': { short: 'TIDAK', detail: 'Tidak' },
        'F14': { short: 'OK', detail: 'Difahami / baik' },
    },
    zh: {
        '007': { short: '求救', detail: '紧急求救 — 所有站台' },
        '100': { short: '求救', detail: '危及生命的紧急情况' },
        '101': { short: '被困不能动', detail: '被困，无法移动' },
        '102': { short: '被困可移动', detail: '被困但可局部移动' },
        '105': { short: '安全自由', detail: '未受伤，自由移动' },
        '107': { short: '重伤', detail: '严重受伤，需要医疗' },
        '109': { short: '多人被困', detail: '此处多人被困' },
        '10F': { short: '空气不足', detail: '可呼吸空气有限' },
        '200': { short: '发现幸存者', detail: '找到幸存者' },
        '300': { short: '大出血', detail: '严重不可控出血' },
        '304': { short: '呼吸困难', detail: '呼吸窘迫' },
        '400': { short: '火灾', detail: '此地有活跃火灾' },
        '403': { short: '洪水', detail: '水位上升中' },
        '500': { short: '即将倒塌', detail: '建筑即将倒塌' },
        '600': { short: '需要医疗', detail: '需要医疗队' },
        '601': { short: '需要水', detail: '需要饮用水' },
        '700': { short: '我在这里', detail: '标记我的位置' },
        '800': { short: '派遣救援', detail: '请求团队到此位置' },
        'B06': { short: '立即撤离', detail: '立即疏散建筑' },
        'F00': { short: '你好', detail: '问候' },
        'F02': { short: '谢谢', detail: '感谢' },
        'F10': { short: '是', detail: '肯定' },
        'F11': { short: '不是', detail: '否定' },
    },
    ta: {
        '100': { short: 'SOS', detail: 'உயிருக்கு ஆபத்தான அவசரநிலை' },
        '101': { short: 'சிக்கியுள்ளேன்', detail: 'சிக்கியுள்ளேன், நகர முடியவில்லை' },
        '105': { short: 'நலம்', detail: 'காயமில்லை, சுதந்திரமாக நகர்கிறேன்' },
        '200': { short: 'உயிர்பிழைத்தவர்', detail: 'உயிர்பிழைத்தவர் கண்டுபிடிக்கப்பட்டார்' },
        '400': { short: 'தீ', detail: 'இடத்தில் தீ எரிகிறது' },
        '600': { short: 'மருத்துவம் தேவை', detail: 'மருத்துவ குழு தேவை' },
        '700': { short: 'நான் இங்கே', detail: 'எனது நிலையைக் குறிக்கிறேன்' },
        'F00': { short: 'வணக்கம்', detail: 'வாழ்த்துக்கள்' },
        'F10': { short: 'ஆம்', detail: 'ஒப்புக்கொள்கிறேன்' },
        'F11': { short: 'இல்லை', detail: 'மறுப்பு' },
    },
    ja: {
        '100': { short: 'SOS', detail: '生命を脅かす緊急事態' },
        '101': { short: '閉じ込め', detail: '閉じ込められて動けない' },
        '105': { short: '無事', detail: '負傷なし、自由に移動可能' },
        '200': { short: '生存者発見', detail: '生存者を発見' },
        '400': { short: '火災', detail: '現場で火災発生中' },
        '600': { short: '医療必要', detail: '医療チームが必要' },
        '700': { short: 'ここにいます', detail: '自分の位置を示す' },
        'F00': { short: 'こんにちは', detail: '挨拶' },
        'F10': { short: 'はい', detail: '肯定' },
        'F11': { short: 'いいえ', detail: '否定' },
    },
    ko: {
        '100': { short: 'SOS', detail: '생명을 위협하는 긴급상황' },
        '101': { short: '갇혔음', detail: '갇혀서 움직일 수 없음' },
        '105': { short: '안전', detail: '부상 없이 자유롭게 이동 중' },
        '200': { short: '생존자 발견', detail: '생존자를 찾았습니다' },
        '400': { short: '화재', detail: '현장에서 화재 발생' },
        '600': { short: '의료 필요', detail: '의료팀 필요' },
        '700': { short: '여기 있어요', detail: '내 위치 표시' },
        'F00': { short: '안녕', detail: '인사' },
        'F10': { short: '네', detail: '긍정' },
        'F11': { short: '아니오', detail: '부정' },
    },
    ar: {
        '100': { short: 'نجدة', detail: 'حالة طوارئ تهدد الحياة' },
        '101': { short: 'محاصر', detail: 'محاصر، لا أستطيع التحرك' },
        '105': { short: 'بخير', detail: 'غير مصاب، أتحرك بحرية' },
        '200': { short: 'ناجٍ', detail: 'تم العثور على ناجٍ' },
        '400': { short: 'حريق', detail: 'حريق نشط في الموقع' },
        '600': { short: 'مساعدة طبية', detail: 'فريق طبي مطلوب' },
        '700': { short: 'أنا هنا', detail: 'تحديد موقعي' },
        'F00': { short: 'مرحبا', detail: 'تحية' },
        'F10': { short: 'نعم', detail: 'إيجابي' },
        'F11': { short: 'لا', detail: 'سلبي' },
    },
    es: {
        '100': { short: 'SOS', detail: 'Emergencia que amenaza la vida' },
        '101': { short: 'ATRAPADO', detail: 'Atrapado, no puedo moverme' },
        '105': { short: 'OK LIBRE', detail: 'Sin lesiones, moviéndome libremente' },
        '200': { short: 'SUPERVIVIENTE', detail: 'Superviviente localizado' },
        '400': { short: 'INCENDIO', detail: 'Incendio activo en la ubicación' },
        '600': { short: 'NECESITA MÉDICO', detail: 'Equipo médico necesario' },
        '700': { short: 'ESTOY AQUÍ', detail: 'Marcando mi posición' },
        'B06': { short: 'EVACUAR AHORA', detail: 'Evacuación inmediata del edificio' },
        'F00': { short: 'HOLA', detail: 'Saludo general' },
        'F10': { short: 'SÍ', detail: 'Afirmativo' },
        'F11': { short: 'NO', detail: 'Negativo' },
    },
    fr: {
        '100': { short: 'SOS', detail: 'Urgence vitale' },
        '101': { short: 'PIÉGÉ', detail: 'Piégé, impossible de bouger' },
        '105': { short: 'OK LIBRE', detail: 'Indemne, libre de mouvement' },
        '200': { short: 'SURVIVANT', detail: 'Survivant localisé' },
        '400': { short: 'INCENDIE', detail: 'Incendie actif sur site' },
        '600': { short: 'BESOIN MÉDECIN', detail: 'Équipe médicale requise' },
        '700': { short: 'JE SUIS ICI', detail: 'Marquage de ma position' },
        'B06': { short: 'ÉVACUER', detail: 'Évacuation immédiate du bâtiment' },
        'F00': { short: 'BONJOUR', detail: 'Salutation générale' },
        'F10': { short: 'OUI', detail: 'Affirmatif' },
        'F11': { short: 'NON', detail: 'Négatif' },
    },
    th: {
        '100': { short: 'SOS', detail: 'เหตุฉุกเฉินเป็นอันตรายถึงชีวิต' },
        '101': { short: 'ติดอยู่', detail: 'ติดอยู่ ขยับไม่ได้' },
        '105': { short: 'ปลอดภัย', detail: 'ไม่บาดเจ็บ เคลื่อนที่ได้อิสระ' },
        '200': { short: 'พบผู้รอดชีวิต', detail: 'พบผู้รอดชีวิตแล้ว' },
        '400': { short: 'ไฟไหม้', detail: 'ไฟไหม้ที่ตำแหน่ง' },
        '600': { short: 'ต้องการแพทย์', detail: 'ต้องการทีมแพทย์' },
        '700': { short: 'ฉันอยู่ที่นี่', detail: 'ระบุตำแหน่งของฉัน' },
        'F00': { short: 'สวัสดี', detail: 'ทักทาย' },
        'F10': { short: 'ใช่', detail: 'ตกลง' },
        'F11': { short: 'ไม่', detail: 'ปฏิเสธ' },
    },
};

// Get translated text for a code, falling back to English
let sarLang = 'en';
function sarSetLang(lang) { sarLang = SAR_LANGUAGES[lang] ? lang : 'en'; }
function sarText(code3) {
    const base = SAR_CODES[code3];
    if (!base) return null;
    if (sarLang === 'en' || !SAR_I18N[sarLang] || !SAR_I18N[sarLang][code3]) return base;
    const tr = SAR_I18N[sarLang][code3];
    return { ...base, short: tr.short || base.short, detail: tr.detail || base.detail };
}

if (typeof module !== 'undefined') module.exports = { SAR_CODES, sarChecksum, sarEncode, sarDecode, sarGrouped, sarSuperGrouped, SUPER_GROUPS, SEV_COLOURS, SAR_BLE, sarPackToBLE, sarUnpackBLE, SAR_LANGUAGES, SAR_I18N, sarSetLang, sarText, SAR_GRAMMAR, sarGrammarRing1, sarGrammarRing2, sarGrammarRing3, sarGrammarText };
