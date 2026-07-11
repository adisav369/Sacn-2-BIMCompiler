// building_parts_taxonomy.js — §BUILDING-PARTS-TAXONOMY
// Spec: prompts/BUILDING_PARTS_TAXONOMY.md (2026-07-11, MANAGER — FLAG ON THE HILL mission).
//
// Bottom-up extraction (extractParts) + top-down checklist walk (checklistReport) over the SAME
// config/building_taxonomy.yaml table — per the user's own framing: "a taxonomy checklist config
// template that ticks off each part... top down. or bottom up, as we go thru each part looking up
// in the checklist." Same data, two directions, not two mechanisms.
//
// NON-INVENT: STAIR_LIKE and NON_ROOM_DOOR_NAMES below are the EXACT constants build/room_walker.js
// and scripts/compile_rooms.py already use to EXCLUDE stair/lift footprints from the room pool
// (§STAIR-EXCLUDE / §DOOR-NOT-ROOM) — reused verbatim here for the POSITIVE extraction, not
// re-derived. Stairs/lifts never reach spatial_structure as IfcSpace rows (removed pre-compilation),
// so this module reads elements_meta/element_transforms directly, not the room pool.
//
// Dual-mode (Node require + browser <script> global), same convention as room_walker.js /
// room_type_classifier.js.
(function () {
  'use strict';
  var ROOT = (typeof window !== 'undefined') ? window : {};

  var STAIR_LIKE = ["IfcStair%", "IfcRamp%"];                                      // ported, see header
  var LIFT_KEYWORDS = ["liftdeur", "lift", "elevator", "aufzug", "fahrstuhl", "hoist"]; // ported, widened scope (see header)
  // MEP-plant keyword set — the recon's own list (build/measure_disc_room_type_density.js-adjacent
  // discipline vocabulary, applied here to element_name text, not ifc_class — plant equipment
  // spans many IFC classes: IfcFlowMovingDevice, IfcFlowController, IfcUnitaryEquipment, etc.)
  var PLANT_KEYWORDS = ["vent", "duct", "fan", "ahu", "damper", "chiller", "condens", "fancoil", "pump"];

  function _rows(db, sql) {
    var r = db.exec(sql);
    if (!r.length) return [];
    var cols = r[0].columns;
    return r[0].values.map(function (v) {
      var o = {};
      cols.forEach(function (c, i) { o[c] = v[i]; });
      return o;
    });
  }

  function _keywordCond(col, words) {
    return words.map(function (w) { return "LOWER(" + col + ") LIKE '%" + w + "%'"; }).join(' OR ');
  }

  // §PLANT_ROOM_GATE_FIX (prompts/FIND_PANEL_PLANT_ROOM_GATE_FIX.md) — ported byte-identical from
  // bim-ootb viewer/navigate_find.js's fix on the same session (do not re-derive). _keywordCond
  // above is a broad SQL substring pre-filter (superset, real hits never excluded — SQLite has no
  // REGEXP/word-boundary builtin); this JS pass rejects false positives like "Preventer" (contains
  // "vent" mid-token) while keeping real camelCase compounds ("BottomDuct") and prefix hits
  // ("Ventilated"). Confirmed against real element_name templates across Duplex/Terminal/Hospital/
  // Clinic/HHS (59 distinct templates surveyed, see the Viewer commit for the full citation).
  function _splitNameTokens(name) {
    var raw = String(name || '').split(/[^A-Za-z]+/).filter(Boolean);
    var out = [];
    raw.forEach(function (tok) {
      var start = 0;
      for (var i = 1; i < tok.length; i++) {
        if (/[a-z]/.test(tok.charAt(i - 1)) && /[A-Z]/.test(tok.charAt(i))) {
          out.push(tok.slice(start, i));
          start = i;
        }
      }
      out.push(tok.slice(start));
    });
    return out;
  }
  function _keywordTokenMatch(name, words) {
    var tokens = _splitNameTokens(name);
    return tokens.some(function (t) {
      var tl = t.toLowerCase();
      return words.some(function (w) { return tl.indexOf(w) === 0; });
    });
  }
  function _filterWordBoundary(extracted, words) {
    var all = extracted.all.filter(function (r) { return _keywordTokenMatch(r.element_name, words); });
    var positioned = extracted.positioned.filter(function (r) { return _keywordTokenMatch(r.element_name, words); });
    return { all: all, positioned: positioned, length: all.length };
  }

  // §PARENT-NO-TRANSFORM (found running this module's own witness against real Duplex/Clinic/
  // Hospital data, 2026-07-11): an assembly parent (IfcStair) frequently carries NO transform of
  // its own — only its child geometry (IfcStairFlight) does. A transform-requiring JOIN silently
  // UNDERCOUNTS real entities (Duplex: 4 real IfcStair+IfcStairFlight rows, only 2 positionable;
  // Hospital: 60 real IfcStair rows, only 30 positionable — confirmed genuinely distinct guids,
  // not a duplicate-row artifact). Report BOTH counts honestly: `all` = every real entity
  // (elements_meta alone, the correct "does this building have one" existence signal) and
  // `positioned` = the subset with a real transform (the correct "where do I draw a marker"
  // subset) — collapsing to one number would silently hide which use case it actually serves.
  function _extract(db, whereCond) {
    var all = _rows(db, "SELECT guid, ifc_class, element_name, storey FROM elements_meta WHERE (" + whereCond + ")");
    var positioned = _rows(db, "SELECT m.guid, m.ifc_class, m.element_name, m.storey, " +
      "t.center_x cx, t.center_y cy, t.center_z cz FROM elements_meta m " +
      "JOIN element_transforms t ON t.guid = m.guid WHERE (" + whereCond.replace(/(^|\s)element_name/g, '$1m.element_name').replace(/(^|\s)ifc_class/g, '$1m.ifc_class') + ") AND t.center_x IS NOT NULL");
    return { all: all, positioned: positioned, length: all.length }; // .length: back-compat convenience (existence count)
  }

  // Bottom-up: extract real STAIRWAY / LIFT_SHAFT / PLANT_ROOM evidence from one building's DB.
  function extractParts(db) {
    var stairCond = STAIR_LIKE.map(function (p) { return "ifc_class LIKE '" + p + "'"; }).join(' OR ');
    var stairways = _extract(db, stairCond); // ifc_class match, not a name keyword — no false-positive mechanism to fix
    var liftRows = _filterWordBoundary(_extract(db, _keywordCond('element_name', LIFT_KEYWORDS)), LIFT_KEYWORDS);
    var plantRows = _filterWordBoundary(_extract(db, _keywordCond('element_name', PLANT_KEYWORDS)), PLANT_KEYWORDS);

    // Which compiled room (spatial_structure IfcSpace) contains the densest cluster of plantRows —
    // density signal, not identity: no single element IS a plant room, a ROOM containing a real
    // concentration of MEP equipment is the inferred plant/mechanical room. Advisory only (n=1
    // building in practice today), never asserted as a hard match.
    var plantRoomDensity = null;
    if (plantRows.positioned.length) {
      var spaces = _rows(db, "SELECT guid, name, center_x cx, center_y cy, size_x sx, size_y sy " +
        "FROM spatial_structure WHERE type='IfcSpace' AND center_x IS NOT NULL AND size_x IS NOT NULL");
      var counts = {};
      spaces.forEach(function (sp) {
        var x0 = sp.cx - sp.sx / 2, x1 = sp.cx + sp.sx / 2, y0 = sp.cy - sp.sy / 2, y1 = sp.cy + sp.sy / 2;
        var n = plantRows.positioned.filter(function (p) { return p.cx >= x0 && p.cx <= x1 && p.cy >= y0 && p.cy <= y1; }).length;
        if (n > 0) counts[sp.guid] = { room: sp.name, guid: sp.guid, n: n };
      });
      var best = null;
      Object.keys(counts).forEach(function (k) { if (!best || counts[k].n > best.n) best = counts[k]; });
      plantRoomDensity = best ? { room: best.room, n: best.n, total: plantRows.all.length } : null;
    }

    return { STAIRWAY: stairways, LIFT_SHAFT: liftRows, PLANT_ROOM: plantRows, plantRoomDensity: plantRoomDensity };
  }

  // Top-down: walk config/building_taxonomy.yaml's entries for one building_class, tick off what
  // extractParts() found. Returns [{type, expected(min_count), found, status}] — status is
  // 'FOUND' | 'ADVISORY_ZERO' (expected advisory, nothing found this building) — never a hard FAIL,
  // per the spec's min_count:0 = advisory discipline.
  function checklistReport(buildingClass, extracted, taxonomyConfig) {
    var cls = taxonomyConfig.building_classes && taxonomyConfig.building_classes[buildingClass];
    if (!cls) return { buildingClass: buildingClass, parts: [], note: 'no taxonomy entry for this class' };
    var out = (cls.parts || []).map(function (p) {
      var found = (extracted[p.type] || []).length;
      return {
        type: p.type, source: p.source, min_count: p.min_count,
        found: found,
        status: found > 0 ? 'FOUND' : 'ADVISORY_ZERO'
      };
    });
    return { buildingClass: buildingClass, parts: out };
  }

  var API = { STAIR_LIKE: STAIR_LIKE, LIFT_KEYWORDS: LIFT_KEYWORDS, PLANT_KEYWORDS: PLANT_KEYWORDS,
    extractParts: extractParts, checklistReport: checklistReport };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  else ROOT.BuildingPartsTaxonomy = API;
})();
