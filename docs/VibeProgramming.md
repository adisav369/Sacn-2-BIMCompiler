# Vibe Programming — How This Compiler Was Built

> **Foundation:** [The Drift](LAST_MILE_PROBLEM.md) · [TestArchitecture](TestArchitecture.md) · [MANIFESTO](MANIFESTO.md)

<div class="bim-banner" markdown>
<b>One human. Zero traditional coding.</b> This compiler was built by a Java-literate ERP architect using AI as a force multiplier — domain expertise steers, AI types at the speed of thought. Current metrics in [PROGRESS.md](https://github.com/red1oon/BIMCompiler/blob/master/PROGRESS.md).
</div>

---

## What Is Vibe Programming?

In February 2025, Andrej Karpathy (co-founder of OpenAI, former Tesla AI director) coined the term:

> *"There's a new kind of coding I call 'vibe coding', where you fully give in to the vibes, embrace exponentials, and forget that the code even exists."*

He described accepting LLM suggestions without fully reading the code, running the app, seeing if it works, and iterating. He noted this works for throwaway weekend projects, not production code.

**This project disagrees with half of that statement.** The BIM Compiler is production-grade — 35 buildings compiled, 6 mathematical gates, deterministic output. But it was built entirely through AI-assisted programming. The difference: **domain expertise is the guard rail, not the code.**

---

## The Industry Numbers

The shift is real. These are the numbers the sceptics should weigh:

| Metric | Figure | Source |
|--------|--------|--------|
| Developers using or planning to use AI tools | 76% | Stack Overflow Developer Survey 2024 |
| GitHub Copilot paid users | 2M+ | GitHub, late 2024 |
| Code suggestions accepted (Copilot) | ~30% | GitHub Blog, 2023-2024 |
| Developer task completion speed with AI | 55% faster | GitHub/Microsoft Research, 2023 |
| AI-generated code share in enabled repos | ~46% | GitHub Octoverse 2024 |
| Code churn increase with AI assistants | +39% | GitClear "Code Quality in 2024" |

That last number is the one that matters. **39% more churn** means AI-generated code gets written and then rewritten. The code drifts. The architecture erodes. This is the central risk — and the central problem this project solved.

---

## Why It Works Here (And Fails Elsewhere)

Most vibe-coded projects fail because the human doesn't know what correct looks like. They accept whatever the AI produces because they can't evaluate it. The AI is both the author and the reviewer — a closed loop with no external ground truth.

This project has three things most don't:

### 1. A domain expert who knows what correct looks like

The creator, [Redhuan D. Oon](mailto:red1org@gmail.com), has two decades in ERP systems — [ADempiere](https://adempiere.net/) (2006), [iDempiere](https://idempiere.org/) (2010), and the BIM Compiler (2025). He helped start the ADempiere fork that led to iDempiere, wrote plugins and core improvements in Java, and knows the language and the ERP internals. But vibe programming changes the role: instead of typing Java line by line, he **supervises at the speed of thought** — his Java insight provides the key questions that steer the AI, while the AI handles the typing at a pace no human can match.

He writes **specifications**: what M_BOM means, how C_Order flows, why a wall must sit on a slab. The AI writes the Java. The domain expert evaluates whether the output is correct — not just by reading the compiled building, but by asking the precise Java questions that expose whether the AI's implementation actually honours the spec.

This is not a non-programmer hoping AI gets it right. This is a **Java-literate ERP architect using AI as a force multiplier for his domain expertise**.

### 2. Deterministic verification (no "looks right")

Every compiled building passes through [6 mathematical gates](TestArchitecture.md):

| Gate | What it proves |
|------|---------------|
| G1 COUNT | Input BOM quantity = output element count |
| G2 VOLUME | Compiled bounding volume matches reference |
| G3 DIGEST | Byte-level hash of output matches known-good baseline |
| G4 TAMPER | No file was modified outside the compilation pipeline |
| G5 PROVENANCE | Every output element traces back to a BOM line |
| G6 ISOLATION | No cross-building contamination between compilations |

The AI cannot cheat these gates. G3 alone — a cryptographic digest of the entire output — means a single wrong coordinate in a 48,428-element building fails the build. There is no "close enough."

### 3. The Drift is tracked, not hidden

When AI-generated code drifts from spec — and it does, consistently — the drift is documented. [The Drift](LAST_MILE_PROBLEM.md) tracks every known failure mode: walls that don't sit on slabs, columns that overlap, coordinates that shift by millimetres. 11 drift points, each citing the spec section it violates.

The project doesn't pretend AI code is perfect. It assumes AI code will drift and builds the infrastructure to catch it.

### 4. Build on known frameworks, never from scratch

This may be the most important lesson after months of vibe programming: **LLMs extrapolate well from established patterns. They hallucinate when there is no pattern to follow.**

Every major module in this project is built on a framework the LLM already knows:

| Module | Framework it builds on | Why the AI gets it right |
|--------|----------------------|------------------------|
| Data model | [iDempiere](https://idempiere.org/) ERP tables (M_Product, M_BOM, C_Order) | 20 years of open-source ERP code in training data |
| Compilation pipeline | [Bill of Materials](https://en.wikipedia.org/wiki/Bill_of_materials) explosion — standard MRP pattern | Textbook manufacturing algorithm, widely documented |
| Geometry verbs | Trigonometry, linear algebra, coordinate transforms | Maths doesn't drift — `cos(30°)` is `cos(30°)` in every language |
| Validation rules | [iDempiere AD_Val_Rule](https://wiki.idempiere.org/) pattern | Same validation framework used across the ERP ecosystem |
| 3D viewport | [Bonsai](https://bonsaibim.org/) / Blender Python API | Massive open-source codebase, heavily represented in training data |
| Test architecture | JUnit 5 + SQLite assertions | Standard Java testing — the AI writes these fluently |

When the AI is asked to "write a BOM explosion algorithm," it draws on thousands of MRP implementations it has seen. When asked to "compute a rafter length from pitch and span," it applies trigonometry it has been trained on extensively. When asked to "create a Blender panel with property fields," it follows Bonsai patterns it has seen in the IfcOpenShell codebase.

The failures come when the AI is asked to do something with **no framework precedent** — spatial reasoning about whether a wall sits on a slab, or whether two columns overlap in 3D space. These are the [drift points](LAST_MILE_PROBLEM.md). The pattern: **known framework = reliable code. Novel spatial reasoning = drift.**

The practical rule: if you can frame your problem as an instance of a pattern the LLM has seen before, vibe programming works. If you're inventing a new pattern, write the spec first and supervise every line.

---

## The Toolchain

This project uses [Claude Code](https://claude.ai/), Anthropic's CLI agent, as the primary development environment. The tools that make it work:

| Tool | Role |
|------|------|
| **Agent** (parallel subprocesses) | 4 research threads simultaneously — codebase search, spec verification, test runs in parallel |
| **Bash** (unrestricted shell) | `mvn compile`, `git`, `sqlite3`, pipeline scripts — the AI runs the full build chain |
| **Grep + Glob** (ripgrep-speed search) | Pattern matching across 400+ Java files in milliseconds |
| **Edit** (surgical diff) | One exact string replacement — not whole-file rewrites |
| **Read** (multimodal file access) | Code, images, PDFs, screenshots — the AI reads the same artefacts the architect reads |

The workflow:

```
Architect writes spec (what to build, why, constraints)
     ↓
AI reads spec + existing code + test architecture
     ↓
AI writes code (Java, SQL, YAML)
     ↓
AI runs tests (mvn test, Rosetta Stone gates)
     ↓
Gates pass? → commit. Gates fail? → AI reads drift doc, fixes, retries.
     ↓
Architect reviews compiled building in Bonsai viewport
     ↓
Building correct? → next task. Building wrong? → new drift point logged.
```

**Session discipline:** One bounded task per session. The AI reads the spec before writing code. Every code change cites the spec section it implements. Pre-flight citation is mandatory:
```java
// Implementing BBC.md §3.5.2 — Witness: W-FORGE-1
```

---

## What the Sceptics Get Right

The concerns are valid. This project has lived through all of them:

**"AI code drifts from architecture."** Yes. Relentlessly. The AI will invent shortcuts, merge concerns that should be separate, and silently change assumptions. [The Drift](LAST_MILE_PROBLEM.md) exists because this happened dozens of times. The solution is not to stop using AI — it's to build gates that catch the drift before it ships.

**"AI code has more bugs."** The GitClear study found 39% more code churn in AI-assisted codebases. This project's answer: 6 gates, 408 tests, 35 reference buildings. The bug rate per shipped line is lower than most hand-written projects because the verification is more rigorous, not because the AI writes better code.

**"You don't understand your own codebase."** Partially true. The architect understands the *architecture* — what each module does, how data flows, what correct output looks like. He does not memorise every Java method signature. This is a feature, not a bug: the spec is the source of truth, not the code. If the code drifts from spec, the code is wrong — regardless of what it says internally.

**"It won't scale."** Tens of thousands of elements in the Terminal building. Multiple pipeline stages. Multiple databases. Dozens of specification documents. The project is larger than most startups' entire codebases. It scales because the architecture scales — not because the AI understands scale.

---

## The Honest Ledger

| What works | What doesn't |
|---|---|
| Spec → code → test → ship pipeline | AI cannot see spatial geometry — walls, slabs, collisions |
| Parallel agent research (4 threads) | AI invents plausible-looking code that violates spec |
| Deterministic gates catch all regressions | AI cannot evaluate aesthetic quality of compiled buildings |
| Domain expert catches architectural drift | AI forgets constraints from earlier in the conversation |
| 100 sessions, each bounded and verified | Long sessions degrade — quality drops past ~80% context |

The project succeeds not because AI is reliable, but because the verification infrastructure assumes AI is unreliable and proves correctness independently.

---

## Case Study: RTree Federation Viewer (S180–S189)

The strongest evidence for vibe programming at scale is the RTree federation viewer — a city-scale BIM viewer built inside Blender's Bonsai addon in ~3 weeks of Claude-assisted sessions.

### What was built

| Component | Lines | What it does |
|-----------|-------|-------------|
| `operator.py` | ~9,200 | Modal loaders, BACKEND bake pipeline, live-link, navigation, shred, distro |
| `bbox_visualization.py` | ~1,300 | GPU draw handler, RTree spatial queries, search, drill-down (L0→L1→L2) |
| `blend_cache.py` | ~1,400 | Full load, BLOB tessellation from SQLite, LOD manager |
| `blob_tessellate_worker.py` | ~420 | Discipline-based chunk baking subprocess |
| UI panels, logging, tests | ~800 | N-panel cockpit, discipline bars, storey filter |
| **Total** | **~13,000** | **Complete BIM federation viewer** |

### Proven at scale

- **1,063,911 elements** in sandbox city (12 disciplines, 35 buildings)
- **190,000 meshes** tessellated from SQLite BLOBs in <45s
- **Fine-grained Outliner** — VENT/HEAT/PLB/SAN/HVAC/ARC/STR/VOID, not generic MEP
- **Session .blend ~1MB** — links to baked chunks, instant save
- Posted to [osARCH forum](https://community.osarch.org/) with video proof (April 2026)

### What it would cost without AI

| Approach | Team | Duration | Cost (USD) |
|----------|------|----------|------------|
| Expert team (3–4 people) | Blender dev + BIM domain + perf engineer | 6–9 months | $150–300K |
| Single senior generalist | Blender + IFC + SQLite + GPU (rare unicorn) | 12–18 months | $120–200K |
| Outsource to BIM software firm | Contract team | 9–12 months | $200–400K |
| **Claude-assisted (actual)** | **1 domain expert + Claude Code** | **~3 weeks** | **Subscription** |

The skill intersection — `bpy.data.libraries.load(link=True)` + `IfcOpenShell geom.iterator()` + SQLite RTree + Blender GPU instancing + BIM domain knowledge — exists in perhaps 50 people worldwide. Hiring even one of them takes months.

### Why the multiplier is so large

**Iteration speed.** Each "try → see in Blender → fix" loop (baked-link path, disc suffixes, chunk naming, fly-to distance, Outliner hierarchy) takes 5–10 minutes with Claude. A developer would need a day per loop — reading Blender API docs, testing, debugging. S189 alone had ~30 such loops.

**Dead-end detection.** GN mode (Geometry Nodes instancing) was explored S165–S176 then halted — 8-minute evaluation overhead at 500 modifier trees made it unviable. A hired team would have burned weeks before discovering that. Claude explored it in 3 sessions.

**Domain bridging.** The architect brings "PLB pipes should not inflate the building bbox" and "disciplines must be contiguous per chunk." Claude brings `bpy.ops.wm.redraw_timer(type='DRAW_WIN_SWAP', iterations=1)` to force status bar updates during blocking loads. Neither alone could have built this.

---

## Case Study: The Browser Pivot (S165–S231)

The second case study is more instructive than the first, because it documents a **complete architectural reversal** — built collaboratively across ~60 sessions with Claude Code, where each session's constraints forced the next decision.

### The assumption we started with

From S165 onward, the viewer was Blender + Bonsai. The BIM compiler produced SQLite databases; Bonsai loaded them. This was the right assumption at the time: Bonsai had GPU rendering, IFC awareness, and a Python plugin API. The goal was city-scale BIM federation inside Blender.

### S165–S174: Building the world's most sophisticated Blender BIM viewer

The first attempt at scale used **Geometry Nodes (GN)** — Blender's node-based instancing system — to render 1M elements as GPU instances. By S172, [`geom.iterator()` replaced the slower `create_shape()`](https://github.com/red1oon/BIMCompiler/commit/f3d2902b2b7fb578b8916565d9db7f1ee76f69bf) per-element tessellator, and BLOBs started flowing from SQLite into Blender via `from_pydata()`.

The architecture at S174 was impressive:

- 3-building pipeline (Clinic / Hospital / Terminal), parallel discipline merge
- 123,573 unique meshes in a 305MB `library.blend`
- Hash-addressed geometry deduplication: identical components share one mesh
- `element_instances.geometry_hash` → `component_geometries.vertices BLOB` — [the two-DB split was born here](https://github.com/red1oon/BIMCompiler/commit/f116fdde35eeccbc1d69dc533a89df17bf38a687), not in the browser

### S175: The wall that stopped GN

**One number killed Geometry Nodes:** at 500 modifier trees (each GN instance is a modifier), Blender's evaluation overhead hit **8 minutes per viewport interaction**. The user could not orbit the model. No GN optimisation — chunking, lazy eval, batch size tuning — changed this. [S175 session 2 confirmed it](https://github.com/red1oon/BIMCompiler/commit/2bb9335ed64d6fa8b352cc438872f6a6d4d5e70a): `Collection Info` node with >7K objects causes viewport hang. GN was [halted at S176](https://github.com/red1oon/BIMCompiler/commit/9d1fc6d810993677a4bccab6bc6fa60a7988d5c4).

This was the first crack: the assumed viewer technology had a hard ceiling we hit at real building scale.

### S180–S189: RTree rescues Blender

With GN abandoned, S180 introduced the **RTree Stingy Loader** — load geometry only for elements near the camera, shred everything else, query the SQLite R-tree for the next batch. This worked: `<1s` to first mesh in the viewport at S184. S189 added BLOB tessellation via `from_pydata()` — no `library.blend` file, geometry reconstructed directly from SQLite bytes in Python.

But Blender's save pipeline had its own ceiling. A session `.blend` linking 190K meshes to a baked library file was 300MB+. Every code change required baking. The bake took 45 minutes for a full city. Users needed Blender installed. They needed the Python addon configured. They needed the DB file paths set in the N-panel.

The second crack: Blender was the bottleneck, not the data.

### S192–S193: The last Blender steps

S192 bridged the BLOB gap to the browser — the first time geometry from `component_geometries` was served to a client other than Blender. S193 implemented DLOD auto-linker: `.blend` link/unlink by camera position. These were still Blender sessions. The pivot hadn't happened yet.

### S195: "No .blend files."

The [S195 commit](https://github.com/red1oon/BIMCompiler/commit/66fc9413a9ed8c3ac0b9d900634c57ba2a7e9e65) is the pivot:

```
[S195] Direct DB Streaming — camera-driven mesh from BLOBs, no .blend files
```

The insight, arrived at through constraint: if `from_pydata()` in Python could reconstruct a mesh from a BLOB, so could `new Float32Array(blob)` in JavaScript. The BLOB format didn't care what consumed it — Python or a browser. The SQLite file didn't care what opened it — Blender or sql.js WASM.

Both Blender's Python and the browser's JavaScript were doing the same thing: deserialising `float32` bytes into vertex positions and sending them to a renderer. The **data format was the viewer interface** — not the Blender addon API.

### S200: BIM OOTB — Two DBs. One browser. Zero install.

```
[S200] BIM OOTB — single HTML + Two DBs + sql.js WASM + Three.js. No server.
Proven at 126K elements (LTU AHouse).
```
([commit 7a19d6e](https://github.com/red1oon/BIMCompiler/commit/7a19d6e2543aeae89d93d80042bb8704793da193))

No Bonsai. No Blender. No server. The same two DBs — `_extracted.db` (semantic index) and `_library.db` (BLOB geometry pool) — that the Blender viewer had been reading since S168 were now loaded into a browser tab via `fetch()` and opened with `new SQL.Database(new Uint8Array(buf))`.

The coordinate transform (`IFC X,Y,Z → Three.js X,Z,-Y`) was the only thing that changed. The schema was identical. The BLOBs were identical. The hash-addressed deduplication was identical.

### S220: IFC import in the browser

```
[S220] IFC import: coord fix, unit scaling, material extraction, boolean openings
```
([commit 788eb47](https://github.com/red1oon/BIMCompiler/commit/788eb47cc302b680025af07d7f55cc20de0ae742))

With web-ifc WASM parsing IFC directly in the browser, the full round-trip closed: IFC → browser → same `_extracted.db` + `_library.db` schema → viewer. Bonsai was no longer needed to create the DBs either.

The Bonsai addon — the assumed viewer from S165 — became one of several *optional* consumers of a schema that now ran everywhere.

### S231: InstancedMesh — the schema pays dividends

```
[S231] TE BOM storey fix + InstancedMesh 85% draw call reduction
```
([commit 9cca45a](https://github.com/red1oon/BIMCompiler/commit/9cca45a365d6dfaa650995288f62f1e08fec8926))

Because geometry is hash-addressed from the beginning — not as a performance optimisation but as a consequence of how the extractor works — instancing in the browser required no schema change. Elements with the same `geometry_hash` were grouped in one SQL query result. `THREE.InstancedMesh` consumed them directly. 85% draw call reduction on a 48K-element Terminal building, with no change to the DB.

### What vibe programming produced

The schema in [SQLite3D_Schema.md](SQLite3D_Schema.md) was not designed top-down. It emerged from 60+ sessions of constraint-driven iteration:

| Session | Constraint | Schema consequence |
|---------|-----------|-------------------|
| S168 | Blender session save was too large with BLOBs inlined | Two-DB split: BLOBs in `library.db`, hashes in `extracted.db` |
| S172 | `create_shape()` per element was O(n) and slow | `geom.iterator()` + SHA256 hash-addressed `component_geometries` |
| S173 | 30 buildings needed the same door mesh without copying it | Hash dedup: 123K unique meshes serve 1M elements |
| S175 | GN 500-tree overhead — Blender viewer had a hard ceiling | R-tree virtual table for spatial queries without in-memory BVH |
| S195 | Blender save pipeline was the bottleneck, not the data | Float32 BLOB format works in any language — Python or JS |
| S200 | Zero-install requirement for site use | sql.js WASM opens the same SQLite file in the browser |
| S231 | Mobile draw call budget | `geometry_hash` grouping → `THREE.InstancedMesh` with no schema change |

Each constraint was discovered in practice, not anticipated. Claude Code explored the solution space; the domain expert evaluated whether each result was correct. The schema that emerged is now published as a candidate open standard — not because it was designed to be one, but because 60 sessions of real-world pressure produced something that actually works.

---

## For the Bonsai/BlenderBIM Community

If you're evaluating this project and wondering whether vibe-programmed code can be trusted:

1. **Clone it.** `git clone https://github.com/red1oon/BIMCompiler.git`
2. **Run the gates.** `./scripts/run_RosettaStones.sh classify_sh.yaml` — watch a building compile and pass 6 mathematical proofs.
3. **Read [The Drift](LAST_MILE_PROBLEM.md).** Every known failure is documented. Nothing is hidden.
4. **Check the tests.** `mvn test` — 408+ tests, not mocked, not stubbed, running against real SQLite databases with real BOM data.

The code was written by AI. The architecture was not. The proofs are mathematical. The buildings compile deterministically. Judge by the output, not the author.

---

*Built with [Claude Code](https://claude.ai/) (Anthropic) in ~190 sessions over 5 months. 1M elements compiled, 35 buildings, city-scale federation. Kuala Lumpur, 2025–2026.*
