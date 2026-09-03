# ⚠ DO NOT REMOVE — Scope guard
# SCOPE: TRIAGE the iDempiere business-rule backlog (the "284 callouts" + validation rules) by iDempiere's
#   OWN 4-tier stack, then ROUTE each tier to the layer/lane that owns it. This session does NOT port all
#   284 in one go — it produces the TIERED MANIFEST + ports the field-level (UI) tier first as the witness.
# WHY THIS SHAPE: the backlog is NOT one bucket. iDempiere already separates enforcement into 4 tiers
#   (Callout / modelChange / docValidate / FactsValidator). Three of the four are ENGINE concerns behind the
#   seam, owned by Agent E (perf) / Agent P (posting); only the field-level Callout tier is a UI overlay.
#   See docs/IDEMPIERE_2.md §"The validation stack — iDempiere's 4 tiers" + prompts/ENGINE_FULL_ERP_ISSUES.md
#   §I-C / §0.1 LAYER MAP / §2.2.
# NON-NEGOTIABLE: EXTRACT-don't-port (clean-room). iDempiere Java is the ORACLE (ERP.md §18.10) — learn the
#   tier + the rule EFFECT, extract the effect from the AD (build/erp/ad_full.db), NEVER copy their Java
#   (copyleft → would contaminate the MIT moat, IDEMPIERE_2.md Guardrail 2). Non-invent: a rule we cannot
#   extract is LISTED as unported, never guessed. Spec-first; witness-led (each test NAMES its issue);
#   §-log first (save every run, READ the log before conclusions).
# READ FIRST: docs/IDEMPIERE_2.md §validation-stack · prompts/ENGINE_FULL_ERP_ISSUES.md §I-C/§0.1/§2.2 ·
#   docs/ENGINE_CONTRACT.md §1 (the seam — where modelChange/docValidate guards sit) ·
#   prompts/UI_OVERLAY_GOVERNANCE.md (the Validation overlay — where field Callouts sit) ·
#   build/erp/ad_full.db (AD_Val_Rule / AD_Column / AD_Field — the extractable rule source).

---

# ERP Callout/Rule Port — tier-triage, then field-tier-first

## §0 The frame
iDempiere's `ModelValidationEngine` runs a 4-tier stack (docs/IDEMPIERE_2.md). The backlog maps onto it:

| Tier | iDempiere mechanism | Our layer | Owning lane |
|---|---|---|---|
| 1. **Field** | `CalloutEngine` (field-change, 6-param) | UI Validation **overlay** (keyed per field) | **THIS prompt** (port first) |
| 2. **Row** | `ModelValidator.modelChange` | kernel verb **guard** (behind seam) | Agent E / engine |
| 3. **Document** | `ModelValidator.docValidate` (prepare/complete/void) | op-group atomicity + **DocAction** | ERP_KERNEL_BUILD |
| 4. **Posting** | `FactsValidator.factsValidate(AcctSchema,…)` | **posting fold** (per acct-schema) | Agent P (ENGINE §I-G/§I-J) |

## §1 ISSUES (each names what it proves)
- **W-TIER-MANIFEST** — *Issue:* the backlog is treated as one flat "callout port" and lands in the wrong layer.
  *Proof:* a manifest classifying every extracted rule into tier 1–4, `§TIER counts t1=… t2=… t3=… t4=… unported=…`.
- **W-FIELD-PORT** — *Issue:* a field Callout (e.g. C_BPartner → fill price-list / payment-term defaults) is not
  reproduced in the UI overlay. *Proof:* the overlay fires the same field effect on change, `§CALLOUT-FIELD
  key=<col> effect=<col2:val>` matching the AD-extracted rule (no Java).
- **W-NO-INVENT** — *Issue:* a rule whose effect isn't extractable is silently dropped or guessed. *Proof:*
  it appears in the manifest's `unported[]` with a reason, `§UNPORTED rule=<name> reason=<not-in-AD|procedural>`.

## §2 STEPS
1. **Extract** the rule inventory from `build/erp/ad_full.db` (AD_Val_Rule, AD_Column.Callout, AD_Field) →
   raw list with each rule's table/column/trigger. Save to log.
2. **Classify** each into tier 1–4 by its iDempiere mechanism (the §0 table). Emit `§TIER` counts.
3. **Route** tiers 2–4 to their owning lane as a handoff list (do NOT port them here) — write the list into
   ENGINE_FULL_ERP_ISSUES.md's relevant lane note.
4. **Port tier 1 (field) only** into the Validation overlay (UI_OVERLAY_GOVERNANCE conformant) — effect
   extracted from AD, witnessed by W-FIELD-PORT. Anything not extractable → `unported[]` (W-NO-INVENT).

## §3 OUT OF SCOPE
Tiers 2–4 implementation (other lanes). Multi-currency rate rules → ENGINE §I-J (determinism, Agent P/E).
Tax/jurisdiction rules → flagged in the manifest as tier-4-localization, ported per-jurisdiction later.

## # DONE (write here — every claim needs a §-log line)
