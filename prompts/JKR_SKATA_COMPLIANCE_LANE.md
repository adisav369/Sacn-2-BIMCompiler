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
