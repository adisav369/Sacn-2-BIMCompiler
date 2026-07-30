# Datacentre Cabling — cable tray / containment pathing (KUL070)

```
# ⚠ DO NOT REMOVE
SCOPE: cable containment (tray/ladder) TOPOLOGY and PATHING for datacentre models — the authored
port graph, cable pull-length routing, tray fill/segregation. Read the log after every run; an exit
code is not evidence. EXTRACT OR COMPILE ONLY — never invent a connection the model does not carry.
This file OWNS this topic. The 2GB-IFC ingestion lane (parse ceilings, splitting, extraction) stays
in prompts/IFC_LARGE_PRIVATE_STRESS_TEST.md — do not duplicate either into the other; cross-link.
```

## §WHY — the user's actual question (2026-07-30)
The project's BIM engineer told the user they must do "something like cable tray pathing" **manually**
because *"Revit or the big tools cannot do it easily."* The user asked what he most likely meant, and
whether we can do it. The user's own steer, verbatim: **"since we have basis from rooms injection
pathing we can learn from"** — reuse the room-graph / A* substrate that already works here.

## §MEASURED — established 2026-07-30, verify before extending, do not re-derive
Every number below came from the real files on this machine, not from a run report.

**1. `CONTAINMENT.ifc` IS the cable tray model.** Not an inference — the product names say so.
`~/bim-ootb/IFC/KUL/KUL070-SWC-01-XX-3D-E-0001 - CONTAINMENT.ifc`, 56.7 MB, 774,041 STEP entities:

| | |
|---|---|
| elements | **21,009** — `IfcFlowSegment` 11,441 + `IfcFlowFitting` 9,568, nothing else |
| discipline | MEP = 21,009 (100%) |
| element names | `Ladder Vertical Outside Bend:Standard:…`, `Oglaend System_INS_vertical inside bend_OE FR:OE FR HDG:…` |

`Oglaend System` is a real cable-ladder manufacturer. "CONTAINMENT" is electrical-trade shorthand for
**cable containment** — tray, ladder, basket, trunking, conduit. The generic `IfcFlowSegment` /
`IfcFlowFitting` classes hide this: nothing in the class name says "cable tray", only the product
name does. **Any classifier keying off `ifc_class` alone will mis-read this file as generic MEP.**

**2. The source IFC carries a hand-authored connectivity graph — and we THROW IT AWAY.**
Counted by `grep -oE '=IFC…\('` on the raw file:

| entity | count | meaning |
|---|---|---|
| `IFCDISTRIBUTIONPORT` | 43,187 | connection points on tray segments/fittings |
| `IFCRELCONNECTSPORTTOELEMENT` | 43,187 | which port belongs to which element |
| **`IFCRELCONNECTSPORTS`** | **19,142** | **port-to-port — the actual tray network topology** |

In our extracted DB (`KUL_CONTAINMENT_extracted.db`): **`port_elements=0`, `port_connections=0`.**
The tables are CREATED and never INSERTed into, and `IfcDistributionPort` sits in
`extractIFCtoDB.py`'s own `NON_GEOMETRIC_CLASSES`. **19,142 authored edges are discarded on import.**

**3. We do have a DERIVED graph — but it is geometric, not authored.**
`rel_adjacency = 18,944` rows (all `provenance='derived:face-touch'`; axis split X 7,055 / Y 7,424 /
Z 4,465), from `extractIFCtoDB.py:derive_adjacency` reading `elements_meta ⋈ elements_rtree`.
Note **18,944 vs 19,142 — close, but not equal.** Whether the derived graph reproduces the authored
one was the pivotal open measurement — **now measured, see §AUTHORED_VS_DERIVED. Answer: no.**

> ⚠ **CORRECTION to the wording above (2026-07-30, survey):** it is not "bbox *proximity*". The rule
> is a **FACE-TOUCH** test — `_face_touch(a,b,tol=0.03,min_overlap=0.02)`: the axis with the smallest
> |overlap| must be within 30 mm AND the boxes must overlap ≥20 mm on the *other two* axes. That exact
> shape is why it fails on MEP (§AUTHORED_VS_DERIVED): it was designed for walls that abut, and a tray
> bend fitting does not abut its segment — it **interpenetrates** it.

**4. Consequence for `strip_ifc_nonessential.py`.** `IFC_LARGE_PRIVATE_STRESS_TEST.md` §KUL004 has
`--tier model` DELETE ports (13.6% of CONTAINMENT.ifc) on the stated grounds that *nothing consumes
them*. If ports turn out to be the cable-pathing substrate, **that decision must be revisited** — the
losslessness proof there is still valid, but it proved losslessness *against today's extractor*, which
ignores ports. Flagged, not yet changed.

## §WHAT_REVIT_CANNOT_DO — three candidates, ranked
Marked honestly: (M) = measured here, (I) = industry knowledge, not measured by us.

1. **Cable pull lengths through the containment network — the strongest candidate.** (I) Revit's
   electrical circuits are *logical* (panel → load); they do not physically travel through tray. So
   "how many metres of cable from this panel to that rack, following the trays" is a graph
   shortest-path problem Revit has no native answer to. Done in Excel off drawings. On a datacentre
   this is direct procurement money. (M) The graph needed to answer it exists in the IFC — 19,142
   authored edges — and we currently discard it.
2. **Auto-routing the tray itself.** (I) Revit's "Generate Layout" auto-routes pipe and duct; there is
   no equivalent for cable tray or conduit — every run is drawn by hand. (M) At 11,441 segments +
   9,568 fittings, that is the whole job, by hand.
3. **Tray fill %, segregation, bend radius.** (I) Which cables occupy which tray, fill capacity,
   power-vs-data separation, minimum bend radius — manual checks.

