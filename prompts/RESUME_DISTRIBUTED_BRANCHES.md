# RESUME — Distributed Design Branches (Idea 1): draft-vs-craft, the collaborative Outliner, chat-as-log

```
# ⚠ DO NOT REMOVE
SCOPE: Domain design for offline, signed, git-style collaboration over ONE building — multiple engineers
each on an isolated branch of the signed op-log, a combined dashboard, a collaborative (blame-tinted,
tabbed) Outliner, and a chatbox that IS the op-log read as commit messages. The CRYPTO SUBSTRATE is
already witnessed (see §4); what this spec adds is the DOMAIN layer (branch identity, scope, seam
protocol, op taxonomy, the panel + canvas UX). NON-INVENT: every color = a signer key, every timestamp =
an edge-minted op input, every chat line = a signed op (or a deterministic render of its verb+params);
never a fabricated value. Read the log after every run. Honour until ✅ DONE or ⛔ BLOCKED.
STATUS: SPEC + first witnessed ENGINE SLICE done 2026-06-30. Standalone layer `build/redpill/` (connectors
  STUB seam → engine → gate → chatlog) + 3 node witnesses GREEN **25/25** (W-GATE-CROSS-BRANCH 11/11,
  W-BLAME-COLOR 5/5, W-CHAT-IS-LOG 9/9) — runs through the stub seam, **touches ZERO modeller code**.
  NEXT = (a) port `build/redpill/*` into **bim-ootb** as a standalone `redpill/` module on its OWN worktree
  branch (pure move, no modeller edits — the connector seam makes it trivial); (b) swap stub bodies for live
  `sdg_gate`/`kernel_ops`/`rates.js`; (c) wire the mock (`build/erp/branches_mock.html`) as the view over the engine.
REPO: layer is repo-agnostic by design (pure JS + connector seam). Built+witnessed in bim-compiler; HOME = bim-ootb
  (the consolidating active repo). Modeller is mid-heavy-session → keep red-pill a SEPARATE additive module, never inline.
```

## ▶ RESUME HERE (next dedicated session)

**Where we are:** engine slice GREEN 25/25 in `build/redpill/` (bim-compiler, commit `650c9f1a`, pushed to
`lane/benchmark-clash-resolution`). Mock view: `build/erp/branches_mock.html` (+ `figs/branches_redpill_{v3,capture}.png`).
Public post live on osARCH + reddit/coolgithubprojects (honest final copy; "Scales like Git", web-ifc importer,
"BCF-aligned (planned)"). r/OpenBIM not yet posted (low traffic).

**First action:** port `build/redpill/*` into **bim-ootb** (the consolidating active repo) as a standalone
`redpill/` module on its OWN `/tmp/wt-*` worktree off fresh `origin/main` — **pure move, no modeller edits**
(modeller is mid-heavy-session; keep red-pill additive, self-injecting button). Then:
1. swap the 5 connector stubs for live bindings — `evaluateGate→sdg_gate.evaluate`, `foldCost→rates.js`,
   `sign/verifyChain→kernel_ops.js`, `subscribeOps→'bonsai:oplog'`, `bus→BroadcastChannel`. Engine + witnesses unchanged.
2. wire `branches_mock.html` as the VIEW over the real engine (replace its mock data with `RedpillEngine`/`Gate`/`Chatlog` output).
3. re-run the 3 witnesses in bim-ootb (must stay 25/25) + a headless smoke.

**Still open (design, §3/§12):** seam protocol (shared-datum CAS), Tier-1 heartbeat payload, op-message field default.
**Comms follow-up (if asked):** draft the 4 ready-to-paste reply comments — Speckle? · merge-conflicts(draft/craft)? ·
open-source/license? · scaling? ; consider deploying the mock to a live URL so the post has something to click.
**Positioning banked:** §COMPETE (11 players), §POSITION (prior-art honesty), keystone framing (collaboration falls
out of the signed log "for free" — no server added, the need for one removed).

---

## §0 — THE ONE-LINE THESIS

> **The engine branches the *drafters* and locks the *crafter*, because it merges by folding *measured*
> facts — and only drafting produces those.** The craft/draft boundary IS the single-writer/multi-branch
> boundary. This is the project's prime rule (`EXTRACT OR COMPILE ONLY · never invent`) applied to
> collaboration: drafting = COMPILE work (derives, folds, merges); crafting = INVENT work (taste, no
> ground truth, cannot be merged).

---

## §1 — DRAFT vs CRAFT (the constitutional cut)

- **Drafting = engineering = COMPILE.** Size a beam from a load; route a duct around a clash; price a part
  = Σ(qty×rate); sequence a pour. Each is a *derivation against a ground truth* (load calc / clash geometry
  / rate table / dependency graph). **Non-invent by nature → folds deterministically.** When two drafters
  collide, the collision is **computable**: RED (geometric — `sdg_gate.evaluate`, §GATE-1) or ORANGE
  (financial — `foldCost`). The merge engine adjudicates objectively.
- **Crafting = architectural composition = INVENT.** Move a wall 200mm "because the proportion feels right."
  **No ground truth.** Two atrium visions cannot be merged — there is no RED/ORANGE rule for "uglier." It is
  a human taste negotiation, a single-writer act.

**Consequence:** the distributed branch-merge-fold engine is *constitutionally a drafting engine*. It hosts
the crafted ARC as a given input it extracts from; it must NOT try to *merge* crafting acts.

---

## §2 — THE TRUNK/BRANCH MODEL (git, with the crafter owning trunk)

- **ARC = trunk**, single crafter-owner (LOCK, not branch). Aesthetic composition lives here.
- **Each engineering discipline = a long-lived feature branch** that **rebases onto trunk** and fills it with
  measured facts. STR strengthens · MEP routes · QS prices · 4D sequences · 5D costs. = the VISION-LOCK
  "every non-ARC disc is a WALKER filling ARC." (See [[project_modeller_vision_lock]].)
- A walker branch **references** trunk + sibling walkers **read-only** (= real-world *federation*); it never
  merges *into* another discipline. Cross-discipline "merge" is **clash-coordination at the federation
  layer**, not a git merge.
- The only **true git-merges** are *intra*-discipline (two STR engineers on different zones → merge into the
  STR branch), and those are scope-disjoint with a seam protocol (§3).

This is trunk-based development: crafter owns trunk; drafters live on rebasing branches.

---

## §3 — SPLIT AXES + THE SEAM PROTOCOL

- **Discipline-split (ARC|STR|MEP|…) = the GOOD split for branching.** Element-overlap ≈ 0 (a plumber and a
  structural engineer touch disjoint elements). The only conflicts are **spatial + financial** — exactly
  what §GATE-1 already computes. Branches are near-conflict-free by construction; the gate handles the
  residue. **Idea 1 should land here first.**
- **Zone-split (Floor-1|Floor-2, East|West) = conflict concentrates at the SEAMS** — the column carrying
  F1→F2, the riser spanning wings, the shared grid line. **Boundary-ownership contract:** one zone *owns*
  the shared element; the others *reference* it read-only. **The negotiated datum IS the seam** — reuse the
  SDG `datum_plane` / `rel_anchored` edges as the contract object. Moving *within* your datums is free;
  touching a *shared* datum is the one op-class that needs CAS / owner-gate (= the single contended
  operation class of DistributedERP §5).

⛔ OPEN: exact seam protocol (who claims a shared datum, hand-off gesture, CAS payload). See §12.

---

## §4 — THE SUBSTRATE THAT ALREADY EXISTS (only DONE part of this spec)

| Capability | Witnessed by | Gives us |
|---|---|---|
| Union → total-order → replay → identical projection-hash | `scripts/poc_distributed.js` | branch merge is holder-irrelevant |
| Edge-minted UUID PKs union with **no clash** | `poc_distributed.js` (G-IDENTITY) | push = pure append, never collides |
| Owner-gate (G-SINGLE-WRITER) + CAS for the one contended class | `poc_distributed.js` | scope/seam enforcement on replay |
| Mail-the-append-log → re-fold on node B (+ falsifier) | `scripts/poc_ad_oplog_distrib.js` (W-AD-OPLOG-DISTRIB) | push/pull transport |
| Hash-chain tamper detection | `scripts/poc_chain.js` / live `kernel_ops.js` | the chat history is tamper-evident |
| Edge signature (wrong key fails anywhere) | `scripts/poc_sign.js` (W-SIGN) | blame color = a *real* identity |
| RED/ORANGE conformity gate | `sdg_gate.evaluate` (§GATE-1, sw v18) | computable merge verdicts |
| 5D fold / 4D CPM | `foldCost` / `computeCpm` | ORANGE budget + schedule rails |
| Live cross-tab sync | `viewer/schedule_sync.js applyOp` + `BroadcastChannel('bim_4d')` | the refresh transport |
| "checking… → settle" vitals animation | `poc_system_monitor.js` + field-health | the color-settle pattern (§7) |
| Faceted-lens panel | `modeller/bonsai_outliner.js` | the Outliner host (§8) |

**What's MISSING = the domain layer only:** branch identity (fork-point hash + owner + scope), scope
declaration, seam protocol, the op taxonomy (§5), the tabbed panel + spectate canvas (§8–§9).

---

## §5 — THE OP TAXONOMY (three classes — the load-bearing distinction)

1. **Geometry CRUD** (`MOVE` / `INSERT` / `DELETE` / route / size). Scoped to **your own branch**. Folds the
   model. RED/ORANGE gated. Owner-gate refuses signing into a branch/scope you don't own.
2. **Annotation / wiki** (`POSTIT` / `WIKI` / `COMMENT`). **Cross-branch ALLOWED.** Does **not** fold
   geometry — attaches a note to a target (element guid or world coord). Shows in the Outliner + chat. = a
   GitHub PR review comment, but signed + chained. This is the layer that makes spectating (§9) useful and
   makes merge-review (Idea 2) genuinely collaborative.
3. **Git actions** (`PUSH` / `PULL` / `REBASE` / `MERGE` / `CHECKOUT`). Log-management ops (§10).

Annotation being a *separate non-geometric op-class* is exactly what lets a user drop a post-it on a peer's
model **without edit rights** (§9): the post-it signs under *their own* key as an annotation targeting the
peer's element — geometry CRUD into the peer's branch is still owner-gate-refused.

---

## §6 — TWO LATENCY TIERS (what makes "async + rather quickly" honest)

Two clocks that must NEVER be confused:

- **Tier 1 — Awareness (fast, cheap, ADVISORY).** Each branch broadcasts a lightweight heartbeat ("touching
  these elements / this zone, tip-hash X, coarse delta"). Other viewers paint an **optimistic overlay** —
  pulse a zone, flag a *possible* coarse-bbox clash. Sub-second. **A guess, not a verdict.**
- **Tier 2 — Integration (async background, AUTHORITATIVE).** A Web Worker runs union → total-order → replay
  → §GATE-1 → the *true* RED/ORANGE. Only then does the color **settle** (reuse the §SYSMON
  "checking…→settle" animation). The fast hint hands off to the verified fact.

**Awareness pushes automatically; geometry integration is pulled (user-gated).** Never auto-mutate a
drafter's working scene — matches the SDG doctrine *no auto-converge, explicit hop-by-hop, user-gated*. The
dashboard is a *signal*, not a *force*.

---

## §7 — THE HONEST COLOR LADDER (color = a fact with provenance, never a bare scalar)

- **Provisional** (Tier-1 peer hint) — pulsing / hollow. "Activity here, unverified."
- **Verified** (Tier-2 fold + gate ran) — solid. GREEN clean · ORANGE soft (budget/clearance) · RED hard
  (clash / door-out-of-host).
- **Stale** (load-bearing) — desaturated/grey. *Trunk moved under this branch* → its verdict is against an
  old fork-point and means nothing until re-fold. = the `CLAUDE.md` **"BEHIND/DIRTY = sync, not redo"**
  lesson surfaced AS A COLOR. A branch fades to grey when trunk advances, relights on rebase.

(= the [[feedback_whitebox_no_handwave_geometry]] rule carried into the UI.)

---

## §8 — THE LEFT PANEL = TABBED OUTLINER (three views of ONE signed log)

The `bonsai_outliner.js` panel gains tabs — each tab a different projection of the same fold (the §SE
WBS-outline + Gantt "two views of one schedule" pattern, generalized):

- **Tree tab** — the faceted Outliner, **blame-tinted**: each node tinted by the **color tone of whoever
  last signed it** (W-SIGN key), with that op's edge-minted **timestamp = "last update."** New facets, all
  pure folds: *by-participant* (blame), *by-freshness* (§7 ladder), *by-branch*, *by-gate-state*.
- **Chat tab** — the op-log read as commit messages (§ below).
- **Dashboard tab** — clash matrix (discipline×discipline → RED count, drill to the §GATE finding), budget
  rail (5D variance vs Project Order), schedule rail (4D slip from CPM), **freshness strip** (per discipline:
  tip-hash, ahead/behind trunk, last-folded, stale? = the System Monitor Release row re-pointed at branches).

### The chatbox is NOT a chat — it's the op-log as commit messages

Every line **IS a signed op**, projected to prose:
```
STR · B-204 upsized W12→W14 · +$1.2k ⚠ORANGE budget · 3m ago
ARC · Wall-12 moved 200mm · trunk · 8m ago
MEP · Duct-7 rerouted around Column-3 · RED→resolved · 11m ago
```
Three properties fall out free, none present in a normal project chat:
1. **Non-invent.** The message is the op's description field; if absent, **compiled deterministically** from
   verb+params (`MOVE Wall-12 by (0.2,0,0)`). Never fabricated prose.
2. **The chat history IS the audit trail.** Hash-chained (`verifyChain`) → you *cannot* edit a past message.
   A project chat that is also an immutable signed change-log is a novel artifact.
3. **Same log as the Tree.** Click a chat line → its Outliner node highlights; filter Tree to a participant →
   chat filters to their commits.

---

## §9 — THE MAIN CANVAS (CRUD + git actions + spectate + post-it)

- **Your canvas = your edit surface.** You CRUD on the model (move/route/size) — those edits ARE the signed
  geometry ops (§5.1), signed under your key into your branch. Git actions (§10) issue from the panel/canvas;
  the Outliner syncs to reflect them.
- **Click a peer's update → switch to THAT user's POV, READ-ONLY** (= `git checkout <their-branch>`, detached
  HEAD). The canvas re-renders **their folded branch state** — a deterministic replay of their signed log, so
  you see *exactly* what they see (non-invent: replayed, not guessed). Read-only is enforced for free by the
  owner-gate — you cannot sign geometry ops into their branch.
- **In spectate mode a user MAY drop a post-it (wiki), but NOT edit the model.** The post-it is an
  **annotation op** (§5.2) signed under *your own* key, targeting their element/coord — it doesn't fold their
  geometry, it appears in their Outliner/chat as a review note. ("This column clashes with my riser — please
  move.") Geometry CRUD into their branch stays refused.

**Three distinct modes, kept honest:** *Edit* (own branch, sign geometry) · *Spectate* (peer POV, read-only,
may annotate) · *Pull/Rebase* (consented integration into your branch, §10).

---

## §10 — push / pull / merge / checkout SEMANTICS

- **Push** = append my signed ops to the shared log. **Pure upload, deletes nothing, no PK clash** (UUIDs) =
  the `CLAUDE.md` "push is a clean fast-forward to your own branch" rule. Always safe, always allowed.
- **Pull** = fetch + Tier-2 background-fold + show-diff-in-Outliner + **gated rebase**. Two honestly-different
  incomings:
  - **Trunk pull (ARC changed):** crafter moved a wall under me → my branch goes **stale-grey** → I *must*
    rebase + re-run my gate (did my duct break against the new wall?). "BEHIND = sync, not redo."
  - **Sibling-walker pull (MEP changed):** advisory **reference only** — I don't merge their data
    (cross-discipline = federation), I just **re-check clash** vs their new refs. Different button, lighter.
- **Merge** = the review surface (Idea 2): union logs → run §GATE-1 across them → director sees the RED/ORANGE
  diff → commits or rejects. Reserved for trunk and intra-discipline merges.
- **Checkout** = spectate a peer branch tip read-only (§9).

---

## §11 — WITNESS LADDER (falsifiers first — name the issue each proves)

- **W-BRANCH-MERGE** (extend `poc_distributed.js`): two participants' signed logs → swap arrival order →
  **identical final projection-hash** after total-order (holder-irrelevant); delete one op → its
  contribution vanishes (log is load-bearing, not a hardcode).
- **W-BLAME-COLOR**: Outliner node tints + "last update" times are a *pure projection* of the two logs —
  swap order → same final colors; remove an op → its color contribution AND its chat line both vanish.
- **W-CHAT-IS-LOG**: every chat line maps 1:1 to a signed op; editing the chain breaks `verifyChain` (the
  chat is tamper-evident); a message with no description renders deterministically from verb+params.
- **W-GATE-CROSS-BRANCH**: a synthetic STR-vs-MEP clash on SampleHouse → Tier-1 paints provisional-red →
  Tier-2 fold settles to verified-red; advance trunk → the cell goes stale-grey. *(the smallest slice, §13.)*
- **W-SPECTATE-READONLY**: checkout peer branch → replayed POV == peer's own fold; a geometry CRUD into the
  peer branch is owner-gate-REFUSED; an annotation op under my key SUCCEEDS and appears in their chat.
- **W-SEAM-CAS** (after §12): two zone branches claim the same shared datum → CAS → first in total order
  wins, the other references read-only; no element lost.

---

## §12 — ⛔ OPEN ITEMS (decisions/design still owed)

1. **Heartbeat payload (Tier-1):** element-IDs + zone + tip-hash? size/frequency/decay rule. (What's cheap
   enough to be sub-second yet enough to paint a useful overlay.)
2. **Seam protocol (zone-splits):** the claim/hand-off gesture over a shared `datum_plane`/`rel_anchored`
   datum; the CAS op-class payload; what "reference read-only" renders as in the non-owner's canvas.
3. **Op message field:** author-written optional + deterministic compiled fallback. *(Recommended default —
   adopt unless rejected.)*

---

## §13 — SMALLEST FIRST SLICE (prove the whole shape with one cell)

**Two branch logs + a background fold → ONE clash-matrix cell that:** goes **provisional-red** (Tier-1 coarse
hint) → **settles** to verified-red or green (Tier-2 §GATE-1) → goes **stale-grey** when trunk advances.
One cell, three colors, both clocks — every color traces to a fold. Witness on **SampleHouse** with a
synthetic STR-vs-MEP clash, **node-first (W-GATE-CROSS-BRANCH), before any UI.** Then grow: blame-tinted
Tree → chat-as-log → spectate POV → seam protocol.

---

## §COMPETE — Competitive landscape ("pick their minds")

> Honest framing: **the individual capabilities here have prior art; the *fusion* does not.** Don't claim novelty
> on version-control-for-models, pinned issues, or clash detection — name who owns each, **steal their best UX,
> interop where it's free, and compete only on the combination** (§POSITION). Feature sets move fast — treat the
> "their strength" column as of mid-2026 knowledge and **verify current state before any public claim.**

| Player | Category | What it does | Their strength → STEAL | The gap WE fill | Stance |
|---|---|---|---|---|---|
| **Speckle** | AEC data hub + viewer | git-like **branches/commits**, object diffing, connectors (Revit/Rhino/IFC), **Automate** (rules on commit) | nearest mind: branch/commit UX, object model, Automate = their "gate" analogue, connector strategy | not an editor; server-hosted (host owns truth); merge = data-merge, **not multi-domain clash+budget gate**; no folded 5D/ERP ledger | **INTEROP** — don't refight transport; be a Speckle consumer/producer |
| **Autodesk** (Revit + ACC/BIM 360, Model Coordination) | authoring + CDE | **worksharing = central file + element LOCKING**, cloud co-author, clash, issues | issue workflow, Model Coordination UX, market gravity | locking (the thing we attack); **server-of-record**; no signed log; no offline-week branch; no 5D/ERP fold | **COMPETE** (locking ↔ branches is the headline) |
| **Navisworks** | federation + 4D | **Clash Detective** (gold standard), **TimeLiner 4D**, saved viewpoints | clash grouping, 4D simulation, viewpoint model | read-only aggregator, not an editor; no branching/provenance | **LEARN** (our RED gate + viewpoints) |
| **Solibri** | model QA | **rule-based model checking** + clash | their **ruleset model ≈ our RED/ORANGE gate** — study it for our gate schema | a checker, not a collab substrate; out-of-band | **LEARN** (gate rules) |
| **BIMcollab / BCF (openBIM)** | issue mgmt | **BCF = pinned issues + viewpoints + comments, vendor-neutral** | **closest to our post-it/spectate** — adopt BCF so annotations interop everywhere | issues live in a side DB, **not a signed log that folds the model**; no branch/merge | **INTEROP** — be BCF-compatible on the annotation op-class |
| **Bentley iTwin / iModelHub** | infra digital twin | **changesets** (git-like change log per iModel), conflict handling, enterprise | **changeset model = nearest to our signed-log** for infra; study conflict resolution | server-of-record (iModelHub); infra-focused; no ERP ledger fold | **LEARN** (changeset/merge mechanics) |
| **Trimble Connect / Tekla** | CDE + STR authoring | federation, ToDos, structural authoring | CDE structure, ToDo workflow | server-of-record CDE; no folded enterprise log | LEARN |
| **Bonsai (BlenderBIM)** | open IFC authoring | free, **IFC-native** authoring in Blender | we already reuse its **outliner/library pattern**; IFC-native authoring | single-user desktop; **no collaboration/branch/ledger** — exactly our add | **BUILD-ON** (we add the collab + enterprise fold it lacks) |
| **Onshape** (MCAD, adjacent) | cloud CAD | full **branch/merge/release**, **simultaneous edit with NO locks**, in-browser | the **benchmark** for no-lock multi-user + branch/merge/release UX — proves our thesis in MCAD | MCAD not AEC; server-of-record; no ERP fold | **LEARN** (UX north star) |
| **iTWO (RIB) / Synchro (Bentley)** | 4D/5D | 5D estimating + scheduling / 4D simulation | deep cost+schedule data models | heavy, siloed from authoring + ERP; no signed-log fold | **COMPETE** (our fused 4D/5D wedge) |
| **Figma** (non-BIM, mind-pick) | design collab | multiplayer presence, **branching**, CRDT merge, review | presence, **branch-merge review UX**, spectate ≈ ours | not a BIM/engineering ledger | **LEARN** (review/presence UX) |

## §POSITION — where we sit (the only defensible claim)

Three concentric rings — everyone owns an inner ring; **we are the only one closing the outer**:

1. **Transport / version** (Speckle, iTwin changesets) — move + version model data.
2. **Coordination** (Navisworks clash, Solibri rules, BCF issues) — find clashes, pin issues.
3. **⟵ OURS adds:** a **single signed, serverless (no server-of-record) append-only log** that **folds simultaneously
   into geometry + 4D + 5D + the double-entry ERP ledger**, gated by a **multi-domain merge** (RED clash **AND**
   ORANGE budget on the *same* commit), with the **chat == the tamper-evident changelog**, and **one engine across
   BIM and iDempiere** (the [[red pill]] is a lens over any signed-op-log tree — building element or AD record).

**Honest prior-art verdict:** NOT prior art for branch/merge (Onshape/Speckle), pinned issues+viewpoints
(BCF/BIMcollab), or clash (Navisworks/Solibri). **Plausibly novel in combination:** *one signed log → model +
schedule + cost + ledger, multi-domain-gated, serverless, BIM↔ERP.* "Novel combination" ≠ guaranteed
patentability — a formal search must clear it against the rows above. The strongest claim is the **fold**, not the UI.

**Interop strategy (don't refight won battles):** be **BCF-compatible** (annotation op-class), **Speckle-friendly**
(consume/produce, don't rebuild transport), **build on Bonsai** authoring, take **Onshape's** no-lock UX as the
north star, **Solibri's** ruleset as the gate template.

## §STRATDEC — Open strategic decisions (need your call — see the question prompt)

1. **Debut substrate** — BIM modeller vs iDempiere ERP vs shared-engine-first.
2. **This phase's output** — vision/marketing artifact vs committed spec vs first witnessed code slice.
3. **Brand/positioning** — cross-product platform capability vs a mode inside one product.

---

*Companions: [[project_modeller_vision_lock]] (ARC=sole-edited substrate, Outliner=Find-on-steroids),
`docs/DistributedERP.md` (the serverless-of-record doctrine + §4 guard set), `prompts/SPATIAL_DEPENDENCY_GRAPH.md`
(datum_plane/rel_anchored = the seam contract), the §GATE-1 / §SE / §SYSMON shipped surfaces this reuses.
Mock + figures: `build/erp/branches_mock.html`, `build/erp/figs/branches_redpill_{v3,capture}.png`.*
