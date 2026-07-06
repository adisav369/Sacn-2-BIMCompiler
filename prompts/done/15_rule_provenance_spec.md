# DONE 9f1212f
# Rule Provenance & Symbiosis — Spec hardening + MANIFESTO integration

You are a docs + architecture session for bim-compiler. No code.

Read first:
1. docs/MANIFESTO.md — Three Concerns (spatial=WHERE, regulatory=HOW), §Why This Matters
2. docs/DocValidate.md — entire document (15 sections, the richest spec)
3. docs/SpecsAnalysis.txt §14 (spatial + regulatory symbiosis decision)
4. docs/DISC_VALIDATION_DB_SRS.md §10-11 (AD dictionary, mined rules)
5. docs/ProjectOrderBlueprint.md §5.1 (4D), §13 (rule-driven discipline)
6. docs/SystemContract.md §7 (effectivity model), §10 (gap register)
7. PROGRESS.md

## Context

The BIM compiler has TWO kinds of validation rules that work in symbiosis.
This is one of the project's most powerful selling points — no competing
product has this. But it is scattered across specs, never told as a single
coherent story. The github.io site needs a prominent, linkable narrative.

### The Two Rule Sources

**Spatial rules (mined from buildings):**
Empirical patterns from 35 real buildings. "Rooms of this type typically
have 1 sprinkler at this spacing." Stored in ad_space_type, ad_element_mep,
ad_space_dim, placement_rules. Propose placements.

**Regulatory rules (from standards bodies):**
Building codes: UBBL (Malaysia), NFPA 13 (fire), IRC (US), BCA (Australia),
NDSS (UK). "NFPA 13 requires sprinkler coverage ≥ 3000mm." Stored in
AD_Val_Rule with jurisdiction scope + pack_id + valid_from/to. Gate placements.

**The symbiosis:**
- Spatial rules PROPOSE: "place sprinkler here based on observed patterns"
- Regulatory rules VALIDATE: "is this placement code-compliant?"
- Compiler compiles freely (WHAT), spatial rules guide WHERE,
  regulatory rules gate HOW — the Three Concerns in action
- Like iDempiere: the BOM Configurator proposes a product mix,
  C_Tax validates the tax liability. One proposes, one constrains.

## TASK 1: Add §0 to DocValidate.md — The Symbiosis Story

DocValidate.md starts with iDempiere mapping (§1) then jumps to validation
types (§2). It needs an executive summary that tells the symbiosis story
BEFORE the technical detail. Insert as §0, before the existing §1.

Write a section titled:
**"§0. Two Kinds of Rules — Why This Changes Everything"**

Content:
- The problem: every BIM tool validates geometry but NONE compose
  spatial knowledge (what engineers actually do) with regulatory
  knowledge (what the law requires). They're separate tools, separate
  consultants, separate workflows.
- Our solution: two rule databases that compose at compile time.
  Spatial proposes, regulatory validates. One compiler, two knowledge bases.
- The iDempiere parallel: this is Configure-to-Order applied to compliance.
  The BOM template (spatial rules) proposes a compliant layout. The order
  (regulatory rules) constrains by jurisdiction. Exception-based validation —
  rules gate by exception, not by prescription.
- The crowd-puller angle: a firm in Kuala Lumpur gets UBBL rules automatically.
  Same building plan exported to Sydney gets BCA rules. Same compiler, same
  spatial knowledge, different regulatory pack. Like switching tax jurisdictions
  in iDempiere.
- Cross-link to MANIFESTO §Why This Matters, ProjectOrderBlueprint §13

### ERP insight required while writing §0:

Think through these iDempiere parallels and note any that don't hold:

a. **C_Tax analogy:** AD_Val_Rule as construction C_Tax — tax rate applies
   to order lines by jurisdiction. Does the analogy hold for spatial rules too,
   or is spatial more like M_PriceList (proposed pricing vs regulatory tax)?