**Best guess: #1, possibly #2.** #1 is the one that is genuinely a graph problem, genuinely expensive
to get wrong, and genuinely absent from Revit. **Not confirmed with the engineer — ask him.**

## §REUSE_FROM_ROOMS — the user's steer, and why it holds (MAP REPLACED 2026-07-30, verified file:symbol)
Same shape, different graph:

| rooms (works today) | cable tray (proposed) |
|---|---|
| rooms = nodes | tray segments/fittings = nodes |
| doors = edges | `IFCRELCONNECTSPORTS` = edges |
| A* over the room graph → fly path | A* over the tray graph → cable pull route |
| path length = walk distance | path length = **cable length** |

⚠ **`docs/internal/WalkerDoctrine.md` is LOCKED** — read it, do not re-litigate it.

### (a) DIRECTLY REUSABLE as-is — the whole reusable surface is four symbols
| symbol | what it gives a tray graph |
|---|---|
| **`viewer/navigate_grid.js:graphAStar` — :411-479** (exported `A.graphAStar` :537) | ★ **best reuse candidate in either repo.** `graphAStar(template, startIfc, endIfc)` over `{nodes:[{x,y,…}], edges:[{from,to,cost}]}`, index-keyed, returns the node chain or `null`. **Zero** references to rooms/doors/`IfcSpace`/storeys. Supply a tray-derived `template` and it works untouched. |
| **`viewer/navigate_grid.js:nearestNode` — :481-489** | snap a start/end pick to a graph node. **⚠ 2D only** (`dx,dy`) — see the traps below. |
| **`common/room_graph.js:_dijkstraCore` — :891-910 + `_Heap` — :1158-1161** | pure adjacency-map Dijkstra; the `graph` arg is dead. Currently **private** (not on the `API` export at :1465) — lift it to a shared module rather than exporting more of `room_graph.js`. |
| **`common/room_graph.js:fullConnectivity` — :818-861** | the island/component report, incl. its own hard-won `§NOT-EVERY-NODE-IS-A-VERTEX` lesson (:818-830): seed the node universe from *edge endpoints*, not from a node dictionary, or you invent phantom size-1 islands. Needs a one-line seed-filter parameterisation. |

### (b) REUSABLE ONLY AFTER GENERALISATION
| symbol | the coupling to parameterise out |
|---|---|
| `common/room_graph.js:_buildAdjacency` — :862-890 | keep only the `else` branch (edges carrying their own `w`). **Two traps, both must be neutralised before any number is called "cable pull length":** (1) the `E1`/`null` branch computes **XY-only** `Math.hypot(cx,cy)` (:869-871) — a vertical riser's length would vanish; (2) `UTILITY_EDGE_PENALTY = 8` (:105, applied :884) multiplies any edge touching a utility-classified room, so `shortestPath().distance` **is not metres**. Note the irony: the room graph *penalises* passing through the very cabling rooms this lane is about. |
| `viewer/navigate_find.js:_ensureRoomsCore` version-stamp block — :970-988, stamp at :1084-1098 | not code to copy, the **mandatory pattern**: `rooms_meta.version` vs the compiler constant, missing/mismatch = stale = recompile, log via `console.log` not `warn`. A tray artifact needs its own `tray_graph_meta` + `TRAY_GRAPH_V`. |
| `viewer/navigate_find.js:_roomGraphFor` — :1208-1215 | generic one-graph-per-building cache keyed on `A.activeBuilding`; only the body (`RG.buildGraph`) is room-coupled. |
| `viewer/tour.js:A._buildGraphRoute` memo — :426-436 | generic input-signature memo; only `storeyZ` is room-specific. |
| `common/room_graph.js:_astarGrid` — :1179-1223 | clean grid A* with an admissible octile heuristic (:1200), but its walkability oracle is hard-called as `_pointWalkable(graph,storey,x,y)` (:1183). Only worth generalising if a tray lane ever needs a *spatial* (clearance-aware) route; a topological pull length does not. |

