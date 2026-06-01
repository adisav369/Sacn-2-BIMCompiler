/**
 * BIM OOTB / ERP OOTB — The Holy Grail: editable business rules, live.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# The Holy Grail — Editable Business Rules, Live

> *A first-person note from the author. The technical claims below are grounded in the
> dated, witnessed sections of [ERP.md](ERP.md); this page is the reasoning that ties
> them to a quest I have carried for a long time.*

## Who is writing this, and why it is personal

I am Redhuan D. Oon. I was the founding leader of **ADempiere**, and in that role I
materially ushered in the birth of **iDempiere** — the community, the people, and the
direction that made it possible. I then left that path to pursue this one.

I did not leave because the open-source ERP idea was wrong. I left because the thing I
most wanted from it — **rules you can edit while the system runs, safely, without a
build** — was structurally out of reach inside the architecture we had built. For two
years I tried to reach it from the inside: I attempted, with Spring and plain Java, to
extract even the *core* of the model — `PO.java`, `Info.java` — into something light
enough to re-host and re-open. It did not converge. This document is the account of why
it could not, and why a browser-and-PWA paradigm shift reached it from the outside —
something that genuinely surprised me, the person who had spent the most time failing at
it the other way.

## What the Holy Grail actually is

Not "an ERP in a browser." That is a means. The grail is one specific capability:

> **Edit a business rule, and watch the affected records change on the map — live,
> reversibly, with no recompilation and no server.**

In this project that is named directly: the *parked endgame* in
[ERP.md "Why this is more than a port"](ERP.md) — *"let you **edit a rule and watch the
affected records flip on that map** (the diff-oracle in the browser, §2d-3)"* — and in
[GLASSBOWL_DOSSIER.md](GLASSBOWL_DOSSIER.md) as *the final big picture*: Glassbowl stops
being a map of the engine and becomes **the console you run the engine from**.

It is earmarked precisely, not vaguely:

| Earmark | What it fixes |
|---|---|
| **[ERP.md §0.4](ERP.md)** — *Editable business rules, the SystemAdmin role* | names this as **the differentiator**, not a feature |
| **[ERP.md §0.5](ERP.md)** — *the rules engine = a per-cell decision table* | the shape: a table per `(DocType, status, action)` cell — **not** Rete / DSL / inference |
| **[ERP.md §0.9](ERP.md)** — *the rule mechanism, JSR-223-native* | the host language **is** JavaScript, so the rule language = the runtime language; the scripting-engine abstraction is *unnecessary* |
| **[ERP.md §0.10](ERP.md)** — *the Rule Compiler* | the rules are already extracted to data: `erp_rules.db`, **746 records**, diff-verified |
| **ERP.md §2d-3 / GLASSBOWL_DOSSIER.md** | the live edit-and-reflow loop — the grail itself |

## Why two years of extracting `PO.java` was the wrong target

This is the part I most want a future reader — especially one still inside a code-engine
ERP — to understand, because it cost me the most to learn.

`PO.java` is **not extractable**, and it never was. Not because it is hard, but because
it is the **wrong thing to extract**. It is an *imperative* engine. Pull on it and the
entire transitive graph follows: the model registry, the OSGi service wiring, the
transaction manager, the callout chain, the `MTable`/`MColumn` reflection. You are
trying to lift the engine *with its whole gravity well attached*. Two years is simply
what that costs — and it does not converge, however much effort you add.

The paradigm shift is not "do the extraction better in a PWA." It is to **stop
extracting the engine, and extract what the engine operates on**:

- The Application Dictionary rows and the rule records were **already data** —
  `AD_Rule`, `AD_Val_Rule`. That is iDempiere's own design ([ERP.md §0.9](ERP.md)); half
  the "rules engine" already exists as data. We *compile* it, we do not reinvent it.
- The *remainder* — the deterministic part that actually runs a document — is small.
  Re-hosted as a **~150-line kernel** in the browser's native JavaScript, `PO.java`'s job
  (persist + lifecycle) becomes `apply(op)` plus the op-log fold; `Info.java`'s job (the
  windowed UI) becomes the keyed-overlay Application Dictionary (the UI-overlay
  governance model — every UI concern a keyed layer over a tagged element).

The bloat was not ported. It was **deleted by being re-based.** That is why the shift
worked from the outside when extraction failed from the inside.

## Why it is a grail others cannot reach

Three conditions have to hold **at the same time**. Almost no system has all three —
which is exactly why this remained a quest rather than a checkbox:

1. **The engine is data.** A rule is a *row you read*, not Java you recompile.
2. **The host language is the rule language.** Browser JavaScript *is* the runtime, so
   editing a rule is editing the running system — no JSR-223, no Drools workbench, no
   scripting bridge ([ERP.md §0.9](ERP.md)).
3. **The op-log makes editing safe.** Change-as-op, replay, dry-run, undo — *the safety
   Drools never had* ([ERP.md §0.4](ERP.md)). You can edit a rule and **not corrupt
   history**, because effects are frozen and replayable ([ERP.md §0.18](ERP.md)).

iDempiere has condition (1) only half-done and lacks (2) and (3): its rules are
half-data, half-welded into `M*` Java side effects, so they can never be made *fully*
editable-at-runtime without the JVM / OSGi / workbench that constitute the bloat. Drools
has a rule engine but no op-log safety and no engine-as-data. For them this is a *stuck*
problem; here it is a **structural property**. The bloat that prevents them is precisely
the bloat this project removed.

## The honest status — half-claimed, one mile left

I will not overclaim my own grail. It is **half-reached, and witnessed that far:**

- **Reached:** the rules are extracted to data — `erp_rules.db`, **746 records**,
  **diff-verified** against the GardenWorld oracle ([ERP.md §0.10, §0.17](ERP.md)).
  The engine renders *itself* from that data (Glassbowl), read-only.
- **Remaining:** the **live edit loop** — *edit the rule → records reflow on the map* —
  is the write-loop, gated behind T3 (`push=live`, explicit go). The read-only History
  undo-preview and the greyed CRUD ring are the honest *seam* to it, not the step.

And the seam is now being built. The CRUD / Validation overlay (the "ring-of-fire" edit
layer, governed by the UI-overlay model) carries the
`AD_Val_Rule` model as a keyed, editable layer. Make that validation layer *editable and
re-folding* instead of static, and the grail is demonstrable in one gesture. The single
witness that closes it:

```
§RULE-EDIT key=c_invoice rule=non_negative_amt edit=min:0→min:100
           affected=K records re-fold verify=ok reversible=Y
```

Change one validation row; watch *K* records change which side of the rule they fall on,
live; op-logged, signed, reversible. That witness is the whole paradigm shift made
visible in a single gesture — and the architecture it needs (engine-as-data + a
JavaScript host + the signed op-log) is **already standing under it.**

## Roadmap check — the write-seam, as of 2026-06-01

The grail is the *last rung of the write-loop*, not a separate project. Here is the
ladder from today's read-only surface to the live rule-edit, and where each rung stands —
this section is meant to be updated as a checkpoint each time a rung is climbed.

| Rung | What it is | State |
|---|---|---|
| **R0** | Rules extracted to data, diff-verified (`erp_rules.db`, 746 records) | **done — witnessed** |
| **R1** | The engine renders itself from that data, read-only (Glassbowl) | **done — live** |
| **E2** | The write *seam* — CRUD ring + the document state machine (**Process / DocAction**) modelled as keyed data, **dry-run** | **done — witnessed (this checkpoint)** |
| **E3** | The seam goes live — `apply` → `commitOp`/`sealChain`, `verifyChain` after each; projection re-folds; acceptance oracle (rebuilt #80001 == traced #80001) | next |
| **E4** | Owner-gate + CAS invoked on the write path (the Phase-A guards surfaced) | pending |
| **§RULE-EDIT** | **The grail** — edit a *rule* row; watch *K* records re-fold live, signed + reversible | the last rung |

**What just landed, and why it matters to the grail.** The CRUD + **Process (DocAction)**
overlay (E2) is now mounted in tandem with the Help guide as an independent peer layer —
witnessed `§CRUD-PROC pass=27 fail=0`, plus the earlier CRUD validation/dry-run witnesses,
all still dry-run. The **Process** piece is the part that matters here: it models the
document's *state machine* — `completeIt()`, the legality of `DR→CO`, the IP-on-unmet
outcome — as **data** (`docAction` descriptors naming the real `M*.completeIt()` oracles),
not code. That widens the grail's surface from *field-validation rules* to *lifecycle
rules*: the most valuable rule a user edits is usually "**when may this complete, and what
does completion do**" — and that is now keyed data the same edit loop can reach. The grail
is no longer only "edit a minimum amount"; it is "edit when an invoice may post."

**The honest gap is unchanged.** E2 is still **dry-run** — it logs the op it *would* run.
The load-bearing step is **E3**: the signed write plus the acceptance oracle that proves a
re-built document matches the traced one. Until E3 is green, the seam is a faithful *model*
of the engine, not the live engine; and the grail rung (`§RULE-EDIT`) sits one step past
E3/E4. So the checkpoint verdict, stated plainly: **the seam to the grail is now built and
witnessed as data; the current is not yet flowing through it.** A rung climbed, honestly
logged — which is the difference between building and day-dreaming.

## A closing note, to the version of me from two years ago

The two years spent proving the *imperative* extraction does not converge were not
wasted — they are the evidence that the *declarative-extract* path is the one that does.
The grail was never going to be reached by carrying the old engine somewhere lighter. It
is reached by realizing the engine should have been data all along, and that the one
thing a code-engine structurally cannot give you — a rule you edit while it runs, safely
— falls out for free the moment it is.

---

*Grounding: [ERP.md](ERP.md) §0.4 · §0.5 · §0.9 · §0.10 · §0.17 · §0.18 · §2d-3 ·
[GLASSBOWL_DOSSIER.md](GLASSBOWL_DOSSIER.md) · the §20 prototype addendum. Every claim of
extraction here is witnessed in a dated log; nothing on this page is asserted that the
source data does not support.*
