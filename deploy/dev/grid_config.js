/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_config.js — View Configuration (JSON strategy)
 *
 * Data-driven config for view presets: what to clip, what to retain,
 * how to style contours and edges. No logic — pure data.
 *
 * To add a retained class: add it to the retain array.
 * To change a line weight: edit the styles entry.
 * To add a new view: add a views entry + matching VIEW_DEFS in grid_views.js.
 */
var GridConfig = {

  // ── Default style (fallback when no class-specific style) ─────────
  defaultStyle: { color: '#666666', weight: 0.5 },

  // ── Per-View Configuration ────────────────────────────────────────
  views: {

    // ── Floor Plans ─────────────────────────────────────────────────

    floor: {
      contourMode: 'section',
      clip: { mode: 'horizontal', offset_m: 1.0 },
      retain: [
        'IfcFurnishingElement',     // generic furniture
        'IfcFurniture',             // explicit furniture
        'IfcFlowTerminal',          // fans, sinks, taps
        'IfcSanitaryTerminal',      // toilets, basins
        'IfcElectricalAppliance',   // switches, outlets
        'IfcLightFixture',          // ceiling lights (project top)
        'IfcBuildingElementProxy',  // misc elements
        'IfcCovering'               // floor/ceiling coverings
      ],
      retain_mode: 'project_top',
      styles: {
        'IfcWall':              { color: '#000000', weight: 2.0 },
        'IfcWallStandardCase':  { color: '#000000', weight: 2.0 },
        'IfcColumn':            { color: '#000000', weight: 2.0 },
        'IfcDoor':              { color: '#333333', weight: 1.0, arc: true },
        'IfcWindow':            { color: '#4488CC', weight: 0.5 },
        'IfcSlab':              { color: '#999999', weight: 0.3 },
        'IfcFurnishingElement': { color: '#888888', weight: 0.5 },
        'IfcFurniture':         { color: '#888888', weight: 0.5 },
        'IfcStair':             { color: '#666666', weight: 1.0 },
        'IfcRailing':           { color: '#AAAAAA', weight: 0.3 }
      }
    },

    floor1: {
      contourMode: 'section',
      clip: { mode: 'horizontal', offset_ratio: 0.55 },
      retain: [
        'IfcFurnishingElement',
        'IfcFurniture',
        'IfcFlowTerminal',
        'IfcSanitaryTerminal',
        'IfcElectricalAppliance',
        'IfcLightFixture',
        'IfcBuildingElementProxy',
        'IfcCovering'
      ],
      retain_mode: 'project_top',
      styles: {
        'IfcWall':              { color: '#000000', weight: 2.0 },
        'IfcWallStandardCase':  { color: '#000000', weight: 2.0 },
        'IfcColumn':            { color: '#000000', weight: 2.0 },
        'IfcDoor':              { color: '#333333', weight: 1.0, arc: true },
        'IfcWindow':            { color: '#4488CC', weight: 0.5 },
        'IfcSlab':              { color: '#999999', weight: 0.3 },
        'IfcFurnishingElement': { color: '#888888', weight: 0.5 },
        'IfcFurniture':         { color: '#888888', weight: 0.5 }
      }
    },

    // ── Elevation Views ─────────────────────────────────────────────

    front: {
      contourMode: 'elevation',
      clip: null,
      retain: [],
      styles: {
        'IfcWall':              { color: '#000000', weight: 2.5 },
        'IfcWallStandardCase':  { color: '#000000', weight: 2.5 },
        'IfcRoof':              { color: '#000000', weight: 2.5 },
        'IfcSlab':              { color: '#666666', weight: 1.5 },
        'IfcColumn':            { color: '#333333', weight: 2.0 },
        'IfcWindow':            { color: '#444444', weight: 1.0 },
        'IfcDoor':              { color: '#444444', weight: 1.0 },
        'IfcCurtainWall':       { color: '#444444', weight: 1.0 },
        'IfcPlate':             { color: '#444444', weight: 0.5 },
        'IfcBeam':              { color: '#888888', weight: 0.5 },
        'IfcMember':            { color: '#888888', weight: 0.5 },
        'IfcStair':             { color: '#666666', weight: 1.0 },
        'IfcRailing':           { color: '#AAAAAA', weight: 0.3 }
      },
      levelMarkers: {
        enabled: true,
        style: { color: '#666666', weight: 0.5, dash: [0.3, 0.15] },
        labelStyle: { color: '#333333', fontSize: 14 }
      }
    },

    rear: {
      contourMode: 'elevation',
      clip: null,
      retain: [],
      styles: {
        'IfcWall':              { color: '#000000', weight: 2.5 },
        'IfcWallStandardCase':  { color: '#000000', weight: 2.5 },
        'IfcRoof':              { color: '#000000', weight: 2.5 },
        'IfcSlab':              { color: '#666666', weight: 1.5 },
        'IfcColumn':            { color: '#333333', weight: 2.0 },
        'IfcWindow':            { color: '#444444', weight: 1.0 },
        'IfcDoor':              { color: '#444444', weight: 1.0 },
        'IfcCurtainWall':       { color: '#444444', weight: 1.0 },
        'IfcPlate':             { color: '#444444', weight: 0.5 },
        'IfcBeam':              { color: '#888888', weight: 0.5 },
        'IfcMember':            { color: '#888888', weight: 0.5 },
        'IfcStair':             { color: '#666666', weight: 1.0 },
        'IfcRailing':           { color: '#AAAAAA', weight: 0.3 }
      },
      levelMarkers: {
        enabled: true,
        style: { color: '#666666', weight: 0.5, dash: [0.3, 0.15] },
        labelStyle: { color: '#333333', fontSize: 14 }
      }
    },

    left: {
      contourMode: 'elevation',
      clip: null,
      retain: [],
      styles: {
        'IfcWall':              { color: '#000000', weight: 2.5 },
        'IfcWallStandardCase':  { color: '#000000', weight: 2.5 },
        'IfcRoof':              { color: '#000000', weight: 2.5 },
        'IfcSlab':              { color: '#666666', weight: 1.5 },
        'IfcColumn':            { color: '#333333', weight: 2.0 },
        'IfcWindow':            { color: '#444444', weight: 1.0 },
        'IfcDoor':              { color: '#444444', weight: 1.0 },
        'IfcCurtainWall':       { color: '#444444', weight: 1.0 },
        'IfcPlate':             { color: '#444444', weight: 0.5 },
        'IfcBeam':              { color: '#888888', weight: 0.5 },
        'IfcMember':            { color: '#888888', weight: 0.5 },
        'IfcStair':             { color: '#666666', weight: 1.0 },
        'IfcRailing':           { color: '#AAAAAA', weight: 0.3 }
      },
      levelMarkers: {
        enabled: true,
        style: { color: '#666666', weight: 0.5, dash: [0.3, 0.15] },
        labelStyle: { color: '#333333', fontSize: 14 }
      }
    },

    right: {
      contourMode: 'elevation',
      clip: null,
      retain: [],
      styles: {
        'IfcWall':              { color: '#000000', weight: 2.5 },
        'IfcWallStandardCase':  { color: '#000000', weight: 2.5 },
        'IfcRoof':              { color: '#000000', weight: 2.5 },
        'IfcSlab':              { color: '#666666', weight: 1.5 },
        'IfcColumn':            { color: '#333333', weight: 2.0 },
        'IfcWindow':            { color: '#444444', weight: 1.0 },
        'IfcDoor':              { color: '#444444', weight: 1.0 },
        'IfcCurtainWall':       { color: '#444444', weight: 1.0 },
        'IfcPlate':             { color: '#444444', weight: 0.5 },
        'IfcBeam':              { color: '#888888', weight: 0.5 },
        'IfcMember':            { color: '#888888', weight: 0.5 },
        'IfcStair':             { color: '#666666', weight: 1.0 },
        'IfcRailing':           { color: '#AAAAAA', weight: 0.3 }
      },
      levelMarkers: {
        enabled: true,
        style: { color: '#666666', weight: 0.5, dash: [0.3, 0.15] },
        labelStyle: { color: '#333333', fontSize: 14 }
      }
    },

    // ── Roof ────────────────────────────────────────────────────────

    roof: {
      contourMode: null,   // keep 3D top-down view as-is
      clip: null,
      retain: [],
      styles: {}
    }
  },

  // ── Helpers ───────────────────────────────────────────────────────

  retainSet: function(mode) {
    var view = this.views[mode];
    if (!view || !view.retain) return {};
    var set = {};
    for (var i = 0; i < view.retain.length; i++) set[view.retain[i]] = 1;
    return set;
  },

  styleFor: function(mode, ifcClass) {
    var view = this.views[mode];
    if (!view || !view.styles) return this.defaultStyle;
    return view.styles[ifcClass] || this.defaultStyle;
  },

  clipFor: function(mode) {
    var view = this.views[mode];
    return (view && view.clip) ? view.clip : null;
  },

  contourModeFor: function(mode) {
    var view = this.views[mode];
    return (view && view.contourMode) ? view.contourMode : null;
  },

  levelMarkersFor: function(mode) {
    var view = this.views[mode];
    return (view && view.levelMarkers) ? view.levelMarkers : null;
  }
};