### (c) ROOM-SPECIFIC — must NOT be forced onto a tray graph
- **`common/room_graph.js:buildGraph` — :217-772.** It *is* the room/door pipeline (`spatial_structure … type='IfcSpace'` :229-232; `ifc_class LIKE 'IfcDoor%' AND discipline='ARC'` :378-380). Nine edge kinds, every one a human-circulation fact. A tray graph needs its own builder; there is nothing to subclass.
- **The four island-bridging heuristics — E7 `§ORPHAN-SPINE-RESCUE` :519-534, E9 `§AMBIGUOUS-RESIDUAL-RESCUE` :487-503, E6 `§CIRC-PER-CHAIN-BRIDGE` :679-709, `§CORRIDOR-ROOM-BACKPROP` :269-326** (all log `§ISLAND_BRIDGE`). Each is justified there by real door+wall evidence for *human* circulation. **There is no equivalent evidence that two unconnected tray ends are electrically continuous. Copying any of these to reduce the 1,486 islands would be fabricating a network. The islands ARE the deliverable.**
- **`circNode` :391-399 + E2 :505-516 + `_publicHop` :941-950** — a fake `CIRC::<storey>` hub with no real position, plus the waypoint-substitution layer that exists only to hide it. A per-storey virtual hub is exactly the invented connectivity the Prime Rule forbids.
- **The door-buffer rule :465-473** (`buf = max(bx,by)/2 + DOOR_BUFFER_SLACK`) — a leaf half-span measured against Duplex. A tray joint's tolerance is a *fabrication tolerance on segment ends*, not a leaf-span buffer. Reusing 0.20 m here = a fabricated constant. Take only its **provenance discipline**: cite the measurement behind any constant.
- **The E1 two-closest tie-break :474-477** — encodes "a doorway has exactly two sides". **Measured here: tray fittings legitimately have degree 3 (992 of them) and degree 4 (37).** Forcing top-2 is the defect `§AMBIGUOUS-RESIDUAL-RESCUE` was invented to patch. Model tray fittings as multi-degree from the start.
- **E3 stair z-bridging `_e3Chain` :554-589 + `getStairGroups` :147-211** — a tray riser is a real geometric element with a real length; it needs no storey-bridging inference at all.
- **E4 exit doors :713-733, `escapeRoute` :1362-1404, `NON_ROOM_DOOR_NAMES` :107-112** — fire-escape + lift-name semantics. Nothing to do with trays.
- **The whole floor-legality stack** — `_pointWalkable` :969-1009, `_chordIllegalCount` :1016-1028, `_legalizePath` :1119-1135, `_detourCandidates/_detourDijkstra/_detourForChord` :1050-1112, `_simplifyLOS` :1228-1237, `_astarHop` :1244-1282, `_buildPolyline` :1288-1328. It answers "does a human's footstep land on real slab, per storey, in 2D". A tray is suspended and crosses slabs; its validity condition is joint continuity. `_detourCandidates` (:1063) admits only `doorwp`/`spine`/`circ` — door-portal midpoints as the entire waypoint vocabulary.
- **`common/hallway_backbone.js` — the ENTIRE module (733 lines)**: `buildBackbone` :460-531, `correlateDoorEdges` :225, `joinDoorways` :256 (`minDoorsForHallway: 3`), `growToWall` :275, `terminateAtStair` :358, `classifyCorridorRooms` :~660-712, `DEFAULT_PROFILE`. A corridor centreline *hypothesised* from ≥3 collinear doors — geometry that was never modelled. **Cable containment IS already explicitly modelled as real elements. There is nothing to infer a spine for, and inferring one would replace real geometry with a guess.**
- **`viewer/tour.js:_buildGraphRouteInner` — :437-643** — an itinerary planner for a camera (label-string room classification :453-454, area-ranked "drama" stops :494-495, per-storey budget `K` :461, `pause:'room'|'storey'` beats, `§MAJORITY-LEGAL` reject :635-640).
- **`build/room_walker.js` / `scripts/compile_rooms.py` — whole files.** Wall-raster flood-fill room compilation (`flood_rooms` py:553, `partition_by_doors` py:740, `_inscribed_rect`, `OPEN_PERIM_FACTOR`, `DOOR_SHORTFALL_RATIO 0.15`). **No nodes, no edges, no weights, no graph search** — the nearest-door BFS is over a wall-occupancy *bitmap*. Its own `§DOOR-PARTITION` guard (py:671-710) exists because flood-fill can't find rooms on sparse models — a failure mode with no tray analogue. Nothing here is a reuse candidate.

### Negative result worth recording
`grep -E 'dijkstra|astar|heapq|priority_queue|shortest_path'` across every non-vendored `.py` in both
repos returns **no graph search at all** on the Python side (only `deploy/boq_export.py`, a rate table
that happens to mention cable tray). Any compiler-side tray graph is greenfield Python; the reusable
search code is all JS.

The room precedent also carries a warning worth copying: `navigate_find.js` compiles rooms from
walls/doors and **refuses honestly (`roomsWritten=0`) if the building lacks them — never invents
rooms.** A tray walker must refuse the same way rather than bridge a gap the model does not have.

## §OPEN — ✅ items 1-3 CLOSED 2026-07-30 (survey); item 4 still open
1. ✅ **Does `rel_adjacency` reproduce the authored graph?** **NO** — precision 74.58%, recall 73.81%.
   → §AUTHORED_VS_DERIVED. The errors are *systematic*, not noise, and a better geometric rule exists.
2. ✅ **Connected-component count of the authored graph.** **1,486 components over connected nodes;
   1,945 including the 459 isolated products. Largest = 2,134 elements = 10.16% of the model.**
   → §GRAPH_MEASURED. The network is **fragmented — cables cannot be routed end to end** over the
   authored graph alone.
3. ✅ **Real pull lengths.** Measured, named pairs, in metres → §PULL_LENGTHS. **⚠ CORRECTION to this
   item's own instruction:** do **not** use `element_transforms` centres — that column is the
   placement ORIGIN, median **11.31 m** away from the element on this model. Use `elements_rtree`.
   → §SUBSTRATE_LANDMINE.
4. ⛔ **Ask the engineer which of §WHAT_REVIT_CANNOT_DO he actually meant.** One question, saves a
   lane. Not answerable from the data — needs the user.

## §STATUS
- 2026-07-30 — survey agent dispatched (graph statistics, authored-vs-derived comparison, reuse map).
  Findings land in this file. **Nothing implemented. No extractor change made.**
- 2026-07-30 — **survey COMPLETE.** §OPEN 1-3 closed with measurements; §REUSE_FROM_ROOMS replaced with
  a verified file:symbol map; four new sections appended (§GRAPH_MEASURED, §AUTHORED_VS_DERIVED,
  §PULL_LENGTHS, §SUBSTRATE_LANDMINE) plus §ANSWER and §SPEC. **Still nothing implemented; extractor,
  viewer and modeller untouched.** Logs: `§TRAY_AUTHORED`, `§TRAY_CMP`, `§TRAY_GEO`, `§TRAY_PATH`,
  `§TRAY_EDGEQ`, `§TRAY_ALTRULE`, `§TRAY_RULEC`, `§TRAY_NN`, `§TRAY_CTR`.
- Cross-references: `prompts/IFC_LARGE_PRIVATE_STRESS_TEST.md` §KUL004 (ports stripped, and why that
  may need reversing), §KUL012 (the complete 87,333-element OVERALL DB),
  `project_spatial_dependency_graph.md` (Spatial MRP — the same graph thinking, ERP side).

---

# SURVEY RESULTS — 2026-07-30 (read-only; every number below is counted, none estimated)

