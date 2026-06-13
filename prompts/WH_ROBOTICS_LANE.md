# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: WH ROBOTICS LANE (§S-9a/§S-9b)
# Paste-to-start: `proceed with prompts/WH_ROBOTICS_LANE.md`
# Scope: wire the `window.WHWalk.robot` stub in wh_walk.js + prove a robot controller
#   can drive the walk to completion without touching any human UI. NVIDIA/Cosmos
#   integration is EXPLORATION only in this lane — no model download, no Isaac Sim setup
#   here; the goal is a clean seam the robot side can meet.
# READ THE LOG after every run (exit code ≠ evidence).
# ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# NON-NEGOTIABLE: newVerbs=[] · Lucide-only icons · NON-INVENT · bim-ootb edits in
#   /tmp/wt-* off fresh origin/main · one PR · SW bump once.

---

## Background

The WH walk (§S-3..§S-5, `viewer/wh_walk.js`) already has:
- A route sequence of steps (`W.steps`) — ordered bin locations, product, qty
- `WHWalk.scanInput(locatorId, via)` — the unified confirm gate (QR / typed / manual)
- `WHWalk.confirmHere()` — manual bypass when you vouch you are at the bin
- `commitGroup` / ledger reconciliation — unchanged by anything here
- `?home=<url>` and `§WH-HOME` nav back to iDempiere

What is missing: a **published JS API** for a robot controller to read the route and push
confirmations, without going through the human scan UI. That is the full scope of this lane.

NVIDIA Cosmos context and the meet-halfway model are in
`docs/SPATIAL_PICKING_SPEC.md §S-9b` — read it before coding.

---

## Pre-Pinned Facts

1. **Confirm gate** (`wh_walk.js` `scanInput`): validates `locatorId` against the CURRENT step's
   `m_locator_id`. Wrong locator → refuses with `§WH scan MISMATCH`. Right locator → commits
   an ANNOTATE op (POS-route) or ENACT_MOVE (M_Movement route) via `commitGroup`. The robot
   MUST pass the exact `m_locator_id` value, not a GUID or a name.

