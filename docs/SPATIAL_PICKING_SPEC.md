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