b. **Rule precedence:** iDempiere resolves C_Tax by most-specific match
   (Country > Region > default). DocValidate §13 has a three-tier cascade
   (per-discipline → cross-discipline → cross-storey). Are these the same
   pattern? If not, how do they compose? What if a jurisdiction rule
   conflicts with a mined spatial rule?

c. **Rule versioning:** iDempiere M_PriceList has M_PriceList_Version with
   ValidFrom. AD_Val_Rule has valid_from/valid_to + pack_id. But there's no
   version entity — no "UBBL_2024_v1" with an effectivity period and a
   supersedes relationship. Is this a gap? Should AD_Val_Rule_Pack be a
   first-class entity with ValidFrom/ValidTo/Supersedes_Pack_ID?

d. **Non-disturbance as audit:** DocValidate §7 says "the building is ground
   truth." In iDempiere terms, this is like saying "the posted invoice is
   ground truth — tax rules must not change the posted amount." Is this the
   right mental model? Or is it more like calibration (§7.2: test against
   known good, adjust until 0 violations)?

e. **Rule mining as AD_Process:** In iDempiere, AD_Process runs batch jobs.
   Rule mining (DocValidate §7.4, §15.4) IS a batch process — extract
   building, measure patterns, generate rules. Should this be modelled as
   an AD_Process with parameters (building_id, discipline, threshold)?

Document any insights, tensions, or gaps as an appendix.

## TASK 2: Strengthen DocValidate §3 — validation.db → ERP.db alignment

§3 still says "validation.db" (the fourth DB concept from 2026-03-18).
Since S76, validation tables live in ERP.db. Update:
- Replace "validation.db" references with ERP.db
- Note: ERP.db IS the validation database. AD_Val_Rule, placement_rules,
  ad_space_type, ad_code_requirement all live there. No separate fourth DB.
- Update the 4-DB table to show ERP.db serving both "discipline metadata"
  AND "validation rules" roles

## TASK 3: Cross-link rule provenance chain

Create explicit cross-links between the specs that document rule lifecycle:

```
EXTRACTION → MINING → ENCODING → VALIDATION → EXCEPTION
    ↓            ↓         ↓           ↓            ↓
IFCtoBOM   DocValidate  DV_*_rules  PlacementValidator  AD_Val_Rule_Exception
  §7.4      §7.1-§7.4   migration   §15.1              §15.3
```

For each stage, add a one-line cross-ref to the relevant spec section.
Place this in DocValidate.md §8 (Implementation Sequence) or as a new
subsection before it.

## TASK 4: Harden DocValidate §11 — world standards table

