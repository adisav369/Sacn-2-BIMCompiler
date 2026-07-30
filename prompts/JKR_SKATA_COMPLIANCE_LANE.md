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

## §ARCHITECTURE 2026-07-30 — layering, patch-vs-binary, and the version-marker gap
User questions: (1) how do we mark which DB has a fix, and is SQL-migration-to-OCI better practice than
shipping an updated DB? (2) can classification injection ride with room topology as a general feature?
(3) is the injection an abstract layer, locale-updatable, not hard-set?

### A. Patch beats binary, and the machine already exists — measured
`viewer/scene.js:1223` `A._applyPendingPatch()` is LIVE, called from `streaming.js:1984` (split/meta path)
and `:2130` (single-DB path). Convention: `buildings/patches/<dbFile>.sql`, fetched from the same directory
the DB came from. **Eight patches already ship** (Hospital_extracted, Hospital_meta, Terminal_extracted,
Terminal_meta, HHS_Office_Federated, JKR_extracted, …). There is also a provenance manifest convention
(`oci-patch-provenance/1`) recording engine SHA, served-DB etag, artifact md5, a verification command with
its exit code, and a PASS/FAIL verdict.

**The ratio settles the question:**
```
Hospital_extracted.db        263,307,264 bytes   (served object, per manifest)
its existing patch .sql          226,962 bytes   → ~1160x smaller
our rel_aggregates payload    ~9,527 rows        → est. <1MB  → still ~300x smaller
```
**The decisive argument is not size, it is the cache.** `scene.js:1218-1219`: the IDB cache always stores
the RAW server bytes; only the buffer handed to `SQL.Database` is patched. So a patch reaches an existing
user **without invalidating their cached 251MB copy**. Replacing the binary forces a full re-download for
every user who already has it — the exact bandwidth failure `SEAM_IDENTITY_AUDIT.md` F1 was about.
**Ship the patch. Reserve a new binary for a genuine re-extraction (new geometry), not a data fix.**

### B. ⚠ THE GAP — nothing marks which fixes a DB carries
Measured on the shipped Hospital DB:
```
sqlite> SELECT * FROM project_metadata;
building_name|Hospital
import_date|2026-05-02
```
**No version row, no patch level, no applied-patch list.** And `_applyPendingPatch` applies the patch on
EVERY load unconditionally, relying on each script being idempotent (`scene.js:1220`). That works, but:
- you cannot ask a DB which fixes it has;
- `§PATCH_APPLY` proves the script RAN, not that the data is now correct;
- one patch file per DB means a second fix must be hand-merged into the same file, with no record of what
  is already in it — this WILL drift once there are two authors;
- the manifest records provenance server-side; nothing is written INTO the DB.

**Recommendation:** every patch script's first act is to write its own identity into `project_metadata`
(e.g. `patch_level`, or an append-only `applied_patches` row carrying id + date + engine SHA). Cheap,
idempotent, and it makes "which DB has this fix" answerable from the DB itself instead of by inference.
This is the same one-identity-no-owner shape as the audit's C1/C2 clusters.

### C. Three layers — and yes, it rides with room topology
| layer | what | changes when | ships as |
|---|---|---|---|
| 1 · base extract | geometry + raw IFC facts | re-extraction only | big binary, OCI |
| 2 · overlay | room topology, `rel_aggregates`, classification BINDINGS (which code on which element) | independently of geometry | **SQL patch + self-heal loader** |
| 3 · scheme | what a valid code LOOKS like per jurisdiction | when a standard changes | **locale JSON, no DB at all** |

Layer 2 answers the user's "together with room topology" — room topology and classification bindings are
the same *kind* of thing (derived/authored facts layered over immutable geometry, changing on their own
cadence). They should share ONE patch file and ONE loader, not grow a third mechanism. ⚠ Note the repo
already has TWO overlay mechanisms — the SQL patch above AND sidecar DBs (`Terminal_rooms.db` carries its
own `elements_meta`/`spatial_structure`/`rel_contained_in_space`/`m_bom`). Adding a third would be a
textbook `SEAM_IDENTITY_AUDIT.md` C4 repeat. **Pick one; the patch path is the one with a live loader.**

Layer 3 answers "abstract, manageable later, not hard-set": the scheme is DATA, so a new jurisdiction is a
new JSON file and touches **zero building DBs**. Phase C already built this locale-keyed. It also makes the
feature opt-in — no scheme configured for a locale ⇒ no compliance UI, zero cost to users who don't need it.

**The split that matters:** binding (layer 2, per-model) must stay separate from scheme (layer 3, per
jurisdiction). Fuse them and a standards revision forces re-patching every building.
