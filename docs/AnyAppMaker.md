/**
 * BIM OOTB / ERP OOTB — AnyAppMaker: the horizon of self-authoring verticals.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# AnyAppMaker — the horizon

> **Status: VISION, far-off, grail-gated.** This is the limit a sequence converges
> toward, not a thing built directly. The first concrete term of that sequence is
> [ERPMaker.md](ERPMaker.md) — read that for the feasible, focused product. This page
> records the reasoning that places ERPMaker on a larger map, so the map is not lost.

## The one-line claim

The entire product surface is one sentence and one link:

```
I want to make a laundrymart system + <our URL>
```

`<intent>` is the source program; `<URL>` is the seed. No signup, no SDK, no install,
no docs to read first. Everything below is what has to be true for that single line to
work — and why "any" is a horizon, not a launch.

## Why "any" is a limit, not a product

The rail that makes generation safe is a **closed verb set + a diff-oracle** ([ERP.md
§0.5, §0.14, §0.17](ERP.md)). Those exist *per domain*, earned one clean abstraction at
a time — exactly as [HolyGrail.md](HolyGrail.md) states for migration: *"eat any instance
is earned one diff-oracle at a time… proven by the first clean abstraction."* So
AnyAppMaker is the limit of a sequence — ERPMaker → RetailMaker → FieldServiceMaker → …
— each new vertical admitted only when its verbs and oracle are witnessed. There is no
shortcut that produces "any app" while keeping the safety property. The honest move is to
build the first term well; the horizon is the union of the terms.

## The womb, not the prompt

A document is something you *read*; the framework is something you are *in*. An
environment teaches without a prompt when it is, at once:

1. **Executable** — verbs are introspectable and callable (§0.14); you learn by running.
2. **Exemplary** — a witnessed sibling bundle exists; you author by imitation.
3. **Falsifiable** — the diff-oracle gives ground-truth pass/fail to the cent (§0.17).

A prose prompt has none of the three — static, singular, unfalsifiable — which is why
prose intros are the degraded case. This is not new doctrine: it is the project's PRIME
RULE (*extract, copy the pattern you find, never invent*) applied to authorship. The
offspring **inherits**; it does not invent.

| Metaphor | Organ | Mechanism |
|---|---|---|
| cord | state / nourishment | the op-log + `apply` |
| env | the chemistry it floats in | the WASM kernel + verb set, introspectable |
| life support | keeps it viable | the diff-oracle / conformance gate |
| exit | the birth canal | package-to-offline-HTML (S284), signed (W-SIGN) |
| genome | inherited material | the witnessed verbs + the reference bundle |

## The front-end is a compiler, not a prompt-maker

Between a non-expert's sentence and the digestible bundle sits a **compiler front-end**
with three jobs:

1. **Elicit** — interview to fill underspecification (deposits? refunds? tax?).
2. **Lower** — emit conformant rule rows over the closed verb set (the IR).
3. **Gate** — feed the womb, run the oracle, bounce failures.

The real value is step 1, and specifically **harvesting the operator's ground truth as
plain-language witnesses** ("ring up three sample washes; tell me the totals"). A
non-expert cannot author a rule but can recognize a receipt; the oracle then catches the
*safe-but-wrong* (a flawlessly-conforming, financially-wrong app) against witnesses the
front-end collected. Iron rule of the loop: **failures return as plain questions, never
error traces; the user validates behavior, never rules.**

**BYO-LLM, as a bundle — never a SaaS.** The front-end calls the user's *own* LLM
endpoint and runs client-side. Host it and you reintroduce the platform dependency the
whole architecture deletes. A weak model buys more round-trips, never a corrupt app,
because the gate is downstream of the model and model-agnostic.

## The URL does triple duty

For `<intent> + <URL>` to fire the whole machine, one address is three things:

- **To the AI: an agent-bootstrap** — reading it turns a generic LLM into the front-end
  (grammar, genome, witness protocol, and the *no-invent-a-verb* clause).
- **To the human: a runtime womb** — opened in a browser it loads the bundle, runs the
  oracle, and births the offline HTML.
- **To both: a signed genome** — its content is a signed fact (W-SIGN), so forks/mirrors
  stay trustworthy; lose the canonical URL, push it elsewhere, nothing lost (git).

**The seam:** a chat LLM talks and emits a bundle but cannot fold a log, run the oracle,
or package HTML — so the LLM is the front-end and the womb at the URL is the back-end;
the handoff is the bundle. Smoothness scales with agent capability (an agentic LLM closes
the loop by calling the womb-as-tool; a plain one needs copy-paste). **Correctness does
not** — the gate always runs in the womb.

## The recursive consequence

An offspring that is itself executable + exemplary + falsifiable **is a new womb.** So the
marketplace is not a catalog of static bundles but a **living population** — each birthed
vertical can gestate the next that forks it. The network grows like a culture, not a
download count. The price that keeps it a species and not a cancer: **no birth without a
passing oracle** — the oracle is the selection pressure; imitation without it propagates
the parent's defects down the lineage.

## Honest boundaries

- **Scope = the verb closure.** "Any vertical" means "any vertical expressible in the
  current verbs." The dangerous LLM failure is *inventing a plausible verb* to cover a
  gap; the bootstrap must make the model **admit the gap and stop**, routing new verbs
  (kernel code, outside the rail) to a human.
- **Adapting data is free; adapting the proven JS is gene-editing.** Data-adaptation
  (rules/seed/dictionary) is unlimited and safe; adding a verb punctures the membrane and
  is human-gated.
- **The URL is a single point of trust** — sign the genome; the womb verifies the
  signature before gestating.
- **LLMs fetch URLs unevenly** — content must degrade gracefully; "paste this page to
  your LLM" is the robust floor.
- **Grail-gated.** Live rule-editing (the reflow loop) sits behind E3/E4 in
  [HolyGrail.md](HolyGrail.md); until then the womb authors and seeds, it does not edit
  rules live.

---

*Grounding: [HolyGrail.md](HolyGrail.md) · [ERP.md](ERP.md) §0.4 · §0.5 · §0.9 · §0.14 ·
§0.17 · §0.18 · §2d-3 · [DistributedERP.md](DistributedERP.md) §0 · §6 · S284
offline-HTML · W-SIGN / W-QR-INPUT. The feasible first term is [ERPMaker.md](ERPMaker.md).*
