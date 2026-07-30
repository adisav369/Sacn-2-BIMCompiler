# ⚠ DO NOT REMOVE — LANE: JKR / SKATA / PeDATA asset-code compliance
# Scope: (a) can we SAY whether a dataset is compliant, (b) a feature to MAKE it compliant.
# PRIME RULE: EXTRACT ONLY. A classification code is authored or extracted — never guessed from a name.
# Read the log after every run. Opened 2026-07-30 from a user ask; nothing built yet.

## §WHAT SKATA IS (researched 2026-07-30 — web summary only, NOT the primary spec)
**SKATA = Sistem Kod Aset Tak Alih** — Malaysia's Fixed/Immovable Asset Code System. Paired with
**PeDATA** (government asset collection + labelling). It is a *classification/coding* standard covering:
building codes · building level codes · space/area codes · component object codes.

**The mandate:** since **1 July 2025**, BIM is mandatory for Malaysian projects ≥ **RM10 million**
(public *and* private) under the Construction 4.0 Strategic Plan. Deliverables must follow **CIDB BIM
Guidelines** and hand over **LOD 500** models carrying full asset data conforming to **PeDATA and SKATA**
classification codes. JKR (Jabatan Kerja Raya) publishes its own BIM Requirements/Piawaian on top.

⛔ **BLOCKED — we do not have the primary source.** Everything above is a secondary web summary. The
*shape of a valid code* (field widths, segment order, allowed values) is not known to this repo and cannot
be invented. Before any validator is written, someone must supply the actual JKR/CIDB SKATA + PeDATA
specification. **This blocks the CHECKER. It does NOT block the schema column (see §PHASING).**

## §THIS IS A DIFFERENT AXIS FROM OUR EXISTING COMPLIANCE WORK
`docs/archive/STANDARDS_COMPLIANCE_SRS.md` and `prompts/UBBL_RULES_{GATE,RECON}.md` cover **UBBL** —
*is the building lawful* (geometric/dimensional: room areas, egress, corridor widths). SKATA/PeDATA is
*is the asset data coded correctly for government handover* (classification/nomenclature). Same word
"compliance", orthogonal problems. **Zero mentions of SKATA/PeDATA/CIDB-codes exist in this repo**
(grepped 2026-07-30). This lane is new ground, not an extension of the UBBL lane.

## §ARE OUR DATASETS COMPLIANT? — measured 2026-07-30
**No — and not in the "fails a check" sense. There is nowhere to put the answer.**

```
sqlite> .schema elements_meta            -- JKR_extracted.db, and identical in every shipped building
CREATE TABLE elements_meta (guid TEXT PRIMARY KEY, ifc_class TEXT, element_name TEXT,
  storey TEXT, discipline TEXT, material_name TEXT, material_rgba TEXT, building TEXT);
```
**No classification-code column of any kind.** `spatial_structure` carries `object_type` +
`predefined_type`, but those are IFC enum fields, not asset codes. So the honest status is: *the extract
discards asset classification entirely* — the same shape as the T1b finding in
`RESUME_4D_TRUTH_AND_BE_HERE_WHEN.md` §DIAGNOSIS D1 (a planner stated it; our schema dropped it).

**And our own JKR building already visibly fails level coding.** `JKR_extracted.db` carries TWO storey
naming conventions in ONE model:
```
01 Ground Floor Level   |  01 Aras Satu
02 1st Floor Level      |  02 Aras Dua
03 Water Tank Floor Level  |  00 Aras Tanah  |  03 Aras Rasuk Bumbung
04 Water Tank Roof Level
```
English and Malay, mixed, for the same building. SKATA specifies building-level codes; this is two
conventions and no code. Note `01 Ground Floor Level` vs `01 Aras Satu` are *different floors* sharing
the `01` prefix — so even the prefix is not a reliable key. **This is a real, reproducible test case and
it is already in the fleet** (`~/bim-ootb/buildings/JKR_extracted.db`, 194MB, 2026-07-12).

## §THE FEATURE — two halves, as the user framed it
1. **"if our data set are compliant or not"** — a CHECKER. Per building: how many elements/spaces/levels
   carry a valid code, how many don't, reported as a number, never a vibe. Same discipline as
   W-HOST-COVERAGE: the un-coded remainder is COUNTED, not hidden.
