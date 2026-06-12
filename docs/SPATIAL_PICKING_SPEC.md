# Spatial Picking Spec — warehouse pick / put-away as a mobile walk through the model

*Spec for the second op-log-native addon: a phone-first **"walk the aisles"** app — the warehouse
rendered as a BIM-like 3D model, the pick route drawn through it, each bin confirmed by a **QR scan**,
every pick/put-away a **signed op** on the same tenant ledger the ERP folds. Roadmap home: item 2 of
the [Migrate & Compare paper's roadmap](MigrateComparisonPaper.md#v-roadmap). Sibling addon:
[POS Addon Spec](POS_ADDON_SPEC.md) (same rails, different room).*

> **Status: SPEC.** §2 lists what is already proven; everything else is the build. The same
> addon contract applies as the POS spec §1: pills + dictionary + process handlers + kernel ops,
> **no new engine, no addon-private persistence**.

---

## 1. The idea

A picker doesn't think in table rows; they think in *places*. This project already renders places —
buildings stream from a SQLite db into Three.js with fly-to, lens highlighting, and tap-picking on
mobile. And the ERP already knows warehouse places — `M_Locator` per `M_Warehouse`, on-hand per
locator. The addon joins the two:

```
pick list (M_Movement / M_InOut lines)             the ERP truth
  → route over locators                            ordered walk (aisle/rack/bin)
  → 3D walk: fly-to + highlight the NEXT bin       the BIM viewer verbs
  → at the bin: scan its QR                        BarcodeDetector (proven)
  → scan == expected locator? confirm qty          one tap / qty stepper
  → signed op group: the movement line enacted     kernel commitGroup
  → on-hand falls/rises via the qtyOnHand fold     same spine as POS backflush
```

Put-away is the same walk with the sign reversed (receipt → destination locator).

## 2. Substrate inventory — what already exists

**ERP side** (full-width `ad_seed.db` + the proven engine):

| Asset | Evidence | Note |
|---|---|---|
| `M_Locator` 11 rows, `M_Warehouse`, `m_storageonhand` 20 rows | seed (PR #265) | ⚠ `X/Y/Z` are **TEXT labels** ("Store North"), *not* coordinates — see §S-1 |
| `M_Movement`(323) / `M_InOut`(319) FSM walked | `ad_docfsm.js` (W-MOVEMENT/W-MINOUT FSM) | inout block: no VO on completed; movement lines carry `m_locator_id` → `m_locatorto_id` |
| `movementSign` / `qtyOnHand` fold | `scripts/erp_engine.js` (W-FOLD-QTYONHAND) | on-hand = fold of movements; locator→warehouse map is load-bearing (proven in W-FOLD-REPLENISH) |
| signed op groups | `kernel_ops.commitGroup` (W-OPGROUP, verifyChain) | all-or-none, idempotent gid, hash-chained |
| element↔ERP linkage pattern | [BIMtoERP.md](BIMtoERP.md) §A/§B (W-ERP-PROBE/EXPORT/FOLD) | GUID-keyed, data-gated chips, idempotent export |

**Viewer side** (bim-ootb/viewer):

| Capability | Where | Reuse |
|---|---|---|
| camera fly-to + pivot | `streaming.js` `A.flyTo`, precision-pivot lane | fly the picker to the next bin |
| highlight / ghost depth model | `navigate_find.js` (`_highlightGuids`, room lens, Alt+X ghost) | rest-of-warehouse ghosted 0.1, current rack solid, target bin bright (the FIND_LENS depth model verbatim) |
| mobile tap/long-press picking | `picking.js` (pointer guards, pinch-guard) | tap a bin = same raycaster path |
| **QR scanning — production code** | `scripts/system_explorer.js:910-930` (W-QR-INPUT, glassbowl) | `BarcodeDetector` + `getUserMedia({facingMode:environment})`, rAF loop, honest unsupported-fallback — lift the pattern as a shared module |
| camera/GPS/share precedents | sitecam.js (4D capture), share sheet | permission UX, fallback idioms |
| mobile pill idioms | `pill_builder.js` registry, Lucide-only | the addon's dock |

**Compiler side** (the warehouse *model*):

| Capability | Where | Reuse |
|---|---|---|
| recursive BOM compilation (building→floor→room→furniture) | [BOMBasedCompilation.md](BOMBasedCompilation.md), W-BOM-ENGINE / W-BUFFER | warehouse→aisle→rack→bin is the SAME recursion, one level deeper named differently |
| verb formulas TILE / ROUTE / CLUSTER | BOM engine | TILE bins along a shelf; ROUTE is literally the picker path primitive |
| geometry from library meshes (AABB primitives) | component_library.db | racks/bins are boxes — the cheapest possible geometry; no IFC needed |

## 3. The spec

### §S-1 The warehouse model — compile it, don't survey it

`M_Locator.X/Y/Z` are text labels, so **positions are compiled, not read**: a small
`warehouse.yaml` recipe (EXTRACT: one line per aisle/rack/bin naming its `M_Locator.Value`)
compiles via the existing BOM recursion into a `warehouse.db` the viewer streams like any
building — aisles as rooms, racks as furniture, bins as TILEd children, each mesh stamped with
its **`m_locator_id` as the element GUID** (the BIMtoERP linkage key, reversed: here the ERP id
*is* the GUID).

- Witness **W-WH-COMPILE**: `§WH bins=<n> == m_locator rows mapped` + W-BUFFER space contract
  holds (bins fit racks fit aisles). §FALSIFIER: a recipe bin naming a locator absent from
  `m_locator` must fail the compile (no invented bins).
- Render gate applies (no-cubes rule): distinct-vertex check before serving.
- Out of scope v1: scale drawings/real CAD import. The recipe is honest about being schematic —
  topology + walk order matter, millimetres don't.

### §S-2 The pick list → route

- Source docs: drafted `M_Movement` (transfer/pick) or `M_InOut` (receipt → put-away). Lines
  carry product, qty, from/to locator — already in the dictionary, already FSM-walked.
- Route = ORDER BY the walk sequence (aisle/rack/bin from the recipe tree — the ROUTE verb's
  order), not by line number. Pure function: `route(lines, tree) → [step]`.
- Witness **W-WH-ROUTE**: same lines + same tree ⇒ same route (deterministic); steps cover ALL
  lines exactly once. §FALSIFIER: a line whose locator is off-model must surface as an explicit
  "unroutable" step, never silently dropped.
- **POS handoff (design 2026-06-12, `docs/POS_ADDON_SPEC.md §P-12`):** a POS sale commits an
  `M_InOut` (its shipment) into the same ledger the walk reads. `draftPick` (`wh_walk.js`) should gain
  an option to source the route from **open POS-generated `M_InOut`/`M_Movement` docs**, alongside the
  current replenishment draft. This is the "finish the sale → go pick it" fold — two lenses, one
  ledger, NO coupling. Additive §S-2 selector, not new machinery. Another device picking the same list
  = the multi-device version of the same fold.
  **Open-docs only (engine note, Fable5 review 2026-06-12):** the selector sources
  `docstatus IN ('DR','IP')` — the WR cash-and-carry sale completes its `M_InOut` INSIDE the sale
  group (W-POS-WR: nothing left to pick, on-hand already moved; routing it would double-move stock).
  The pickable sale is the **deliver-later shape** (plain `SOO` — seed doctype `132 Standard Order` —
  shipment stays DR; the walk's §S-4 scan-commit is what completes it and moves on-hand). That sale
  variant is engine glue, built engine-side first; see `POS_ADDON_SPEC.md §P-12` engine note.
  **STATUS 2026-06-12 — the ENGINE half is ✅ DONE (POS_GAP_CLOSE §G-1, W-POS-DELIVERLATER,
  bim-compiler 5bc4b389):** `pos_core.buildDeliverLaterGroup` (order CO + `M_InOut` born DR, policy
  verbatim from the doctype-132 row, invoice timing named from `InvoiceRule`) +
  `pos_core.completeShipmentOps` (scan-commit completes the DR shipment by the PICKED qty;
  doctype-148 `IsPickQAConfirm` REFUSES → the `inout_confirm.js` W-WH-CONFIRM gate). The selector
  query itself is witnessed against a folded ledger (`§S-2 selector open-pos-docs=[… DR]`, empties
  after the pick). The WALK-SIDE half — this section's `draftPick` source extension — is the open
  item: build card **`prompts/WH_POS_PICK_LANE.md`**. Access-path hardening rode bim-ootb #280
  (viewer sw v647): PWA-resume dead-link self-heal (`§PWA_RESUME_CLEAR` → landing) and
  W-WH-LIVE-PAGES retargeted to the in-repo GH Pages db (OCI duplicate retired); the walk engine
  itself is byte-unchanged (W-WH-LIVE regression green).

### §S-3 The walk — viewer UX

- Mobile-first surface (a pill on the warehouse model's viewer page): the FIND-lens depth model
  drives focus — everything ghosted, the current rack solid, the **target bin bright + camera
  flown to it**; a strip shows step i/N, product, qty.
- Tap the lit bin (or the strip) → the scan screen. Long-press = skip-with-reason (logged as an
  op annotation, the exception trail).
- Witness **W-WH-WALK**: `§WH step=3/12 locator=50003 fly=done lit=1` §-lines per step;
  falsifier: tapping a NON-target bin must not advance the step.

### §S-4 Scan = the one clean act

- Each bin carries a printed QR encoding its `m_locator_id` (+ a tenant salt). The app reuses
  the W-QR-INPUT pattern (BarcodeDetector; honest fallback = type the locator code — the
  same non-invent act, lower tech).
- Scan match → qty confirm (default = line qty, stepper for short-pick) → **one signed op
  group** enacting the line: movement enacted (M± on the qty spine), short-pick = qty delta with
  the remainder left open on the doc. This is POSLens §1 verbatim: collapse input to a single
  authentic act at the place itself; everything downstream is a fold.
- Witness **W-WH-SCAN**: scan of the WRONG bin refuses (`§WH scan=50004 expected=50003 REFUSED`);
  right bin commits a group whose replay moves `qtyOnHand` by exactly the confirmed qty;
  verifyChain ok. §FALSIFIER: hand-typed locator code goes through the same refuse/commit gate.

### §S-5 Completion + the books

- Last step → doc Complete via `ad_docfsm.dispatchFor` (323/319 walked sets); postings via the
  frozen `derivePostings` where the doc posts; on-hand now folds to the new truth — the SAME
  numbers the ERP, the POS replenishment, and the Posting-Preview see, because it is one ledger.
- Witness **W-WH-COMPLETE**: after the walk, `qtyOnHand` fold == per-locator expected deltas for
  every touched (product, locator); doc status CO; TB unchanged-or-balanced per doc class.

### §S-6 Put-away + cycle-count (same walk, different verbs)

- Put-away: receipt lines route to destination locators (sign +). Cycle-count: the walk visits
  bins and the act is "count what you see" → `M_Inventory` lines (the W-FOLD lane already folds
  MInventory I± rule-consistently). Named for v2; the §S-1..§S-5 rails are identical.

## 4. Honest gaps (named)

- **No coordinates in the ERP** — the model is a compiled schematic (§S-1); claiming "BIM-accurate
  warehouse" would be invention. It is a *navigable topology with boxes*, which is what picking needs.
- **BarcodeDetector support varies** (no Safari/iOS guarantee) — the typed-code fallback is part of
  the spec, not an afterthought; no third-party QR wasm in v1.
- **GardenWorld scale**: 11 locators / 20 on-hand rows = demo aisle. Real tenants bring rows via the
  install lifecycle.
- **Reservation semantics** (`m_storagereservation`) are read by the replenish fold but not yet
  enacted by picks — short-pick → reservation release is v2.
- **Offline walk** rides the same sync-FSM story as POS §P-5 — out of v1 scope, never re-invent.

## 5. Done-when

Each §S is ✅ (witness + on-screen verify on a phone-sized viewport + deploy) or ⛔ with the one
blocking fact. The demo: compile the GardenWorld warehouse, draft a 3-line movement, walk it on a
phone, scan three printed QRs, and watch `qtyOnHand` + the ERP window agree to the unit.

---

## 6. Extension arc (proven rails — downhill from here)

The §S-1..§S-5 proof establishes the moat: spatial model + signed op-log + same ledger as the
financial fold, zero-install browser. Everything below extends that without re-inventing the rails.

### §S-7 Packing order

Route verb variant: `packRoute(lines, products)` sorts the walk by pack priority instead of aisle
sequence — heaviest/bulkiest first, fragile last, same locator-bin targeting.
Pack priority derives from `m_product` attributes already in the seed (weight, `m_product_category`
flags, or a `packing_order` attribute injected at compile time).
The strip UI, scan gate, and op-group are identical to §S-3..§S-5 — only the sort key changes.
**Architecture cost: one new pure verb. No new screen, no new kernel path.**

### §S-8 Special handling overlays

The `ANNOTATE` verb (§S-3 long-press skip trail) already writes exception annotations to the op-log.
Extend it inward: before the scan prompt, read hazmat / fragile / two-person flags from the current
step's `m_product` row and render a visual overlay on the bin — a flashing colour, an icon badge, a
text chip ("Fragile — two-person lift").
The depth model (ghost/rack/bin bright) is already the animation system; the overlay is a new
material pass on the bin mesh driven by product metadata, not new UI scaffolding.
**Architecture cost: metadata read + one material variant. Same fly-to, same scan gate.**

### §S-9 Autonomous agent (forklift / robot)

The route verb already outputs the walk sequence as pure data. The QR-scan callback is one
implementation of "confirm this bin" — replace it with any other signal:
- RFID reader → `onTagRead(locatorId)` instead of `onScanResult(locatorId)`
- Robot position API → poll `robot.currentLocator()` and auto-confirm when reached
- Conveyor sensor → event stream

The `commitGroup` call, the op-log entry, and the ledger reconciliation are unchanged.
The virtual walk highlight (fly-to + bright bin) becomes a live mirror of the physical agent's
position — the same rAF loop, just driven by telemetry instead of a human tap.
**Architecture cost: swap the scan callback. Everything downstream is untouched.**
The hardware integration contract (positioning system, comms protocol) is always custom — that is
the real work. The ERP side is already done.

### §S-10 AR overlay via Walk + SiteCam

The viewer already ships `sitecam.js` — the device camera feed composited under the 3D scene.
The Walk lens (§S-3) runs inside the same viewer. Combining them:
1. Open the warehouse db in the viewer with the SiteCam layer active
2. The virtual bins overlay on the live camera feed, aligned to the compiled spatial topology
3. The Walk pill drives the same fly-to + highlight — the highlight appears on the physical aisle
   as an AR overlay rather than on a dark background

No new architecture: `sitecam.js` + `wh_walk.js` + the existing `A.HOME_URL` nav chain already in
production (sw v654). The alignment accuracy is topology-based (compiled locator positions), not
sensor-fused — sufficient for guided picking, honest about the gap.
**Architecture cost: wire SiteCam layer active when `?mode=ar` param is present. One init flag.**
*WebXR sensor-fused AR = future footnote when device APIs mature and a real IFC building survey
provides metric coordinates.*

### §S-11 Bin share deep link (Walk-to-Snag)

**Ask:** can a warehouse worker share "look at this bin" with a colleague the same way the BIM
viewer shares a picked element or a clash deep link?

**How it works (build on existing rails):**

`share.js` already captures `?pick=<guid>` for element picks and `?cam=`/`?tgt=` for camera;
on load the viewer restores the pick highlight and flies the camera. The WH Walk equivalent is
`?wh_locator=<m_locator_id>` — the bin's locator ID (which IS its element GUID in the compiled
db, §S-1 stamp).

1. **Share (in the Walk strip):** add a share icon button to the strip; on tap build
   `viewer.html?db=../buildings/warehouse_gardenworld.db&wh_locator=<current step locator id>`
   and call `navigator.share` / clipboard fallback. Log `§WH-STEP-SHARE locator=<id>`.
2. **Receive (viewer load):** `config.js` reads `A.WH_LOCATOR = _params.get('wh_locator') || null`.
   `wh_walk.js` gate poll: if `A.WH_LOCATOR` is set after gate=on, call `focusStep` for that
   locator in **view-only mode** (no draft `M_Movement` created — just fly-to + bright highlight +
   ghost rest). Log `§WH-SNAG locator=<id> view=only`.
3. **No login required** for the recipient — it is the viewer URL, the same zero-install surface
   as any building share. The bin highlight works off the compiled db geometry alone.

**Architecture cost:** `config.js` +1 param read · `wh_walk.js` +view-only branch on gate+param ·
strip +share button. Same `focusStep`, same depth model. No new kernel path, no auth gate.

### §S-3b Walk mode engages + ⌂ home — ✅ SHIPPED 2026-06-12 (sw v645, W-WH-WALKMODE)

User direction: the walk should *engage* a distinct mode, not sit as a strip over BIM. Built
(presentation only, engine untouched): `open()` hides `#mobile-pill` (the BIM construction chrome),
`close()` restores it verbatim — `§MODE walk-on/off`. A ⌂ home button on the walk strip navigates to
`A.HOME_URL` (`?home=`, e.g. iDempiere) or `../index.html` (bubbles landing) — single-hop escape,
because browser-back walks the whole nav stack down to the landing.

### §S-4b Desktop / manual confirm — ✅ SHIPPED 2026-06-12 (sw v645, W-WH-WALKMODE) — was OBSERVED GAP

**Observed (desktop F12):** mid-walk (`§WH step=1/3 locator=101 fly=done lit=1`) a mouse click on
the LIT bin resolved to a guid-less mesh — `§PICK hits=12 chosen=0 … §PICK no guid for mesh.id=55`
(top hit op=0.6 = the highlight overlay) — so the scan screen never opened. The phone-witnessed tap
path worked (W-WH-LIVE L2); on desktop the bin/rack OVERLAY mesh intercepts the raycast and carries
no GUID.

**Spec:**
1. **Overlay must not eat the pick**: the walk's overlay meshes get `raycast = noop` (or carry the
   target locator id as GUID so the hit resolves to the bin). Either way a click on the lit bin
   MUST reach `WHWalk.onPick`. Witness line: `§WH TAP via=overlay|mesh bin=<id>`.
2. **Desktop demo confirm (user-dictated)**: no camera on a desktop — **double-click (or long-press)
   the lit bin = confirm**, equivalent to a matching scan, qty confirm still shown (the one clean
   act stays). Single click keeps opening the scan/typed screen. Logged distinctly:
   `§WH scan=<id> via=dblclick MATCH` — the op group is IDENTICAL to the QR path (same gate, §S-4).
   Wrong bin double-clicked → same REFUSE line. This is a DEMO affordance, not a bypass: same
   refuse/commit gate, same signed group.
- Witness **W-WH-DESKTOP**: scripted desktop run — click lit bin opens scan; dblclick lit bin
  commits step; dblclick WRONG bin refused. §FALSIFIER: overlay click must never advance the step
  without the gate line.

### §S-12 ERP info drawer on pick (browse mode) — OBSERVED GAP 2026-06-12

**Observed (live test, F12 + screenshot):** with `§WH PILL gate=on`, tapping a rack
(`§PICK IfcFurnishingElement "Store Central rack"`) shows the GENERIC IFC info card only —
Class/Name/GUID/Storey/Material. No `M_Locator`, no on-hand, no `M_Movement` context. The §S-1
linkage (bin GUID == `m_locator_id`) exists in the db but is only read *inside the Walk lens*;
the browse-mode pick path never touches the ERP side.

**Spec — data-gated ERP chips appended to the info card** (BIMtoERP §A Check-ERP drawer pattern,
reversed: here the GUID *is* the ERP id):

- Trigger: `ELEMENT_PICK` while `WHWalk` gate=on (same gate, no second probe). Lazy-load the seed
  via the existing `wh_walk.js ensureDeps` (ad_seed.db fetch + ERPEngine UMDs — already written,
  share it, don't duplicate).
- **Bin pick** (numeric GUID = `m_locator_id`): chips show locator `Value` + warehouse name ·
  **on-hand per product = the `ERPEngine.qtyOnHand` fold** (seed + op-log — the §S-5 truth, never
  a raw `m_storageonhand` read) · open `M_Movement`/`M_InOut` lines touching this locator
  (from/to) · `m_replenish.level_min` if present.
- **Rack / aisle pick** (non-numeric GUID `RACK_*`/`AISLE_*`): aggregate of child bins from the
  `m_bom_line` tree — `n bins · n products on hand · n open lines`; tap a chip → drill to the
  bin list. (The observed test picked a RACK — this case is first-class, not an edge.)
- Honest-empty: a bin with nothing on hand shows `on-hand: —` (zero rows), never invented rows.
- Witness **W-WH-INFO**: `§WH-INFO guid=<g> locator=<Value> onhand=<n> openlines=<n>` per pick.
  §FALSIFIER 1: gate=off building → ZERO chips added (code inert, generic card byte-identical).
  §FALSIFIER 2: locator with no movement lines shows `openlines=0`, not a fabricated list.

**Architecture cost:** one render hook on the pick card + reuse of `ensureDeps` + two pure
queries. No new kernel path, no write.

### §S-13 WH context layer — ADDITIVE, never subtractive (user direction 2026-06-12)

**Direction (user, live test):** the warehouse viewer retaining ALL the BIM goodies — Find panel,
Fly, Shadow earth, Night, x-ray — is a feature, not a bug. Do NOT strip or profile the pill bar.
The BIM capabilities are camera/render machinery, orthogonal to ERP context; the WH context is a
**read-only overlay family on the same GUID↔locator key**, added beside them.

Everything load-bearing already exists: §S-1 linkage in the db · `wh_walk.js ensureDeps` (lazy
seed + engine) · `ERPEngine.qtyOnHand` fold · the depth/palette highlight machinery · the Walk
pill's data-gate (non-WH buildings stay byte-identical). What remains is presentation wiring:

- **§S-13a Find panel ERP facet** — Find already builds storey/discipline/type trees; when
  gate=on, add a **Products** branch: product → bins holding it (qtyOnHand fold > 0) → tap =
  the existing fly-to + highlight (room-lens behavior verbatim). The Find panel BECOMES the
  spatial stock browser — same UX the user already knows. Witness **W-WH-FIND**:
  `§WH-FIND facet=products rows=<n> bins=<n>`; §FALSIFIER: gate=off → no Products branch,
  Find tree byte-identical.
- **§S-13b Stock lens pill** — ONE new data-gated pill (sibling of Walk, same gate): color
  bins by on-hand level via the palette/SC-coloring idiom (e.g. empty=ghost · low=amber ·
  ok=green, thresholds from `m_replenish.level_min` where present). Composes with Night/
  Shadow/Fly — layers, not modes. Witness **W-WH-STOCK**: `§WH-STOCK bins=<n> colored=<n>
  low=<n>`; §FALSIFIER: a bin with no on-hand rows renders ghost, never a colored invention.
- **§S-13c Movement lens (v2)** — open `M_Movement` lines drawn as from→to arrows over the
  floor (the measure/grid overlay line-drawing idiom); tap arrow → its doc / step into the
  Walk. Named v2: needs arrow geometry + doc drill, the only piece without a 1:1 existing idiom.

All three are read-only folds — no new kernel path, no write, no fork of any BIM module.
Sequencing: §S-12 chips first (its queries feed 13a/13b), then 13b (small), then 13a, 13c v2.

**Architecture cost:** 13a = one facet builder on the existing Find tree · 13b = one pill +
one material pass · 13c = v2. Zero behavior change for non-WH buildings (same gate).

### What a real IFC survey adds

The current model is a compiled schematic (§S-1 honest gap — X/Y/Z are text labels, not metric
coordinates). A real warehouse survey via phone LiDAR → IFC → the existing compiler pipeline
produces a metric db — `warehouse_real.db` loads into the viewer unchanged. The walk, AR overlay,
robot path, and packing route all inherit metric precision automatically. This is a content
production problem, not an architecture problem; the code path is identical.