§11.2 has a world standards comparison table (MY/US/UK/AU/SG/EU).
Check for:
- Missing jurisdictions that matter (India IS 16700, Japan Building
  Standards Act, China GB 50096 — the world's largest construction markets)
- Missing rule types (accessibility/DDA, energy efficiency, acoustic)
- Consistency with AD_Val_Rule seed SQL in §11.3
- Note: these are FUTURE — seed data, not blocking current work. But the
  table is a crowd-puller for the github.io site and should be comprehensive.

## TASK 5: Fix SystemContract.md §6 — M_InOut mapping

SystemContract.md §6 maps M_InOut to "compilation batch / placing buildings
on plots." This is WRONG (confirmed with project owner, SpecsAnalysis §13).

Fix:
- M_InOut = materials movement (delivery batch to site). Belongs under 4D
  scheduling, not site allocation.
- Plot placement = C_ProjectLine (one plot = one C_ProjectLine, FK to C_Order)
- Add cross-ref to ProjectOrderBlueprint.md §5.1 (4D schedule)
- Keep M_InOutLine/M_InOutLineMA rows but redefine:
  - M_InOutLine = individual material receipt (100 panels delivered)
  - M_InOutLineMA = lot/attribute tracking (batch number, inspection date)

## TASK 6: Add enterprise scaling note to ProjectOrderBlueprint.md

Add a subsection under §14 (or §8 Forward Friction) titled
"Enterprise Scaling — ModelValidator + Federated Spatial DB":

- Current processIt() dispatch is correct for batch compilation
- For real-time collaborative editing (multi-user BIM Designer),
  align with iDempiere ModelValidator pattern (beforeSave/afterSave hooks)
- Federated Model Spatial DB addon (93% memory reduction on 93K elements,
  StrategicIndustryPositioning.md line 238) needed for multi-building
  federation — current in-memory approach handles TE's 48K but won't
  scale to multi-project without spatial DB partitioning
- These two features are companions for enterprise deployment

## TASK 7: Verify github.io prominence

After all edits, check that:
- INDEX.md links DocValidate.md with updated description mentioning
  "spatial + regulatory rule symbiosis"
- MANIFESTO §Why This Matters hyperlinks resolve correctly
- DocValidate.md §0 is the first substantive section a visitor reads

## Constraints

- Docs only — no Java, no SQL, no migrations
- Do NOT edit MANIFESTO.md (user maintains it directly)
- Do NOT edit AUDIT_S51_FOCUSED.md (historical)
- Keep edits surgical — update specific sections, don't rewrite prose
  that's already correct
- Pre-flight citation: `<!-- Implementing SpecsAnalysis.txt §14 -->`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S##] Rule provenance: spatial+regulatory symbiosis spec + DocValidate §0 + cross-links`.

Append findings from the ERP insight questions (Task 1 a-e) as an appendix
after the DONE marker for watchdog review.

---

## APPENDIX — ERP Insight Findings (Task 1 a-e)

### a. C_Tax analogy — does it hold for spatial rules?

**Finding: the analogy holds, but spatial rules are closer to M_PriceList than C_Tax.**

C_Tax constrains by jurisdiction — it gates. AD_Val_Rule (regulatory) does
the same: UBBL gates Malaysian buildings, IRC gates US buildings. The
parallel is exact.

Spatial rules (ad_space_type_mep_bom, placement_rules) are different —
they PROPOSE, not gate. In iDempiere terms, spatial rules are more like
**M_PriceList** (proposed pricing that the sales rep can override) than
C_Tax (mandatory tax that the system enforces). A PriceList proposes a
unit price; the sales rep can accept or adjust. A spatial rule proposes
sprinkler placement; the architect can accept or adjust.

**The symbiosis in ERP terms:**
- Spatial rules = M_PriceList (proposes, overridable)
- Regulatory rules = C_Tax (gates, mandatory)
- Both apply to the same C_OrderLine, resolved at different moments

**No tension found.** The two-layer model (propose + gate) is native to
iDempiere — every C_InvoiceLine has both a price (proposed) and a tax
(mandatory). DocValidate §0 uses this framing correctly.

### b. Rule precedence — same pattern as C_Tax?

**Finding: related but different patterns. They compose, don't conflict.**

iDempiere C_Tax resolves by **most-specific geographic match**:
Country > Region > default. This is a NARROWING cascade — the most
specific rule wins.

DocValidate §13 has a **scope-expanding cascade**:
Tier 1 (per-discipline) → Tier 2 (cross-discipline) → Tier 3 (cross-storey).
This is not narrowing — it's progressively WIDER scope. Each tier sees
more of the building.

**They are complementary, not competing:**
- WITHIN each tier, jurisdiction precedence follows the C_Tax pattern
  (plot-specific > project-level > jurisdiction > default, per
  SystemContract.md §7.1)
- ACROSS tiers, the cascade is sequential — Tier 1 fires first (per-line),
  then Tier 2 (per-floor), then Tier 3 (per-building)

**Conflict resolution (spatial vs regulatory):** If a spatial rule proposes
placement and a regulatory rule blocks it, **the regulatory rule wins** — same
as C_Tax overriding a PriceList discount that would violate tax law. The
spatial rule is a suggestion; the regulatory rule is a gate. ProjectOrderBlueprint
§13.2 models this as Absent → Proposed → Accepted, with the architect as
arbiter when spatial and regulatory conflict.

### c. Rule versioning — is AD_Val_Rule_Pack needed as first-class entity?

**Finding: YES — this is a gap. AD_Val_Rule_Pack should be a versioned entity.**

Current state: AD_Val_Rule has `pack_id` (TEXT), `valid_from`, `valid_to`.
Pack_id is a string tag, not a foreign key. There is no version entity —
no "UBBL_2024_v1" with an effectivity period and a supersedes relationship.

iDempiere precedent: **M_PriceList → M_PriceList_Version** (with ValidFrom
+ Supersedes). Each PriceList has multiple versions; only one is active
for a given date. This is the exact pattern needed for building codes:
UBBL 2012 superseded by UBBL 2024, NFPA 13 2019 superseded by NFPA 13 2022.

**Proposed schema:**
```
AD_Val_Rule_Pack (NEW first-class entity)
  pack_id              INTEGER PRIMARY KEY
  name                 TEXT          -- 'UBBL_2024', 'NFPA_13_2022'
  jurisdiction         TEXT          -- 'MY', 'US'
  valid_from           DATE
  valid_to             DATE          -- NULL = current
  supersedes_pack_id   INTEGER       -- FK to prior version
  is_active            INTEGER DEFAULT 1
```

AD_Val_Rule.pack_id would become an FK to this table instead of a string.

**Status:** Already identified as GAP-SC-4 in SystemContract.md §10 (partially
addressed — tagging done S67c, versioning/lifecycle deferred). This finding
confirms the gap and proposes the schema. Not blocking current work.

### d. Non-disturbance as audit — right mental model?

**Finding: it's calibration (§7.2), not audit. The iDempiere analogue is
M_Inventory (physical inventory count), not C_Invoice posting.**

The prompt offers two models:
1. "Posted invoice is ground truth — tax rules must not change the posted amount"
2. "Calibration — test against known good, adjust until 0 violations"

**Model 2 is correct.** The Non-Disturbance principle (DocValidate §7) says:
"the building is ground truth." When a mined rule flags a violation against
Terminal, the RULE is wrong, not the building. This is calibration:

- iDempiere M_Inventory: count physical stock, compare to system stock,
  adjust system to match reality
- BIM Non-Disturbance: run rules against real building, compare violations
  to zero, adjust RULES to match reality

The audit model (posted invoice) would mean rules must never change the
building — but that's not what happens. Rules are adjusted to describe
the building accurately. The building teaches the rules, not the other way
around. This is calibration, and DocValidate §7.2 uses the term correctly.

### e. Rule mining as AD_Process — should it be modelled?

**Finding: YES — RuleMiner (§15.4) IS an AD_Process. Model it explicitly.**

In iDempiere, AD_Process runs batch jobs with parameters. Rule mining
(DocValidate §7.4, §15.4) is exactly a batch process:
- Input: building_id, discipline, threshold
- Processing: query extracted data, detect patterns, generate rules
- Output: AD_Val_Rule + AD_Val_Rule_Param candidate rows

**Proposed mapping:**
```
AD_Process: RULE_MINING
  Parameters:
    building_id   TEXT     -- 'TE', 'DX', 'SH'
    discipline    TEXT     -- 'FP', 'ELEC', 'STR', NULL=all
    tier          INTEGER  -- 1, 2, 3 (per §15.4 mining phases)
    threshold     REAL     -- statistical threshold for pattern detection
  Output:
    List<CandidateRule> → staged in AD_Val_Rule with is_active=0
    NonDisturbanceReport → pass/fail per stone
```

The RuleMiner class in §15.4 already has this shape (mineTier1/2/3 +
verify methods). Making it an AD_Process would:
1. Give it a process ID, parameter set, and scheduling via iDempiere's
   standard AD_Process infrastructure
2. Allow it to be triggered from the UI ("Mine Rules" button)
3. Produce auditable process logs (AD_PInstance)

**Status:** Future. The class design in §15.4 is correct. Wrapping it as
AD_Process is an enterprise concern — add when ModelValidator alignment
(§8.5) is implemented.
