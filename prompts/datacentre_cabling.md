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
`rel_adjacency = 18,944` rows, derived from bbox proximity (`extractIFCtoDB.py` reads
`elements_meta ⋈ elements_rtree`). Note **18,944 vs 19,142 — close, but not equal.** Whether the
derived graph reproduces the authored one is the pivotal open measurement (see §OPEN).

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

## §REUSE_FROM_ROOMS — the user's steer, and why it holds
Same shape, different graph:

| rooms (works today) | cable tray (proposed) |
|---|---|
| rooms = nodes | tray segments/fittings = nodes |
| doors = edges | `IFCRELCONNECTSPORTS` = edges |
| A* over the room graph → fly path | A* over the tray graph → cable pull route |
| path length = walk distance | path length = **cable length** |

Substrate to read before designing: `viewer/navigate_find.js` (`ensureRooms`, `getRoomGraph`),
`viewer/tour.js` (`_buildGraphRoute`, A* polyline flight), `common/room_graph.js`,
`common/hallway_backbone.js`, and in this repo `build/room_walker.js` / `scripts/compile_rooms.py`.
⚠ **`docs/internal/WalkerDoctrine.md` is LOCKED** — read it, do not re-litigate it.

The precedent also carries a warning worth copying: `navigate_find.js` compiles rooms from
walls/doors and **"refuses honestly (roomsWritten=0) if the building lacks them — never invents
rooms."** A tray walker must refuse the same way rather than bridge a gap the model does not have.

## §OPEN — the measurements that decide everything
1. **Does `rel_adjacency` (18,944, geometric) reproduce the authored graph (19,142)?** Precision and
   recall, with concrete examples each set has that the other lacks. If geometry suffices, we need no
   extractor change. If not, we must stop discarding ports.
2. **Connected-component count of the authored graph.** The headline number: if the tray network is
   one navigable component, cables can be routed end to end; if it is many islands, they cannot, and
   any "pull length" would require inventing a bridge — which is forbidden.
3. **Real pull lengths.** Shortest path between distant tray elements, summing true segment lengths
   from `element_transforms` centres + `bbox_*`, reported in metres for named real pairs.
4. **Ask the engineer which of §WHAT_REVIT_CANNOT_DO he actually meant.** One question, saves a lane.

## §STATUS
- 2026-07-30 — survey agent dispatched (graph statistics, authored-vs-derived comparison, reuse map).
  Findings land in this file. **Nothing implemented. No extractor change made.**
- Cross-references: `prompts/IFC_LARGE_PRIVATE_STRESS_TEST.md` §KUL004 (ports stripped, and why that
  may need reversing), §KUL012 (the complete 87,333-element OVERALL DB),
  `project_spatial_dependency_graph.md` (Spatial MRP — the same graph thinking, ERP side).