Scripts + logs are scratch (not committed, per the no-`.db`/no-scratch-in-repo rule); each claim
carries its `§` log line so it is re-derivable. Sources read: `CONTAINMENT.ifc` (raw STEP, streamed),
`KUL_CONTAINMENT_extracted.db` (`mode=ro`), `KUL_EQUIPMENT_extracted.db`, `Duplex_mep_extracted.db`,
`extractIFCtoDB.py`, `strip_ifc_nonessential.py`, `build/disc_walker.js`, `scripts/witness_walkback_mep.js`.

## §GRAPH_MEASURED — what the authored port graph actually is

Parsed straight from the STEP file (774,052 lines / **260,547 rooted objects** — consistent with
§KUL004's 774,041 entities + the header/footer lines). The coordinator's §MEASURED table is **exactly
right**, confirmed independently: `IFCDISTRIBUTIONPORT` 43,187 · `IFCRELCONNECTSPORTTOELEMENT` 43,187 ·
`IFCRELCONNECTSPORTS` 19,142 · `IFCFLOWSEGMENT` 11,441 · `IFCFLOWFITTING` 9,568. Nothing to correct.

```
§TRAY_AUTHORED ports=43187 rel_port_to_element=43187 rel_connects_ports=19142
§TRAY_AUTHORED ports_bound_to_element=43187 ports_unbound=0 conflicting_bindings=0
§TRAY_AUTHORED element_edges_unique=19142 port_pair_rows=19142 self_loops=0 unresolvable=0
§TRAY_AUTHORED graph_nodes=20550 product_elements=21009 products_not_in_graph=459
§TRAY_AUTHORED node_class_histogram=[["IFCFLOWSEGMENT",11111],["IFCFLOWFITTING",9439]]
```

**The graph resolves perfectly.** Every one of the 43,187 ports binds to exactly one element with zero
conflicts; every one of the 19,142 port-pairs resolves to a distinct element-to-element edge — no self
loops, no dangling references, no de-duplication needed. This is a *clean, complete, hand-authored
topology*. It is not partial data we would have to repair.

### The headline number: **1,486 components. It is NOT one navigable network.**
```
§TRAY_AUTHORED components_connected_only=1486 sizes_top15=[2134,667,458,299,298,260,251,229,218,213,211,211,182,180,171] sum=20550
§TRAY_AUTHORED components_incl_isolated=1945 singletons=459 size2=140 size_ge10=291
§TRAY_AUTHORED top1_pct_of_products=10.16
```
The largest connected run is **2,134 elements = 10.16%** of the model. 459 products are completely
isolated (no port connection at all); 140 more are lone pairs. **A cable cannot be pulled end to end
across this building on the authored graph** — any whole-building pull length would require bridging a
gap the model does not assert. That is forbidden by the Prime Rule, and it is the single most important
finding for scoping this lane.

### Shape of the network — it is a chain graph, not a mesh
```
§TRAY_AUTHORED degree_histogram=[[1,3891],[2,15622],[3,999],[4,38]]
§TRAY_AUTHORED deadends_deg1=3891 deg0_products=459 max_degree=4
§TRAY_AUTHORED ports_per_element_histogram=[[1,11],[2,19862],[3,1089],[4,45],[5,1]]
§TRAY_AUTHORED ports_with_a_connection=38284 ports_dangling=4903
```
76% of nodes have degree exactly 2 (a run passing through). **Max degree is 4** — tees (999 at degree 3)
and crosses (38 at degree 4) are the only branching. **3,891 dead ends** — these are the real cable
drop / termination candidates, and they are *data*, not a defect. **4,903 ports are authored but
connected to nothing** (11.4% of all ports) — open tray ends, i.e. where the containment stops.

### Geometric cross-validation — the authored graph is TRUE, not just present
```
§TRAY_GEO authored_AABB_gap_m n=19142 p50=0.0000 p90=0.0000 p99=0.0000 max=0.3311 zero=19139
§TRAY_GEO authored_centre_dist_via_RTREE n=19142 p05=0.091 p50=0.471 p95=4.375 max=27.864
```
**19,139 of 19,142 authored edges have a literally ZERO AABB gap** — the two elements physically touch.
Worst case in the whole file is 0.33 m. The authored topology and the geometry agree. This is the
strongest single argument for persisting ports: they are free, exact, and independently confirmed.

### The full model carries even more of it
`grep` on the 2.0 GB `OVERALL.ifc`: `IFCDISTRIBUTIONPORT` **55,561** · `IFCRELCONNECTSPORTTOELEMENT`
**55,561** · `IFCRELCONNECTSPORTS` **24,366**. So the complete E-series model has ~27% more authored
edges than CONTAINMENT alone (the extra are the EQUIPMENT-side ports — panel/busway connection points,
i.e. exactly the *sources and sinks* a pull-length query needs at the ends of a tray route).

## §AUTHORED_VS_DERIVED — geometry does NOT reproduce the authored graph, and the failure is systematic

```
§TRAY_CMP authored=19142 derived=18944 intersection=14129
§TRAY_CMP precision_derived_edges_that_are_authored=74.58% recall_authored_edges_found_by_derived=73.81%
§TRAY_CMP authored_only=5013 derived_only=4815
```
F1 = 74.19. **Answer to §OPEN 1: no.** But "18,944 ≈ 19,142" was a coincidence of totals — the two sets
differ on ~5,000 edges in *each* direction. Both error classes have a single, diagnosable cause.

### Why face-touch MISSES 5,013 real joints: MEP fittings interpenetrate, they do not abut
```
§TRAY_EDGEQ authored_only n=5013 why_face_touch_missed=[["gap_or_clash_exceeds_tol_30mm",5012],["overlap_on_other_axes_below_20mm_min_overlap",1]]
§TRAY_EDGEQ authored_only AABB_gap_m p50=0.0000 p95=0.0000 max=0.3311 zero=5011
```
**5,011 of the 5,013 missed edges have a ZERO AABB gap — the elements are touching.** `_face_touch`
rejects them anyway, because it picks the axis of *smallest* |overlap| and requires it to be ≤30 mm:
when a bend fitting's box overlaps a tray segment's box substantially on **all three** axes, no axis is
near zero, so the rule classifies a genuine coupling as a "deep clash" and discards it. The rule is
correct for its design case (two walls back to back). It is **structurally wrong for MEP**, where the
joint is a socket/flange overlap. This is a real, named defect in `derive_adjacency`, not a tolerance
that needs widening.

### Why face-touch ADDS 4,815 edges the authored graph denies: parallel trays resting on each other
Discriminator, measured per edge: is the touch axis the element's own **longest** axis (an end-butt) or
a transverse one (a tray laid across/against another)?
```
§TRAY_EDGEQ confirmed_both  n=14129 END_BUTT=12792 (90.5%) TRANSVERSE=1337 (9.5%)
§TRAY_EDGEQ confirmed_both  contact_m2 p50=0.0200  gap_mm p50=0.492 p95=1.961
§TRAY_EDGEQ confirmed_both  class_pairs=[["Seg|Fit",7112],["Fit|Seg",6700],["Fit|Fit",293],["Seg|Seg",24]]
§TRAY_EDGEQ derived_only    n=4815  END_BUTT=2262 (47.0%) TRANSVERSE=2553 (53.0%)
§TRAY_EDGEQ derived_only    contact_m2 p50=0.0171  gap_mm p50=6.832 p95=27.345
§TRAY_EDGEQ derived_only    class_pairs=[["Seg|Seg",2359],["Fit|Fit",995],["Fit|Seg",750],["Seg|Fit",711]]
```
Three independent signatures separate them:
1. **Class pair.** Real containment topology is *always* segment→fitting→segment: **13,812 of 14,129
   (97.8%)** confirmed edges are `Segment|Fitting`, and only **24** are `Segment|Segment`. The
   derived-only set is *led* by `Segment|Segment` (2,359) — parallel trays in the same cable route,
   physically touching, electrically two different runs.
2. **Touch orientation.** 90.5% end-butt when real, 47.0% when spurious.
3. **Gap.** p50 0.49 mm when real (a modelled coupling), 6.83 mm when spurious (an incidental contact
   living off the 30 mm tolerance).

### A better purely-geometric rule exists — and it nearly matches the authored graph
Scored against the 19,142 authored edges as ground truth (the first time such an oracle has existed on
this project — `witness_walkback_mep.js` states, correctly for its own substrates, that no
recorded-topology oracle was available):
```
§TRAY_ALTRULE RULE_A aabb_proximity_30mm                        edges=26629 precision=71.88% recall=99.99% F1=83.63
§TRAY_ALTRULE RULE_B  + class_pair==Segment|Fitting             edges=21166 precision=88.85% recall=98.24% F1=93.31
§TRAY_ALTRULE RULE_C  + AABBs actually intersect                edges=20106 precision=93.53% recall=98.24% F1=95.83
§TRAY_ALTRULE RULE_E  C + keep 2 nearest segments per fitting    edges=18119 precision=95.34% recall=90.25% F1=92.72
```
Shipped `derive_adjacency` = **F1 74.19**. RULE_C = **F1 95.83** from three lines of change. Note also
that RULE_A recovers **99.99%** of authored edges — i.e. **essentially every authored connection is
geometrically discoverable**; the shipped rule's low recall is entirely its own filter, not missing data.
RULE_E confirms the degree cap is the wrong lever (`§TRAY_ALTRULE authored_degree_by_class
IfcFlowFitting [[1,518],[2,7892],[3,992],[4,37]]` — capping at 2 destroys every tee and cross).

### And yet: a better geometric graph is still not the authored graph
```
§TRAY_RULEC AUTHORED         edges=19142 components=1945 largest_share=10.16% singletons=459
§TRAY_RULEC RULE_C           edges=20106 components=1540 largest_share=53.90% singletons=546
§TRAY_RULEC AUTH_UNION_RULEC edges=20442 components=1266 largest_share=57.03% singletons=307
§TRAY_EDGEQ union_components(authored ∪ shipped rel_adjacency)=589 largest_share=86.60%
```
Read that last line carefully — **the shipped rule's union looks "better connected" (86.6%) precisely
because its false positives glue unrelated runs together.** Higher connectivity is *not* the quality
metric here; a spurious side-contact between two parallel trays creates a route a cable cannot take.
This is the trap to avoid if anyone is ever tempted to tune for a bigger component.

### Verdict on §OPEN 1
**We need the ports.** They are exact (19,142/19,142 resolve), independently geometry-confirmed
(19,139 zero-gap), free (already in the file), and they carry a distinction no bbox rule can ever
recover: *this* contact is a coupling, *that* one is two trays touching. Geometry with a fixed
`derive_adjacency` (RULE_C) is a good **fallback for models that ship no ports** — and it should be
built, because most of the fleet has none — but it is a 93.5%-precision approximation of something we
are currently deleting at 100% fidelity.

## §PULL_LENGTHS — yes, the graph is metrically usable; here are real metres

Node position = `elements_rtree` AABB centre. Edge weight = Euclidean centre-to-centre (a centreline
approximation over a chain whose median hop is 0.47 m). **Not** `element_transforms.center` — see
§SUBSTRATE_LANDMINE.

```
§TRAY_PATH authored_edges_with_geometry=19142 total_centreline_sum_m=22406.3
§TRAY_PATH edge_len_m p05=0.091 p50=0.471 p95=4.375 max=27.864
§TRAY_PATH longest_elements=[[55.53,"Cable Tray with Fittings:Trunking:50354710"],[55.15,"…:98393108"],[54.09,"…:50354686"]]
```
**~22.4 km of containment centreline in this one model.** (Single longest element: a 55.53 m trunking run.)

### Largest authored component — a measured 324.71 m pull
```
§TRAY_PATH comp0 n=2134 bbox_span=(100.0,84.2,11.5)m storeys=[["Level 0",2052],["Level 0.5 Plenum - Mezzanine",77],["0.0 - G.F.L",4],["Level 1",1]]
§TRAY_PATH comp0 DIAMETER pull_len=324.71m hops=249 straight_line=41.06m tortuosity=7.91
§TRAY_PATH comp0   A=2W5MtY1$rFROvHu5DQvtiD [Cable Tray with Fittings:Trunking:50354684] storey=Level 0.5 Plenum - Mezzanine
§TRAY_PATH comp0   B=2Tm3yzBML8PRVWWPREg0ua [Cable Tray with Fittings:Trunking:71598857] storey=Level 0
§TRAY_PATH comp0   reachable_from_A=2134/2134
```
**324.71 m of cable to travel 41.06 m of straight-line distance — a tortuosity of 7.91×.** That single
number is the business case for this lane: nobody estimates a 7.9× detour factor by eye off a drawing,
and getting it wrong is the difference between a drum of cable that reaches and one that does not.

Three more named real pairs in the same component (all fully reachable, no bridging):

| from | to | straight-line | **pull length** | tortuosity |
|---|---|---|---|---|
| `…Trunking:93688505` | `…Trunking:73853020` | 100.17 m | **169.20 m** | 1.69× |
| `…Trunking:50355079` | `…Trunking:116716247` | 91.94 m | **137.76 m** | 1.50× |
| `…Cable Tray:117484455` | `…Trunking:50349760` | 94.14 m | **184.12 m** | 1.96× |

Other components (each internally 100% reachable): `comp1` n=667, diameter **141.00 m** over 140 hops
(straight 50.43 m, 2.80×); `comp2` n=458, diameter **218.65 m** over 168 hops (straight 70.28 m, 3.11×).

### Cross-component: honestly UNREACHABLE
```
§TRAY_PATH cross_component_reach: from comp0 node, reachable=2134 ; comp1 node in reach=False
§TRAY_PATH components_spanning_multiple_storeys=154/1945
```
A query between two islands must return **UNREACHABLE with the island ids**, never a number. 154
components do genuinely span storeys (real risers exist and are modelled) — so vertical routing needs
no stair-style inference, which is why room-graph E3 must not be copied.

For reference, the best *fallback* graph (authored ∪ RULE_C) reaches a single 11,981-element component
spanning 103.2 × 93.7 × 18.9 m across all six main storeys, whose diameter is a **401.30 m** pull over
209 hops (`Cable Tray:116791876` @ Level 0 → `Trunking:98393118` @ COOLING TOWER, straight 87.57 m,
4.58×). Useful as an upper bound; **not** to be presented as measured truth, since ~6.5% of RULE_C's
edges are false.

## §SUBSTRATE_LANDMINE — `element_transforms.center_*` is the placement ORIGIN, not the element

This is the most consequential incidental finding, and it invalidates a step written into §OPEN 3 itself.

```
§TRAY_GEO transform_source=[('ifc_extract',21009)]
§TRAY_GEO |et.center - rtree.center| n=21009 p50=11.3127 p95=63.3970 max=86.4332 exact_zero=0
§TRAY_GEO rtree_extent_X p50=0.200 p95=2.408 max=55.528   (elements are SMALL; the offset is not a half-length)
```
Cause, read from source not guessed: `extractIFCtoDB.py:1268` sets `center = mat4[:3, 3]` — the
**translation column of the placement matrix**. The AABB (`elements_rtree`, line 1437) is computed
separately from the rotated local vertices. For Revit-exported MEP whose family geometry is authored in
project coordinates, the two are unrelated. Both get the same `§NORMALIZE` offset (lines 1555-1566), so
this is not a normalisation bug — it is what the column *means*.

Fleet-wide, not KUL-specific:
```
§TRAY_CTR KUL_CONTAINMENT_extracted.db n=21009 p50=11.3127 p95=63.3970 max=86.4332 over1m=11536
§TRAY_CTR KUL_EQUIPMENT_extracted.db   n=292   p50=1.1425  p95=3.8556  max=15.7231 over1m=167
§TRAY_CTR Duplex_mep_extracted.db      n=1169  p50=0.0967  p95=3.6400  max=18.0301 over1m=248
```
(`Terminal_extracted.db` / `Duplex_extracted.db` under `deploy/buildings/` predate `elements_rtree` and
could not be checked this way.)

### This already breaks the existing MEP router, measurably
`build/disc_walker.js:routeChains` → `_loadXYZ` / `_loadXYZB` read
`element_transforms.center_x/y/z` (+ `bbox_*`) and nn-pair each fitting to its nearest segment. Scored
against the authored oracle on KUL_CONTAINMENT — the first time `routeChains`' strategy has had a
recorded-topology oracle at all:
```
§TRAY_NN pos=et    face=0 bound=1.0m paired=2    noNbr=9566 precision_vs_authored=0.00% edge_recall=0.00%
§TRAY_NN pos=et    face=1 bound=3.0m paired=657  noNbr=8911 precision_vs_authored=0.00% edge_recall=0.00%
§TRAY_NN pos=rtree face=0 bound=1.0m paired=9063 noNbr=505  precision_vs_authored=78.89% edge_recall=37.35%
§TRAY_NN pos=rtree face=1 bound=1.0m paired=9525 noNbr=43   precision_vs_authored=90.07% edge_recall=44.82%
```
**With the column it actually reads, the engine pairs 2 of 9,568 fittings and gets 0.00% precision. With
`elements_rtree` centres and its own `_nnPassFace` line-distance mode it gets 90.07%.** Same algorithm,
same building — the only variable is the position substrate. `routeChains` is not broken; it is being
fed a column that does not mean what it is used for. (Its `§FACE` mode, `_segLine`/`_ptSeg`/`_perpHalf`,
is the right idea and earns +11 points of precision — keep it.)

Also note the structural ceiling: nn gives **one** leg per fitting, so edge recall plateaus at ~45%
however good the positions are. `witness_walkback_mep.js` already documents this honestly as a
"junction-degree coverage" gap. A tray graph needs *all* legs — the measured degree profile is
`[[1,518],[2,7892],[3,992],[4,37]]` — so an all-pairs rule (RULE_C) or the ports, not nn.

**Scope note:** this section is a finding, not a change. No fix applied to `extractIFCtoDB.py`,
`disc_walker.js`, or any consumer. Whether other `element_transforms.center` consumers are affected is
**unknown** — not surveyed here (see §THREE_WAY (c)).

## §ANSWER — what the BIM engineer most likely meant, in plain language

He is almost certainly talking about **#1, cable pull lengths and routing through the containment**, and
he is right that the big tools do not do it. Ranked by what the measurements support:

1. **Cable pull lengths / which route a cable takes through the tray.** (I) Revit's electrical circuits
   are *logical* — a panel-to-load relationship with no physical path through containment; the tray
   model and the circuit model are separate universes in the same file. (M) So the question "how many
   metres, following the trays" is a shortest-path problem, and this building's answer is **324.71 m
   between two elements 41.06 m apart** — a **7.91×** factor no one eyeballs. (M) 22.4 km of centreline
   and **3,891 authored dead ends** (drop/termination candidates) is a spreadsheet nobody wants to keep
   by hand. This is where his manual work goes.
2. **Auto-routing the tray itself.** (I) Revit's *Generate Layout* auto-routes pipe and duct; there is
   no equivalent for cable tray or conduit — every run is drawn by hand. (M) 11,441 segments + 9,568
   fittings, and the model shows the human's fingerprints all over it: he *did* wire up the connectors
   (19,142 of them, 19,139 geometrically exact), and he left **4,903 ports open** and the network in
   **1,486 pieces**. That is what hand-drawn containment looks like.
3. **Tray fill %, power/data segregation, bend radius.** (I) Manual checks. (M) Not measurable from
   this file — CONTAINMENT carries no cables at all, only the containment. Fill% needs a cable schedule
   we do not have.

**The sharpest thing to tell him:** *"your model already contains the answer — you wired 19,142
connectors and we've been throwing them away on import. What you cannot get from Revit, you can get
from your own IFC."* And the honest caveat in the same breath: the network is in 1,486 pieces, so
today it answers "how long within this run", not "panel to rack across the building" — the two ends
must be in the same run, or the missing links have to be closed in the model first.

**Still unconfirmed with him. Ask.** (§OPEN 4)

## §SPEC — what we would build (spec-first; nothing implemented)

**Claim to witness before any code:** *W-TRAY-GRAPH — for a building whose IFC carries
`IfcRelConnectsPorts`, the compiled tray graph reproduces the authored edge set exactly (edge count
equal, set difference empty), reports its component count without bridging, and returns a pull length
in metres for two elements in the same component / `UNREACHABLE` for two in different ones.*

### T1 — persist the ports (the one extractor change, and it is small)
`extractIFCtoDB.py` already declares `port_elements` (:184) and `port_connections` (:192) and never
writes them. Fill them: walk `IfcRelConnectsPortToElement` → `port_elements(port_guid, element_guid,
flow_direction, local_x/y/z)`; walk `IfcRelConnectsPorts` → `port_connections(port_a_guid,
port_b_guid)`. `IfcDistributionPort` **stays** in `NON_GEOMETRIC_CLASSES` (it has no mesh — that list is
about geometry, and it is correct); ports are read as *relationships*, on the same footing as
`IfcRelVoidsElement` / `IfcRelFillsElement`, which the extractor already handles. Cost: 43,187 + 19,142
rows for CONTAINMENT. Justified by §GRAPH_MEASURED + §AUTHORED_VS_DERIVED, not by preference.

### T2 — REVERSE the port strip in `strip_ifc_nonessential.py --tier model`
`§KUL004`'s stated ground was *"nothing consumes them"*. After T1 something does. Remove
`'IFCDISTRIBUTIONPORT'`, `'IFCRELCONNECTSPORTTOELEMENT'`, `'IFCRELCONNECTSPORTS'` from the drop set
(the file's own line 80) — or gate them behind a flag. **The §KUL004 losslessness proof stays valid; it
proved losslessness against an extractor that ignored ports, and that premise is what changes.** Cost:
CONTAINMENT's model tier goes from -45.2% to roughly -31.6% (ports were 13.6% of the file). The
`--tier meta` (pset-only) tier is unaffected and remains the safe default for RAM-driven stripping.

### T3 — fix `derive_adjacency` for MEP (the fallback path — most of the fleet ships no ports)
Add RULE_C as an MEP branch of `_face_touch`, provenance `derived:mep-coupling` so it never masquerades
as a face-touch: candidate pairs from the rtree within 30 mm, **require** the class pair to be
segment↔fitting, **require** the AABBs to actually intersect, and **drop** the smallest-|overlap| ≤ tol
test that is currently rejecting 5,011 zero-gap real joints. Measured: F1 74.19 → **95.83**. Do **not**
cap degree (kills 1,029 tees/crosses). Do **not** widen the tolerance — the false positives live in the
existing 30 mm, they are not beyond it.

### T4 — the tray graph artifact + walker
- **Nodes** = `elements_meta` rows whose `ifc_class` ∈ {`IfcFlowSegment`,`IfcFlowFitting`,
  `IfcCableCarrierSegment`,`IfcCableCarrierFitting`}, position = **`elements_rtree` AABB centre**
  (§SUBSTRATE_LANDMINE — never `element_transforms.center`).
- **Edges** = `port_connections ⋈ port_elements` when present; else T3's derived edges; **provenance
  stamped per edge**, and the two are never silently mixed in a reported number.
- **Weight** = Euclidean centre-to-centre in **3D** metres, no penalty multiplier, no XY-only hypot.
- **Artifact** = a compiled, version-stamped table (`tray_graph` + `tray_graph_meta.version` /
  `TRAY_GRAPH_V`), following `navigate_find.js:_ensureRoomsCore`'s staleness pattern (:970-988, stamp
  :1084-1098) so consumers self-heal.
- **Search** = `viewer/navigate_grid.js:graphAStar` (:411-479) unchanged, with a tray-derived
  `template`. Two known adaptations: its heuristic is 2D (`hx,hy` at :462-463) — still **admissible**
  for a 3D metric graph (2D ≤ 3D ≤ path cost), so correctness holds, it is just less informed; and its
  open list is a linear scan, so swap in `room_graph.js:_Heap` (:1158-1161) before running 21k nodes.
  `nearestNode` (:481-489) is 2D and **must** take z for tray picking (risers stack).
- **Islands** = `room_graph.js:fullConnectivity` (:818-861), seeded from **edge endpoints** per its own
  `§NOT-EVERY-NODE-IS-A-VERTEX` lesson. **Report them. Never bridge them.**
- **Refuse honestly** — no `IfcRelConnectsPorts` and no MEP class rows → `traysWritten=0` with a reason,
  exactly as `ensureRooms` refuses a building with no walls/doors. An `UNREACHABLE` answer names both
  island ids and their sizes.
- Oracle for the witness: the authored edge set itself where present, and it must **fail loud if its own
  reference data is empty** (a building with zero `IfcCableCarrier*`/flow rows must not report
  "0 islands, all connected").

### T5 — the deliverable a BIM engineer would actually use
`pullLength(fromGuid, toGuid)` → `{metres, hops, path[], straightLine, tortuosity, provenance}` or
`{unreachable: true, islandA, islandB, sizeA, sizeB}`; plus a per-island report (element count, storeys
spanned, centreline metres, dead-end count) and the dead-end list as termination candidates. That last
one is directly the 3,891 number, and it is the cheapest useful thing in this whole spec.

### Sequencing
T1 → T4 → T5 answers the user's question on KUL with exact data. T3 is the independent, fleet-wide win
(and the only path for models with no ports). T2 must land with T1 or the strip silently re-breaks it.
**§SUBSTRATE_LANDMINE is NOT in this spec** — it is a separate finding affecting existing engines, and
it deserves its own scoped session rather than being smuggled in here.

## §THREE_WAY — measured / industry knowledge / genuinely unknown

**(a) MEASURED here, on these files, this session** — authored graph resolution (43,187 ports, 19,142
edges, zero conflicts); **1,486 / 1,945 components, largest 2,134 = 10.16%**; degree profile
`[[1,3891],[2,15622],[3,999],[4,38]]`; 4,903 dangling ports; 19,139/19,142 zero-gap geometric
confirmation; precision **74.58%** / recall **73.81%** of `rel_adjacency` vs authored, and both root
causes (5,011 zero-gap misses from the smallest-overlap test; 53% transverse + `Segment|Segment`-led
false positives); RULE_A/B/C/E scores up to **F1 95.83**; **22,406.3 m** total centreline; the named
pull lengths (**324.71 m / 41.06 m / 7.91×**, 169.20, 137.76, 184.12, 141.00, 218.65 m);
`element_transforms.center` p50 **11.31 m** off the AABB centre with the source line that causes it
(`:1268`) and the fleet spread; `routeChains`' nn strategy at **0.00%** precision on the column it reads
vs **90.07%** on rtree centres; OVERALL.ifc's 55,561 ports / 24,366 connections; ports absent from
`CONTAINMENT_model.ifc` (grep = 0) confirming §KUL004's strip; and every §REUSE_FROM_ROOMS
file:symbol/line above, spot-checked against source.

**(b) INDUSTRY KNOWLEDGE — asserted, NOT measured by us** — Revit's *Generate Layout* auto-routes pipe
and duct but has no cable-tray/conduit equivalent; Revit electrical circuits are logical (panel→load)
and do not path through containment, so pull lengths and tray fill are manual/Excel work; tray fill %,
power/data segregation and bend-radius checks are manual; `Oglaend System` is a cable-ladder
manufacturer and "containment" is electrical-trade shorthand for tray/ladder/basket/trunking/conduit.
**We ran no Revit and read no Autodesk source. Do not upgrade any of these to "measured".**

**(c) GENUINELY UNKNOWN** — (1) **Which of the three the engineer meant** — §OPEN 4, needs him. (2)
**Whether the 1,486 islands are a modelling artifact or physical reality** — some are surely real
(containment genuinely stops), some are surely unwired connectors; nothing in the file distinguishes
them, and no amount of geometry will. (3) **Whether the other `element_transforms.center` consumers are
affected** by §SUBSTRATE_LANDMINE — `routeChains` demonstrably is; every other consumer is unaudited.
(4) **Tray fill / segregation** — CONTAINMENT carries containment only, no cables; unanswerable without
a cable schedule. (5) **Whether EQUIPMENT's 292 elements + their ports connect the tray network to
panels** — the panel-to-rack query needs that join and it was not attempted here (EQUIPMENT is a
separate DB; the merged 87,333-element OVERALL DB exists per §KUL012 but its port tables are empty for
the same T1 reason). (6) **RULE_C's transferability** — it is tuned and scored on ONE building; whether
F1 ~96 holds on Terminal/Duplex MEP is untested.