2. **Locator→GUID map** (`wh_walk.js` `W.locVal`): `buildLocVal` queries
   `elements_meta WHERE guid GLOB '[0-9]*'` — the §S-1 stamp that links ERP locator ids to
   BIM element GUIDs. From that GUID the viewer can resolve 3D position (centroid of the
   element's geometry). `W.locVal[locatorId]` = the human-readable label. The GUID lives in
   `m_bom_line.role='BIN'` rows joined to `elements_meta`.

3. **Coordinates are schematic** until a real IFC survey feeds the compiler. `xyz` in
   `currentStep()` will be null on today's GardenWorld model — name that honestly in the log,
   never invent a coordinate.

4. **`?mode=robot` param**: read by `wh_walk.js` at init time (`_params.get('mode')==='robot'`).
   When set: suppress the scan screen (`#wh-scan`) and the "Confirm bin" button
   (`#wh-scan-btn`) from the strip; they are replaced by a robot-status div. Human can still
   override via `WHWalk.robot.confirm(id)` or the existing `WHWalk.confirmHere()`.

5. **Witness to run after every change**:
   - `bash build/erp/run_witness.sh scripts/poc_wh_robot.js` (NEW, this lane)
   - `WT_ROOT=<wt> bash build/erp/run_witness.sh scripts/poc_wh_walk_live.js` (regression)

---

## Spec

### §R-1 — `window.WHWalk.robot` API (wh_walk.js)

Add to `wh_walk.js` alongside `WHWalk.toggle` / `WHWalk.isOpen`:

```js
window.WHWalk.robot = {
  // ── READ ─────────────────────────────────────────────────────────────────
  currentStep: function () {
    if (!W.open || W.idx >= W.steps.length) return null;
    var s = W.steps[W.idx];
    var guid = W.locGuid ? W.locGuid[s.m_locator_id] : null;   // §R-3 adds locGuid
    return {
      step: s.step, of: s.of,
      locatorId: s.m_locator_id,
      locatorLabel: W.locVal[s.m_locator_id] || null,
      locatorGuid: guid || null,
      xyz: null,                  // null until metric IFC survey — §S-9b honest gap
      product: W.names[s.line.m_product_id] || s.line.m_product_id,
      qty: s.line.qty,
      unroutable: !!s.unroutable
    };
  },
  stepCount: function () {
    var done = doneCount();
    return { total: W.steps.length, done: done, remaining: W.steps.length - done };
  },

  // ── WRITE ────────────────────────────────────────────────────────────────
  confirm: function (locatorId) {
    // same gate as QR — wrong locator REFUSES, ledger op is identical
    console.log('§WH-ROBOT confirm locatorId=' + locatorId);
    WHWalk.scanInput(String(locatorId), 'robot');
  },
  skip: function (reason) {
    console.log('§WH-ROBOT skip reason=' + reason);
    WHWalk.skipStep('robot: ' + (reason || 'no-reason'));
  },

  // ── TELEMETRY (optional) ─────────────────────────────────────────────────
  telemetry: function (x, y, z, locatorId) {
    // Drives the 3D mirror: update a robot-avatar object in the scene.
    // fly-to is suppressed in robot mode; the camera follows telemetry instead.
    // locatorId: if it matches W.steps[W.idx].m_locator_id → auto-confirm (policy opt-in)
    console.log('§WH-ROBOT telemetry x=' + x + ' y=' + y + ' z=' + z +
      ' locatorId=' + (locatorId || 'none'));
    // TODO: scene avatar update (§R-4, deferred)
  }
};
```

§-log: `§WH-ROBOT confirm locatorId=<id>` · `§WH-ROBOT skip reason=<r>` ·
`§WH-ROBOT telemetry x=… y=… z=…`

### §R-2 — `?mode=robot` strip variant (wh_walk.js `ensureUI`)

When `(new URLSearchParams(location.search)).get('mode') === 'robot'`:
- Hide `#wh-scan-btn` ("Confirm bin") from the strip
- Replace it with `<div id="wh-robot-status">Robot mode — awaiting confirm</div>` (same style,
  updates via `renderStrip`)
- `#wh-scan` (the full-screen scan overlay) is never opened in robot mode
- `§WH-ROBOT mode=on` logged once at open

Human override: `WHWalk.confirmHere()` still works (fallback if robot gets stuck).

### §R-3 — locGuid map (wh_walk.js `buildLocVal`)

`W.locGuid` = `{ [m_locator_id]: guid }` — built alongside `W.locVal` from the same
`m_bom_line JOIN elements_meta` query. Exposed in `currentStep().locatorGuid`.
`§WH-LOCGUID n=<count>` logged at build time.

### §R-4 — Witness: W-WH-ROBOT (scripts/poc_wh_robot.js)

Headless. Proves:
- `§R-1-READ` — `WHWalk.robot.currentStep()` returns `{locatorId, product, qty, xyz:null}`
  after walk open (auto-engage, seeded M_Movement route).
- `§R-1-CONFIRM` — `WHWalk.robot.confirm(correctId)` advances the step; `chainOk=Y`.
- `§R-1-WRONG` — `WHWalk.robot.confirm(wrongId)` is refused (`§WH scan MISMATCH`).
- `§R-1-SKIP` — `WHWalk.robot.skip('obstacle')` records the ANNOTATE op honestly.
- `§R-2-MODE` — in `?mode=robot`, `#wh-scan-btn` absent, `#wh-robot-status` present.
- `§R-3-GUID` — `currentStep().locatorGuid` is a string or null (never invented).

Run: `bash build/erp/run_witness.sh scripts/poc_wh_robot.js`

---

## NVIDIA / Cosmos meet-halfway (reference, not implementation task)

This lane does NOT download Cosmos or set up Isaac Sim. It only supplies the contract.

**What the robot implementer needs from us (all available after this lane):**
1. `WHWalk.robot.currentStep()` — route target per step (locatorId + label + guid + qty)
2. `WHWalk.robot.confirm(id)` — call this when physically at the bin and pick is done
3. The compiled warehouse db (`warehouse_gardenworld.db`) — for USD/glTF export into Isaac Sim
4. `?mode=robot` URL — open the viewer in headless-robot mode

**What the robot implementer brings:**
1. Cosmos / IsaacSim setup — generate synthetic camera sequences along bin routes from the 3D model
2. Navigation policy (trained on those sequences) — outputs a `locatorId` sequence
3. Physical hardware (mobile base + arm + sensors) — optional; simulation is the first milestone
4. A small WebSocket / BLE / REST bridge that calls `WHWalk.robot.confirm(id)` from the policy

**Cheapest first milestone (simulation only, no hardware):**
- Load `warehouse_gardenworld.db` geometry into Isaac Sim via a glTF/USD export script
- Run Cosmos-Predict on a synthetic camera path between two bin GUIDs
- Script calls `WHWalk.robot.confirm(locatorId)` for each step via `window.open` + `postMessage`
  or a localhost WebSocket (`erp_relay_server.js` already exists for this pattern)
- Walk completes, ledger records the pick — full loop proven in simulation, zero hardware

**Cosmos model choice for this use case:**
- `Cosmos-1.0-Predict-7B` (causal prediction) — smallest, can run on a single A10/RTX 4090
- Input: current camera frame + target bin direction
- Output: predicted frame sequence of reaching the bin
- Used for: verifying the trained policy reaches the right bin before committing hardware time

---

## Implementation Order

1. Write `poc_wh_robot.js` witness (§R-4). Run it — must FAIL on unimplemented API first.
2. Add `window.WHWalk.robot` API to `wh_walk.js` (§R-1).
3. Add `?mode=robot` strip variant (§R-2).
4. Add `W.locGuid` to `buildLocVal` (§R-3).
5. Run witness — all 6 verdicts must PASS.
6. Run regression: `WT_ROOT=<wt> bash build/erp/run_witness.sh scripts/poc_wh_walk_live.js`
7. ONE PR. viewer sw bump. Change note references §R-1..§R-4 + §S-9a.

---

## OUTSTANDING

- [ ] §R-4 witness (`poc_wh_robot.js`) — W-WH-ROBOT 6 verdicts
- [ ] §R-1 `window.WHWalk.robot` API (`wh_walk.js`)
- [ ] §R-2 `?mode=robot` strip variant
- [ ] §R-3 `W.locGuid` map
- [ ] Regression `poc_wh_walk_live.js` PASS
- [ ] PR + viewer sw bump