2. **"a feature to infuse it to be"** — an AUTHORING path. Assign/import codes, store them, export them
   back out with the handover model. ⚠ **Codes are authored or extracted, never inferred from
   `element_name`.** Guessing a SKATA code from a free-text label is exactly the failure mode already
   recorded in this repo (the glazed-façade name-rule, and HHS's `Rechteckiger Pfosten` blind spot).

**Surface (user, 2026-07-30): the Measure long-click element info panel.** That is `info-panel`
(`viewer/picking.js:651`; also read by `viewer/share.js:227,365`) — it already shows guid + ifc_class per
element, and it is where someone auditing a handover model would actually look. Add the code + a
present/missing state there.

## §PHASING — and why the timing is urgent
The `rel_aggregates` extraction change (RESUME_4D §STAGE B follow-on) is **about to re-extract and
re-upload the building fleet to OCI**. Local DBs are ~4.0GB across two trees; an OCI cycle is not free.

**Adding the classification column in the SAME extraction pass costs one schema line. Adding it later
costs a second full re-extract + re-upload of every building.** The two changes are the same kind of
change to the same file.

- **Phase 0 — decide NOW, before the 4D re-extract ships:** does `elements_meta` (and/or
  `spatial_structure`) get a nullable `classification_code` / `classification_system` column in this pass?
  This does **not** require the SKATA spec — a nullable text column holds whatever the spec turns out to
  demand. **Recommendation: yes, take the free ride.**
- **Phase 1 — extract what the IFC already carries.** `IfcClassificationReference` / `IfcRelAssociatesClassification`
  is where a compliant authoring tool already puts these. EXTRACT it; do not synthesise it. Report coverage
  per building as a number (expect near-zero on our current set — that IS the finding).
- **Phase 2 — the checker.** ⛔ blocked on the primary SKATA/PeDATA spec.
- **Phase 3 — the authoring path + the `info-panel` surface.**

## §OPEN QUESTIONS FOR THE USER
1. Can you supply the actual JKR/CIDB **SKATA + PeDATA** specification documents? Phase 2 cannot start
   without them and must not be guessed at.
2. Is the target **JKR-specific** (their BIM Requirements) or the broader **CIDB/Construction 4.0**
   mandate? They overlap but are not the same deliverable.
3. Is this for **winning JKR work** (must match their submission checklist exactly) or a **general
   compliance feature** (looser, sellable across jurisdictions — the `STANDARDS_COMPLIANCE_SRS.md`
   framing already anticipates UK/BCA/NCC)? That changes how hard-coded the code format should be.

## Sources (secondary — see the ⛔ above)
- https://jkrbim.my/ — JKR BIM CPAB official site
- https://infinitywave.io/blog/malaysia-bim-mandate-2025/ — the 1 July 2025 RM10m mandate, LOD 500,
  PeDATA + SKATA conformance
- https://theedgemalaysia.com/node/724693 — JKR BIM adoption scale (455 projects)

---

## §PHASE-C 2026-07-30 — the pluggable, locale-keyed scheme mechanism (BUILT). Phase 0 + Phase 1 DONE.

**Worker session, `bim-compiler` branch `feat/rel-aggregates-classification`.** User directive:
*"keep the code format pluggable via its locale json settings."* Mechanism only — **no SKATA format
was invented**, and the checker refuses to emit a verdict against the example one.

### C.0 Spec (written before code)

- **The descriptor is DATA.** Adding a jurisdiction must require zero code changes — a new locale entry
  plus a new scheme file, nothing more.
- **Locale is the key, not building or project.** A code format is a *jurisdiction* artifact: the same
  geometry re-tendered in another country needs a different code. A **scheme** is a reusable descriptor
  several locales may point at (Uniformat is not US-only), so locale → scheme is a reference, not a copy.
- **EXTRACT ONLY.** The checker reads `elements_meta.classification_code` (recovered verbatim by
  `extractIFCtoDB.py` §CLASSIFY). It never derives, repairs or guesses a code, and never reads
  `element_name`.
- **An example scheme must not be able to produce a green.** *Proves/disproves:* "can a placeholder
  format silently be mistaken for a real compliance result?" — it must not be able to.

### C.1 What shipped

```
config/classification/locales.json                  locale -> scheme(s) + level-naming conventions
config/classification/schemes/uniformat.json        VERIFIED-FROM-DATA
config/classification/schemes/skata.example.json    ⛔ EXAMPLE-NOT-SPECIFIED (labelled fiction)
build/classification_checker.py                     the consumer — §CLASSIFY_CHECK
```

**Scheme resolution order** (so an unlabelled building still reports honestly):
`--scheme` → `--locale` → `project_metadata.locale` → **detect** (match `classification_system`
against each scheme's `detect.ifc_classification_name`) → `locales.json` `default`.
Detection is why Hospital is recognised as Uniformat with nobody declaring anything.

**A scheme descriptor carries:** `id`/`title`/`authority`/`status`; a `detect` block; a `code` block
(`separator`, `case`, `pattern`, `permitted_lengths`, `observed_lengths`); a `segments[]` table where
each segment names its **facet** (`element` | `space` | `level` | `building` | `system`), offset,
length, pattern, required, and controlled `values`; a `facets` map saying which facet each one can
actually be stored against **in today's schema**; and an `evidence` block.

### C.2 Uniformat is not an example — it is real, and it came out of our own fleet

Phase 1 said *"expect near-zero coverage — that IS the finding."* **Measured, that was wrong, and in
our favour.** Hospital carries **4,647 `IfcRelAssociatesClassification`** and re-extraction recovered:

```
§CLASSIFY_CODES coded=4546/63917 (7.11%) uncoded=59371 distinct=15 valid=4546 invalid=0 badLength=0
§CLASSIFY_VERDICT GREEN — 4546/4546 carried codes are well-formed for uniformat
```

System = **Uniformat**, `IfcClassification('http://www.csiorg.net/uniformat','1998',$,'Uniformat')`.
Real codes: `B2020200 Curtain Walls` (1585), `D2090800 Piping & Fittings` (1439),
`A1020130 Piles - Steel Pipe` (444), `C1030220 Bath & Toilet Accessories - Residential` (298).
So the answer to *"are our datasets compliant?"* is no longer "there is nowhere to put the answer" —
for the Uniformat axis it is **7.11% coded, 59,371 elements NULL**, and the NULLs are counted, not hidden.

⚠ **A fixed-width validator would already have been wrong.** 14 of 15 observed codes are 8 characters;
`D4090` ("Other Fire Protection Systems") is **5**. That is why `permitted_lengths` is a *list* and why
`observed_lengths` is recorded separately from what the pattern permits. This is the single most useful
thing the real data taught us about the shape of a scheme file, and it will apply to SKATA too.

### C.3 The level-code axis — the JKR landmine is worse than §ARE OUR DATASETS COMPLIANT recorded

`level_naming` is deliberately a **separate axis** from the code, and it classifies the *convention*
in use. It never maps a storey name to a code — in this model it demonstrably cannot.

Measured on `JKR_extracted.db` (8,985 elements, 21 distinct storeys):

```
--locale en-US   conventionsInUse=2  hits={numeric_prefix:6976, no_prefix:1119} unknown=890  collisions=5
--locale ms-MY   conventionsInUse=3  hits={numeric_prefix_ms:2028, numeric_prefix_en:4948, no_prefix:1119}
  §LEVEL_COLLISION '00' -> 3 storeys: 00 Aras Tanah | 00 Ground Floor | 00 Ground Level
  §LEVEL_COLLISION '01' -> 3 storeys: 01 Aras Satu | 01 Ground Floor Level | 01 Ground Level Floor
  §LEVEL_COLLISION '02' -> 4 storeys: 02 1st Floor Level | 02 Aras Dua | 02 First  Floor | 02 Ground Floor Level
  §LEVEL_COLLISION '03' -> 3 storeys: 03 Aras Rasuk Bumbung | 03 Second Floor Level | 03 Water Tank Floor Level
  §LEVEL_COLLISION '04' -> 2 storeys: 04 Aras Bumbung | 04 Water Tank Roof Level
  §LEVEL_MIXED the model uses 3 naming conventions at once
```

The file recorded **one** `01` collision. There are **five**, and `02` names **four** different floors
(including `02 Ground Floor Level`, an "02" prefix on a "Ground Floor" name, and `02 First  Floor` with
a double space). 890 elements sit on `Unknown`. **Any SKATA level code must be authored independently
of `IfcBuildingStorey.Name`** — this model proves the name cannot yield one.

**The locale switch is the pluggability, demonstrated:** the same DB, same command, one flag, and the
Malay/English split appears (2028 `Aras|Bumbung|Tanah|Rasuk` vs 4948 English) because `ms-MY` points at
`my_bilingual_storey`, which `extends` the generic rules rather than replacing them.

### C.4 The example scheme cannot produce a green — verified

```
§CLASSIFY_CHECK[JKR_extracted.db] locale=ms-MY scheme=skata.example status=EXAMPLE-NOT-SPECIFIED
  §CLASSIFY_CHECK EXAMPLE-SCHEME — NO COMPLIANCE VERDICT EMITTED.
     'skata.example' is a placeholder shape, not the real format. Coverage above is real; any
     pass/fail against it would not be.
```

`skata.example.json` opens with a `_WARNING` block stating every field is fiction, and
`classification_checker.py` hard-refuses a verdict for any scheme with `status:
"EXAMPLE-NOT-SPECIFIED"`. **Do not remove that refusal to "get a green."**

### C.5 ⛔ Exactly what a real `schemes/skata.json` needs — hand this list to whoever gets the spec

Enumerated in full as `_TODO_FROM_PRIMARY_SPEC` inside `skata.example.json`. The load-bearing unknowns:

1. **`code.pattern` + `code.separator` + `permitted_lengths`** — delimited (`AA-01-002`) or fixed-width
   contiguous? And *how many* valid lengths (Uniformat has at least two — do not assume one).
2. **`segments[].offset/length`** — the per-segment widths. The core unknown.
3. **`segments[].values`** — the controlled vocabularies for building / level / space / component codes.
   These lists are the bulk of the real deliverable, not the regex.
4. **`detect.ifc_classification_name`** — what string a compliant Malaysian tool writes into
   `IfcClassification.Name`. Without it, auto-detection cannot work.
5. **PeDATA's relationship to SKATA** — separate code needing its own column and scheme file, or a field
   inside the SKATA code? **This decides whether one nullable column pair is enough.**
6. **Cardinality** — may one element carry SKATA *and* PeDATA *and* Uniformat? Today
   `extractIFCtoDB.py` keeps FIRST and COUNTS collisions (`§CLASSIFY multi=`). Multi-valued codes would
   need a **side table, not a column**, and that is a second schema change — worth deciding before the
   fleet re-extract, not after.
7. **Level code vs storey name** — see C.3. Our own JKR model says the name cannot derive the code.

### C.6 Status of the four phases in §PHASING

- **Phase 0 (decide before the re-extract) — ✅ DONE.** `classification_code` /
  `classification_system` / `classification_name` are in `elements_meta`, nullable, and they rode the
  SAME extractor pass as `rel_aggregates`. The free ride was taken.
- **Phase 1 (extract what the IFC carries) — ✅ DONE.** 7.11% on Hospital, Uniformat, 0 invalid.
  Per-building coverage across the rest of the fleet is **NOT yet measured** — only Hospital was
  re-extracted (see RESUME_4D §STAGE B).
- **Phase 2 (the checker) — ⚠ MECHANISM DONE, still ⛔ on the SKATA spec.** The checker runs and gives a
  real verdict for a real scheme (Uniformat GREEN). It cannot give one for SKATA and says so.
- **Phase 3 (authoring path + `info-panel` surface) — NOT STARTED.** Out of scope for this session.

⛔ **Still blocked, unchanged:** the primary JKR/CIDB SKATA + PeDATA specification. Question 1 in
§OPEN QUESTIONS is the one that unblocks Phase 2; questions 2 and 3 shape how much of `segments[].values`
must be exact.

---

## §TIER-COVERAGE — how much of the 92.89% gap is closable WITHOUT AI (measured 2026-07-31)

**Question asked:** Phase 1 recovered 7.11%. Of the uncoded remainder, how much can be proposed by a
**pure SQL join** — no inference, no LLM — and how much genuinely needs a human/AI decision?

**Three provenance tiers** (only tier 1 exists in the DB today; tiers 2 and 3 are a MEASUREMENT here,
nothing was written back):

| tier | definition |
|---|---|
| `ifc:recovered` | the code was authored in the IFC and extracted |
| `derived:sibling` | the type has NO code, but another type in this same model **sharing the same `ifc_class`** DOES — a code is proposable by join alone |
| `derived:ai` | no coded sibling exists for that `ifc_class` — a proposal would have to reason from the scheme's category tables |

### T.1 Method (reproducible, all numbers from a fresh run)

Re-extracted Hospital from `internal/UNMERGED/Hospital_IFC4_*.ifc` (the 7 discipline files — §B.4 of
`RESUME_4D_TRUTH_AND_BE_HERE_WHEN.md`), one `extractIFCtoDB.py` pass per discipline (`-o` overwrites,
so it cannot federate), then merged **on `guid`, never on `id`** (`id` is a per-file autoincrement and
collides). Per-discipline logs + the merged DB live in the session scratchpad; **no `.db` committed, no
OCI upload, no DB written back.**

**The established figures reproduced EXACTLY — no drift:**

```
ARC  15074 elem  agg=9464  coded=2253 (14.9%)  orphan=69
STR   2897 elem  agg=  20  coded= 504 (17.4%)  orphan=0
ELE   2798 elem  agg=   9  coded=  12 ( 0.4%)  orphan=1
MECH 19670 elem  agg=  13  coded=   0 ( 0.0%)  orphan=0
PLB   9121 elem  agg=   7  coded= 334 ( 3.7%)  orphan=0
FIRE   867 elem  agg=   7  coded=   0 ( 0.0%)  orphan=0
SPR  13490 elem  agg=   7  coded=1443 (10.7%)  orphan=31
§MERGE_TOTAL      elements_meta=63917
§DECOMP_MERGED    rel_aggregates=9527 rows / 254 distinct parents
§CLASSIFY_MERGED  coded=4546/63917 (7.11%) uncoded=59371 distinct_codes=15 systems=Uniformat
§PROOF            7 PASS / 0 FAIL on all 7 disciplines
```

Matches §PHASE-C's `4546/63917 (7.11%)`, §STAGE-B's `9527 / 254 parents` and `orphan=101`
(69+1+31) to the row. **`IfcRelNests` = 0 in all 7 files**, reported as zero, not omitted.

**Type key.** `elements_meta.element_name` is Revit `Family:Type:InstanceID`; the type is the first two
colon-segments. The re-extract has **338 distinct type_keys** (the shipped `Hospital_extracted.db` has
339 — the delta is §B.5's `+735 IfcOpeningElement / −233 non-geometric aggregate parents`, not drift).
**28 type_keys span more than one `ifc_class`** (`Basic Wall:Exterior - Metal Panel on Mtl. Stud` →
`IfcWall` + `IfcWallStandardCase` + `IfcOpeningElement`; every `M_Single-Flush:*` door →
`IfcDoor` + `IfcOpeningElement`). Since the tier is *defined* by `ifc_class`, the primary row identity
below is **`(type_key, ifc_class)` = 368 rows** — collapsing would blur the very key the tier turns on.

### T.2 The two splits — they disagree, and both matter

**By TYPE (368 `(type_key, ifc_class)` rows — this is the HUMAN WORK):**

| tier | types | share |
|---|---:|---:|
| `ifc:recovered` | 27 | 7.34% |
| `derived:sibling` | 179 | 48.64% |
| `derived:ai` | 162 | 44.02% |

**By INSTANCE (63,917 elements — this is WHAT THE USER SEES):**

| tier | instances | share |
|---|---:|---:|
| `ifc:recovered` | 4,546 | 7.11% |
| `derived:sibling` | 24,759 | 38.74% |
| `derived:ai` | 34,612 | 54.15% |

Collapsed to bare `type_key` (338 rows, a type inherits sibling-tier if ANY of its classes has a coded
sibling) it reads 27 / 179 / 132 types and 4,548 / 25,628 / 33,741 instances — the same story.

**`§PARTIAL = 0`.** Not one type is partly coded: coding in this model is authored **per type**, all-or-
nothing across its instances. That is a genuinely useful property — a type-level worklist is the right
granularity, and there is no "finish the half-done types" residue to chase.

### T.3 THE HEADLINE

> **341 of 368 Hospital types are uncoded. 179 of them can be proposed by join alone, covering 24,759
> instances (38.7% of the model). 162 types truly need an AI/human decision, covering 34,612 instances
> (54.2%).**

Put the other way: the join takes Uniformat coverage from **7.11% → 45.85%** of instances without a
single inference — but it can never reach the last **54.15%**, because those elements' `ifc_class` has
**no coded example anywhere in the model to copy from.**

### T.4 ⚠ The join key is COARSE — measured, with a damning example

`ifc_class` was the only join key permitted (a name-based rule was already rejected on measured grounds:
it silently missed 30% of HHS's curtain-wall plates, §B.5). It works, but it is not clean:

```
§JOINKEY  ifc_classes carrying ANY code = 8 of 31 present; of those, 3 carry >1 DISTINCT code
  IfcBuildingElementProxy: C1030220=298 D2010110=282 C1030200=219 D2010440=119 C1010400=31 D2010210=13 C1030100=1
  IfcFooting:              A1020130=444 A1010130=56
  IfcDoor:                 B2020200=5   B2030410=2
```

**116 of the 179 sibling types (covering 5,252 instances) sit on an ambiguous class**, where the join can
only offer the *modal* code — a judgement the data does not settle. The CSV flags every one
(`join_key_ambiguous=YES`) rather than hiding it.

**Two concrete failures a reviewer must catch, both visible in the CSV:**

1. **`IfcDoor`** — 7 coded doors, modal code `B2020200 Curtain Walls` (5 of them, i.e. curtain-wall
   doors). Propagating that to the **433 uncoded doors** would be flatly wrong.
2. **`IfcBuildingElementProxy`** is Revit's junk drawer — 5,729 instances, 7 different codes. The join
   proposes `C1030220 Bath & Toilet Accessories` for `M_RPC Tree - Deciduous:Blue Berry Elder` and
   `M_RPC Shrub:Oleander`. **Trees.**

**In favour of the join**, the other 63 sibling types — **19,507 instances, 78.8% of the sibling tier** —
sit on classes with exactly ONE code and are genuinely unambiguous: `IfcPipeFitting`→`D2090800 Piping &
Fittings` (12,182 uncoded), `IfcMember`→`B2020200 Curtain Walls` (5,547 — the curtain-wall mullions,
corroborated independently by `rel_aggregates`), `IfcLightFixture`→`D5020220` (1,260),
`IfcValve`→`D2090800` (466), `IfcRailing`→`C2010400` (52).

**Verdict on the key: usable as a PROPOSAL GENERATOR, never as an auto-apply.** Split the CSV on
`join_key_ambiguous` and ~19.5k instances are one review away from correct; the remaining ~5.3k need the
same human attention as tier 3.

### T.5 Why tier 3 is so large — it is a discipline story, not a modelling failure

Only **8 of 31 `ifc_class` values carry any code at all**. The whole MECH discipline (19,670 elements)
and FIRE (867) have **zero** authored classifications, so every MECH/FIRE type is tier 3 by construction.
The top of the uncoded worklist is exactly that: `Pipe Types:Standard` (5,950), `Pipe Types:Threaded
Under 65mm` (5,758), `Round Duct:Taps` (2,490). `IfcPlate` also has **zero** coded instances model-wide
(`System Panel:Glazed Spandrel`, 805 instances, is tier 3) — the same fact §B.5 recorded when it noted
classification alone could not have fixed the façade.

### T.6 Artifact

**`build/classification_tier_worklist_Hospital.csv`** (regenerated by `build/measure_classification_tiers.py <scratch-dir>`) — 341 uncoded types, ranked by instance count
descending. Columns: `rank, type_key, ifc_class, instances, discipline, tier, proposed_code,
proposed_code_label, evidence_sibling_type, evidence_sibling_instances, sibling_coded_instances_in_class,
sibling_distinct_codes_in_class, join_key_ambiguous`.

Placed in **`build/`, not `reference/`**: `reference/` is the source IFC corpus (its README: *"These IFC
files are the ground truth"*) and this is a *derived* measurement; `build/` already holds generated data
artifacts (`build/terminal_rules_payload.json`). It is a **proposal for human review — nothing more.**

### T.7 What this session did NOT do (boundaries held)

- **No `UPDATE elements_meta SET classification_code`** — not one code written to any DB.
- **No code inferred from an element NAME.** Tier 2 is a join on `ifc_class` to an already-coded sibling
  *in the same model*: the model's own assertion generalised.
- **No LLM called.** Tier 3 is a COUNT here, not an action.
- **No `.db` committed, no OCI upload, no `.db` deleted, no push.**
- **Hospital only.** Every `derived:*` number above is a property of *this* model's authoring habits —
  which classes its author happened to code. Do not quote 38.7% / 54.2% as fleet numbers; no other
  building has been re-extracted, and §PHASE-C's warning about 7.11% applies identically here.
