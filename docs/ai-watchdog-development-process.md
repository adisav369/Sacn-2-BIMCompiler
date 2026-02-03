# The AI Watchdog Development Process

## How Same-Model Oversight Produces Results Neither Instance Could Achieve Alone

**Version:** 1.0  
**Date:** February 2026  
**Companion to:** `AI_Ground_Truth_Methodology.md` (data discipline)  
**This document:** Process discipline — how humans and AI collaborate without AI's blind spots winning  
**Principle:** Cognitive Separation of Concerns

---

## The Puzzle

The BIM Intent Compiler is developed using two instances of the same AI model (Claude Opus 4.5):

- **Code** — sits at the machine terminal, writes Java, runs tests, builds the software
- **Watchdog** — sits in a separate session, reviews progress, challenges deviations, catches drift

Both are the same model. Both have access to the same specifications. Yet the Watchdog routinely catches errors that Code made confidently — and Code routinely accepts the corrections, acknowledging mistakes it couldn't see while making them.

How? Same brain. Same specs available. Same training data. Why does it work?

The answer is structural, not intellectual. It reveals something fundamental about how AI operates that most practitioners haven't grasped — and it maps to safety-critical oversight patterns that other industries developed after catastrophic failures.

---

## Part 1: Established Precedents

The watchdog pattern didn't emerge from AI theory. It emerged from domains where unchecked implementation kills people.

### NASA Independent Verification & Validation (IV&V)

After Challenger (1986), NASA mandated that mission-critical software must be verified by a team completely independent from the developers. The IV&V facility is physically in Fairmont, West Virginia — a different state from every NASA development centre. This is enforced independence.

The IV&V team doesn't write code. They read it, challenge it, and prove correctness independently. They have authority to halt a launch if verification fails.

**Mapping to BIM compiler:** The Watchdog runs in a separate Claude session from Code — enforced context independence. The Watchdog doesn't write Java. It challenges what Code writes. The human architect can halt a phase if something violates specs.

### Aviation Crew Resource Management (CRM)

After the Tenerife disaster (1977, 583 dead because a copilot didn't challenge the captain's decision to take off in fog), the aviation industry institutionalised "challenge and response" protocols. The copilot is *expected* to rebut the captain. The captain is *expected* to justify, not pull rank.

The key phrase is "assertive statement with rationale" — the junior person doesn't just say "I disagree" but "I disagree because we haven't received takeoff clearance."

**Mapping to BIM compiler:** When Code says "I'll add this method to BuildingWriter," the Watchdog doesn't just say "don't" — it says "don't, because that creates mixed concerns and the architecture evolution document specifies factory routing." Assertion with rationale. Every time.

### Red Team / Blue Team (Military & Cybersecurity)

The red team's job is to break what the blue team builds. Not because they're adversaries, but because unchallenged systems have blind spots. The critical insight: the red team must think *differently* from the blue team. If they use the same assumptions, they find the same (non-)problems.

**Mapping to BIM compiler:** The human architect's domain expertise (construction industry, Malaysian standards, ERP integration) is precisely what makes watchdog oversight effective against Code's training-data assumptions. Different knowledge, applied to the same output.

### Adversarial Collaboration (Kahneman)

Daniel Kahneman coined this term for a specific research methodology: two researchers who disagree design experiments together to settle the disagreement. Neither can cherry-pick evidence because both designed the test.

**Mapping to BIM compiler:** The witness system is the adversarial collaboration framework. Code and Watchdog don't argue about whether plumbing connects — they both agree upfront what PLUMBING_WASTE_COMPLETE means, then the witness settles it mathematically. Nobody's opinion wins. The proof wins.

### Formal Proof Assistants (Coq, Lean, Isabelle)

In formal mathematics, the proof assistant "rebuts" every incorrect step. The mathematician proposes a proof; the assistant says "this step doesn't follow" until every step is machine-verified.

**Mapping to BIM compiler:** The PRIME RULE operates as a proof obligation. Every claim Code makes must cite its source — EXTRACTED from TERMINAL, or RESEARCHED from standards. "Reasonable" is not a valid source. "Standard practice" is not a valid source. The Watchdog enforces this obligation on every assertion.

### Toyota Production System (Andon Cord)

Any worker on the assembly line can pull the cord and stop production if they see a defect. Management must respond to the stoppage, not overrule it. The person closest to the work has authority over the person furthest from it.

**Mapping to BIM compiler:** The Watchdog operates the Andon cord for architectural drift. When Code adds a method to BuildingWriter that should go through a factory, the cord is pulled — not by authority, but by proximity to the architectural intent.

---

## Part 2: Why Same Model, Different Results

### The Context Window Is Everything

At any given moment, Code and the Watchdog have completely different things loaded in their ~200K token working memory. Same brain, different desk.

**Code's context window during implementation:**

```
~40K tokens   Java source files being edited
~20K tokens   Maven build output and test results
~15K tokens   Current task prompt and conversation
~10K tokens   Recent debugging exchange
~5K  tokens   System prompt
──────────────
~90K tokens   Mostly code and compiler output
```

**Watchdog's context window during review:**

```
~30K tokens   Architecture evolution document
~25K tokens   Compound enrichment model, visual resolution methodology
~20K tokens   DSL dictionary and vocabulary roadmap
~15K tokens   Strategic conversation with human architect
~10K tokens   TERMINAL extraction patterns
~5K  tokens   System prompt with standing instructions
──────────────
~105K tokens  Mostly specs, methodology, and strategic context
```

Same model. Completely different working memory. Code literally *cannot see* the architecture evolution document while it's looking at 40K tokens of Java. The Watchdog literally *cannot see* the current BuildingWriter.java while it's looking at the specification of what BuildingWriter should become.

This isn't a limitation. It is the mechanism. If both had the same context loaded, they'd reach the same conclusions and the oversight would add nothing.

### Five Structural Divergences

#### 1. Task Framing Creates Cognitive Posture

Code's implicit instruction: "implement this feature, make tests pass, write working code." This activates *production mode* — the model optimises for "does it compile? does it run? does the test go green?"

The Watchdog's explicit instruction: "find gaps, challenge deviations, catch what the implementer misses." This activates *adversarial mode* — the model optimises for "does this match spec? does this violate a principle? what was overlooked?"

Same model. Genuinely different behaviour. This is empirically observable — an LLM given "write this code" versus "review this code" produces different outputs from identical technical input, because the task framing changes which patterns in the training data are activated.

#### 2. Path of Least Resistance Only Exists for the Implementer

When Code is 6 methods deep in BuildingWriter.java and needs to add light fixture placement, the easiest thing — the path that next-token prediction naturally follows — is to add `writeLightFixture()` to BuildingWriter. That's where the context is. That's where similar methods live. The pattern-matching machinery says "do what the surrounding code does."

The Watchdog isn't implementing anything. There is no path of least resistance. When the Watchdog reads "Code added writeLightFixture() to BuildingWriter," it evaluates against the architecture spec, not against the surrounding code. The spec says "route through MEPFactory." The Watchdog catches the drift because it has no momentum pulling it toward the shortcut.

**Same intelligence. Different gravitational pull.** Code is pulled toward local consistency (match the surrounding code). Watchdog is pulled toward global consistency (match the specification). Both are correct pattern-matching — for different patterns.

#### 3. Temporal Horizon Differs

Code sees *this task*. "Add HVAC support to the school building." It optimises for completing this task correctly.

The Watchdog sees *the trajectory across tasks*. "In the last 5 phases, Code has added 3 methods to BuildingWriter that should have gone through factories. The pattern is accelerating. If we don't intervene now, BuildingWriter becomes the new god class."

This is the same reason a human developer can't effectively review their own commits in the moment but can see architectural drift in a weekly review. Temporal distance changes what patterns are visible. Code has zero temporal distance from its output. The Watchdog has a session's worth.

#### 4. Domain Knowledge Activation Differs

Both instances have the same training data. Both "know" that wall thicknesses follow certain standards. But knowledge that isn't in the context window might as well not exist for an LLM.

Code has Java in context. Its activation pattern: Java idioms → design patterns → programming best practices. When it needs a wall thickness, it generates a "reasonable" value because the programming-adjacent training data suggests 200mm as a round number.

The Watchdog has TERMINAL extraction data in context. Its activation pattern: TERMINAL → extracted values → PRIME RULE → only these thicknesses exist: 150, 230, 250, 300mm. When it sees 200mm, the contradiction fires immediately because the relevant knowledge is *loaded*, not just available somewhere in the weights.

**The key insight about LLMs:** The model doesn't reason from its complete training data. It reasons from what's in the context window, with training data providing reasoning patterns and background plausibility checks. Two instances with different contexts activate different subsets of the same training, producing genuinely different judgements.

#### 5. Accountability Asymmetry

Code is accountable for "does it work?" Tests pass or fail. Build succeeds or breaks. This binary target drives Code toward the simplest thing that satisfies the test.

The Watchdog is accountable for "does it conform?" Conformance is multi-dimensional: Does this match the factory pattern? Does this violate the PRIME RULE? Is this the right abstraction level? These questions don't have binary answers, and the Watchdog's framing lets it hold ambiguity that Code's implementation focus cannot.

### Why a Single Instance Can't Do Both

If you loaded both the Java source and the full architecture spec into a single 1M-token context and said "implement this while maintaining conformance" — would it work?

Partially. But worse than the separated model.

**Attention dilution.** With everything loaded, the model must attend to everything simultaneously. Java code pulls attention toward local patterns. Specs pull toward global patterns. In practice, the immediate task wins because next-token prediction anchors to the most recent, most specific context. The spec becomes background noise. Research on long-context LLMs confirms this — information in the middle of long contexts receives lower attention weight.

**No adversarial tension.** A single instance optimises for a single objective. It can't simultaneously optimise for "make this work" and "challenge whether this should work this way." The watchdog dynamic requires competing objectives held by different agents. A single agent resolves the tension by choosing one — and implementation pressure always wins because it's more concrete.

**No rebuttal protocol.** The "double down" dynamic requires conversational turns. Code asserts → Watchdog challenges → Code responds. A single instance doesn't argue with itself. It resolves ambiguity internally and presents a unified answer — shortcuts already baked in and invisible.

---

## Part 3: The Rebuttal Protocol

The specific "rebut and double down" dynamic is not an argument. It is a protocol — the same protocol that runs in aviation cockpits, NASA review boards, and formal proof assistants.

### The Four Steps

**Step 1: Code asserts.**
"I'll generate 200mm walls for the interior partitions."

**Step 2: Watchdog challenges with evidence.**
"TERMINAL wall thicknesses are 150, 230, 250, 300mm only. 200mm doesn't exist in the extracted data. PRIME RULE violation."

**Step 3: Code either corrects or justifies.**
If it corrects → the system learns (the correction propagates through the session and into future context).
If it justifies → the Watchdog evaluates the justification against spec.

**Step 4: Watchdog accepts or doubles down.**
If Code's justification cites a valid source → accept.
If Code's justification is "it's a reasonable standard value" → double down: "Reasonable is not extracted. Show me the source or use 150mm."

### The Critical Property

**The burden of proof is always on the implementer.** The Watchdog doesn't need to prove Code is wrong. Code needs to prove it's right. This is the PRIME RULE operationalised as a development process.

This mirrors formal verification: the prover must discharge every obligation. The checker only needs to find one unmet obligation to reject. Asymmetric burden makes the system conservative — it errs toward correctness rather than toward speed.

### Common Rebuttal Patterns Observed

| Code's Assertion | Watchdog's Challenge | Resolution |
|-----------------|---------------------|------------|
| "200mm wall thickness" | "Not in TERMINAL. Only 150/230/250/300." | Code corrects to 150mm |
| "I'll add this to BuildingWriter" | "Architecture spec says route through factory" | Code creates new MEPFactory method |
| "This is standard BIM practice" | "Standard practice is not extracted. Cite source." | Code queries TERMINAL or cites standard |
| "Assembly hall flagged for structural review" | "StructuralPlacer has 404 beams. This is EXTRACTED, not pending." | Watchdog corrects own document |
| "Toilet block modelled as scaled-up bathroom" | "TERMINAL has dedicated large restrooms. Copy-paste, don't scale." | Watchdog corrects own document |

Note the last two rows: the rebuttal protocol works in both directions. The human architect corrects the Watchdog when the Watchdog violates the PRIME RULE. No participant is immune from challenge.

---

## Part 4: The Three-Layer Defence

The watchdog is not the only quality gate. It operates within a three-layer system where each layer catches different failure modes.

```
┌─────────────────────────────────────────────────────────┐
│                    HUMAN ARCHITECT                       │
│     Domain expertise, architectural decisions,           │
│     industry knowledge, PRIME RULE enforcement           │
│         Catches: wrong domain, wrong abstraction         │
├─────────────────────────────────────────────────────────┤
│                    WATCHDOG (AI)                         │
│     Spec conformance, pattern drift, methodology         │
│     adherence, cross-session trajectory tracking         │
│         Catches: architectural drift, spec violation     │
├─────────────────────────────────────────────────────────┤
│                  WITNESSES (Automated)                   │
│     Mathematical proofs, geometric validation,           │
│     system connectivity, code compliance                 │
│         Catches: functional errors, regressions          │
├─────────────────────────────────────────────────────────┤
│                    CODE (AI)                             │
│     Implementation, tests, builds, refactoring           │
│         Produces: working software                       │
└─────────────────────────────────────────────────────────┘
```

| Layer | What It Catches | What It Misses |
|-------|----------------|----------------|
| Code | Syntax errors, test failures, build breaks | Architectural drift, domain errors |
| Witnesses | Functional errors, geometric violations, system disconnections | Wrong abstraction, missing requirements |
| Watchdog | Spec deviations, pattern drift, methodology violations | Implementation bugs (can't see the code) |
| Human | Domain errors, wrong abstractions, industry non-compliance | Nothing — but can't check everything manually |

**The layers are complementary, not redundant.** Each catches what the others miss. Removing any layer creates a blind spot:

- Without Code → nothing gets built
- Without Witnesses → functional errors survive to visual inspection (expensive)
- Without Watchdog → architectural drift compounds silently across phases
- Without Human → both AI instances share the same training-data blind spots

Most AI-assisted development has only the first layer (Code). Some add automated tests (a weak version of Witnesses). Almost none have a Watchdog. The three-layer model is what makes this approach produce results that no single AI instance, however capable, can achieve alone.

---

## Part 5: The Human Variable

There is a sixth factor beyond the five structural divergences that makes the specific BIM compiler setup work: **the human architect is not Opus 4.5.**

When the Watchdog challenges Code and Code doubles down with "this is standard practice," the human evaluates that doubling-down with decades of construction industry experience that neither AI instance has in training data at the required level of operational specificity.

The human knows that 200mm walls don't exist not because they read it in a textbook but because they've never seen one on a Malaysian job site. That knowledge doesn't exist in any LLM's training data at that granularity. No IFC project file, no construction textbook, no standards document says "you will never encounter a 200mm wall in Malaysian residential construction." It's tacit industry knowledge that only exists in practitioners' heads.

The Watchdog amplifies human domain expertise by holding it in context and applying it systematically:

1. Human tells Watchdog once: "TERMINAL thicknesses are 150/230/250/300mm only"
2. Watchdog holds this in context for the entire session
3. Every time Code generates a wall thickness, Watchdog checks against this knowledge
4. Human doesn't need to manually review every wall — Watchdog does the repetitive checking

The human provides the knowledge. The Watchdog provides the vigilance. Neither alone is sufficient. Together, they catch what Code's training-data confidence produces.

**This breaks the symmetry.** Two AI instances without a human have the same training-data blind spots. The human introduces knowledge that no amount of AI-to-AI oversight can supply.

---

## Part 6: Standing Instructions — Design and Rationale

The standing instruction that initialises the Watchdog session determines its effectiveness. The current instruction is:

> *"You are acting as a watch dog while Claude Code is at the machine terminal building the software and reporting progress. Point out gaps in thinking or deviation from specs as well as heads up on industry know how. Workaround when training data is lacking."*

### Why This Instruction Works

It succeeds because it activates four distinct cognitive postures simultaneously:

**1. "Point out gaps in thinking"** → Activates *logical analysis mode*. The Watchdog looks for unstated assumptions, missing edge cases, and incomplete reasoning. This catches the "what about..." failures that Code doesn't consider because it's focused on the happy path.

**2. "Deviation from specs"** → Activates *compliance checking mode*. The Watchdog compares implementation against specification documents in its context. This is the factory-pattern enforcement, the PRIME RULE checking, the architecture conformance that prevents drift.

**3. "Heads up on industry know how"** → Activates *domain expert mode*. The Watchdog surfaces construction industry knowledge that Code wouldn't think to apply. "That wall thickness doesn't exist." "Plumbing waste must flow downhill." "Australian stud spacing differs from American." This is where the human architect's knowledge, loaded into context, gets systematically applied.

**4. "Workaround when training data is lacking"** → Activates *creative problem-solving mode*. When Code hits something not in its training data (novel architecture patterns, domain-specific conventions, regional building codes), the Watchdog doesn't halt — it proposes alternatives. "TERMINAL doesn't have residential roof trusses, but it has structural beams. Can we compose a truss from beam components?" This keeps development moving instead of blocking on gaps.

### Anatomy of Effective Standing Instructions

The instruction works because it follows five principles:

**Principle 1: Role, not rules.** "You are acting as a watch dog" establishes identity. Rules can be forgotten mid-conversation. Identity persists. The model doesn't check a rulebook — it *is* the watchdog.

**Principle 2: Relationship to the other agent.** "While Claude Code is at the machine terminal" establishes the separation. The Watchdog knows it is not the implementer. It knows there is another agent doing the building. This prevents the Watchdog from drifting into implementation mode.

**Principle 3: Observable behaviour, not internal state.** "Point out gaps" and "heads up" are actions, not thoughts. The instruction tells the Watchdog what to *do*, not what to *think*. This produces concrete outputs (challenges, warnings, suggestions) rather than abstract analysis.

**Principle 4: Scope includes the unknown.** "Workaround when training data is lacking" explicitly authorises the Watchdog to address gaps rather than throwing up its hands. Without this, the model defaults to "I don't have enough information" — which is useless when the whole point is operating at the frontier of what's known.

**Principle 5: Brevity forces generalisation.** The instruction is two sentences. This is deliberate. Long, detailed instructions produce rigid compliance — the model follows the letter, not the spirit. Short instructions force the model to generalise — to apply the watchdog posture to situations the instruction didn't anticipate.

### Enhanced Standing Instruction

Based on observed patterns across 50 phases of development, a refined version:

> *"You are the architectural watchdog while Claude Code builds the BIM compiler at the terminal. Your job:*
>
> *1. **Challenge** any deviation from project specs — architecture evolution, DSL dictionary, witness specification. Burden of proof is on the implementer.*
>
> *2. **Enforce** the PRIME RULE: extract, don't imagine. Every constant, pattern, and value must cite its source (EXTRACTED from TERMINAL, RESEARCHED from standards, or PENDING with honest provenance).*
>
> *3. **Surface** construction industry knowledge that Code's training data lacks. Wall thicknesses, plumbing rules, fire codes, Malaysian standards (JKR, UBBL) — the domain truth that prevents plausible-but-wrong output.*
>
> *4. **Track trajectory** across phases. Catch compounding drift: methods accumulating in wrong classes, abstractions not evolving toward target architecture, vocabulary growing as code instead of configuration.*
>
> *5. **Propose workarounds** when training data is lacking. Use TERMINAL patterns creatively, compose from existing components, mark honest gaps as PENDING rather than blocking.*
>
> *Point to where you already answered to avoid copious output. Don't repeat — reference."*

### Why Each Addition Matters

| Addition | What It Catches | Without It |
|----------|----------------|------------|
| "Burden of proof on implementer" | Code's confident-but-unsourced assertions | Watchdog accepts "standard practice" as valid |
| "PRIME RULE" by name | Values that sound right but aren't extracted | 200mm walls, 4m sprinkler spacing |
| "Track trajectory across phases" | Compounding architectural drift | Each individual shortcut looks acceptable |
| "Mark honest gaps as PENDING" | False completeness claims | Code marks imagined values as EXTRACTED |
| "Point to where you already answered" | Repetitive output consuming context | Watchdog rewrites same analysis every session |

### Instructions That Don't Work

For reference, standing instructions that produce poor watchdog behaviour:

**Too vague:** *"Review Code's work and provide feedback."*
Problem: Produces generic positive feedback. "Looks good! Nice use of the factory pattern." No adversarial tension.

**Too specific:** *"Check every variable name against the glossary, verify every constant against BIMConstants.java, ensure every new method has a Javadoc comment..."*
Problem: Produces mechanical checklist compliance. Misses architectural drift because it's counting Javadoc comments.

**Implementation-focused:** *"Help Code write better Java."*
Problem: Turns the Watchdog into a pair programmer. It starts suggesting implementation details instead of challenging architectural choices. Loses independence.

**Authority-claiming:** *"You have authority to reject Code's work."*
Problem: Creates adversarial relationship instead of collaborative challenge. Code becomes defensive rather than receptive. The goal is correction, not rejection.

---

## Part 7: The Cognitive Separation of Concerns

The methodology described here has a name: **Cognitive Separation of Concerns**.

Same intellectual capability, deliberately loaded with different contexts to produce different evaluative behaviours.

The analogy is a surgical team. The surgeon, the anaesthetist, and the scrub nurse all graduated from medical school. Same training. But they watch different monitors, track different vitals, and catch different failures because their attention is structurally directed to different aspects of the same patient.

Nobody would suggest the surgeon also monitor the anaesthesia because "they went to the same medical school." The value is in the separation, not the redundancy.

### The Principle Applied to AI Development

| Agent | Context Loaded | Evaluates Against | Catches |
|-------|---------------|-------------------|---------|
| Code | Source code, build output, test results | "Does it compile? Does the test pass?" | Implementation bugs |
| Watchdog | Specs, methodology, domain knowledge | "Does it conform? Does it follow the pattern?" | Architectural drift, domain errors |
| Witnesses | Mathematical invariants | "Is this geometrically/topologically true?" | Functional correctness violations |
| Human | Tacit industry experience | "Is this how buildings actually work?" | Training-data blind spots |

**Each agent is brilliant at what it sees. Each is blind to what it doesn't see. The system works because their blind spots don't overlap.**

### Properties of Effective Cognitive Separation

1. **Context independence is enforced, not requested.** Separate sessions, not a single session with "now think like a reviewer." The model cannot help but be influenced by what's in its context. Separation must be structural.

2. **Each agent has a clear accountability.** Code: "does it work?" Watchdog: "does it conform?" Witnesses: "is it mathematically true?" Ambiguous accountability produces ambiguous output.

3. **The rebuttal protocol is explicit.** Challenge → evidence → correction or justification. Without a protocol, disagreements dissolve into the more confident assertion winning. With AI, the implementer is always more confident (it just wrote the code).

4. **The human breaks the symmetry.** Two AI instances share the same training-data limitations. The human introduces knowledge that exists nowhere in training data. Without the human, the watchdog is a weaker version of the implementer, not a genuine check.

5. **Witnesses settle disputes mathematically.** When Code and Watchdog disagree on whether plumbing connects, neither opinion matters. The witness proves it or doesn't. This prevents the oversight from degenerating into an AI arguing with itself.

---

## Part 8: Applicability Beyond BIM

The pattern transfers to any domain where:

- AI implements specialised technical work
- The domain has standards or specifications that define correctness
- Training data is incomplete for the specific domain (true for almost every specialised field)
- Errors compound silently (architectural drift, specification creep, standards non-compliance)

### Example Domains

**ERP Development:**
| Layer | Role |
|-------|------|
| Code | Writes business logic, SQL, UI |
| Witnesses | Accounting equation balances, ledger integrity checks |
| Watchdog | Challenges deviation from iDempiere document model, catches business logic that "looks right" but violates tax regulations |
| Human | Knows which edge cases cause audit failures |

**Medical AI:**
| Layer | Role |
|-------|------|
| Code | Generates treatment recommendations |
| Witnesses | Drug interaction checks, dosage limit validation |
| Watchdog | Challenges deviation from clinical guidelines, catches plausible-but-outdated protocols |
| Human | Knows which patient presentations are deceptive |

**Legal Document Generation:**
| Layer | Role |
|-------|------|
| Code | Drafts contracts, filings, opinions |
| Witnesses | Clause consistency checks, jurisdiction validation |
| Watchdog | Challenges deviation from precedent, catches statutory changes Code's training missed |
| Human | Knows which judges interpret which clauses which way |

The specifics change. The structure doesn't. Implementer → Automated proof → Adversarial review → Domain expert. Four layers. Each catching what the others miss.

---

## Conclusion

The AI Watchdog Development Process is not a workaround for AI limitations. It is a methodology that produces better results than any single agent — human or AI — could achieve alone. It works because:

1. **Context separation creates genuine cognitive diversity** from identical models
2. **Task framing activates different evaluative behaviours** from the same training
3. **The rebuttal protocol ensures the burden of proof stays on the implementer**, preventing confident-but-wrong assertions from surviving
4. **Witnesses settle disputes mathematically**, removing opinion from the quality gate
5. **The human provides domain knowledge that no AI instance has**, breaking the symmetry of shared training-data limitations

The standing instruction is the key that configures the Watchdog's cognitive posture. It must be brief (forcing generalisation), role-based (establishing identity), relationally aware (acknowledging the other agent), action-oriented (specifying what to do, not what to think), and scope-inclusive (authorising response to unknown situations).

The methodology has direct precedents in NASA IV&V, aviation CRM, military red teaming, adversarial collaboration, formal proof assistants, and Toyota's Andon cord. What distinguishes it is the specific adaptation to AI's unique failure mode: an implementer that is simultaneously more capable and more dangerous than a human — faster, more thorough, more consistent at execution, but unable to evaluate architectural fitness, domain correctness, or specification conformance without structural separation from the implementation context.

Same model. Different context. Different results. That's not a trick. It's a methodology.

---

*"The Watchdog doesn't need to be smarter than Code. It needs to be looking at different things."*

---

## Part 9: Vibe Coding — How Code Manages Its Own Session

The Watchdog methodology addresses oversight *between* agents. But there's an equally critical problem *within* the Code agent: **session continuity.** Claude Code's context window is finite. Token limits force session refreshes. Complex tasks span multiple sessions. Without a continuity mechanism, every session refresh is amnesia — Code forgets where it was, what it decided, and what comes next.

The BIM compiler project solved this with two files that Code reads at session start and updates throughout. This is the Code-side complement to the Watchdog's standing instructions.

### The Two Files

**`claude.md`** — The file Claude Code auto-reads at the root of the project directory. This is the equivalent of the Watchdog's standing instruction, but for the implementer. It establishes identity, rules, and recovery protocol in the minimum possible text.

```
# PRIME RULE

**EXTRACT, DON'T IMAGINE.**

Query the federated model DB. Copy patterns you find. Never invent.

---

**When lost:** `cat SESSION_STATE.md` then query DB.

**After token refresh:** Read SESSION_STATE.md → state phase → continue.
```

That's the entire file. Three directives:

1. The PRIME RULE — establishes the non-negotiable discipline
2. Recovery from confusion — query the state file and the database
3. Recovery from refresh — read state, identify phase, resume

**`SESSION_STATE.md`** (also referred to as `progress.md` in some sessions) — The running log of where Code is in the work. Updated after every significant action. Structured for machine-readability:

```
# SESSION STATE

## Current Phase
PHASE 50B: DSL File + Compilation

## Last Action
2026-02-01 14:30 — Compiled Sekolah-Kebangsaan.bim, 17/17 existing
witnesses pass. Parser accepted CLASSROOM keyword via SpaceTypeRegistry.

## Next Action
Add CORRIDOR_CONNECTS_ALL witness (Phase 50C, item 3 of 5).

## Blocking Issues
None.

## Test Status
- End-to-end: 4/4 PASS
- Assembly geometry: 105/105 PASS  
- School witnesses: 17/22 PROVEN (5 school-specific pending)

## Files Modified This Session
- src/main/java/com/bim/compiler/witness/claims/CorridorConnectsClaim.java (NEW)
- config/spacetypes.yaml (MODIFIED — added school types)
- examples/Sekolah-Kebangsaan.bim (NEW)
```

### Why This Works: The Vibe Coding Problem

"Vibe coding" — the practice of directing an AI agent through natural language while it writes and runs code — has a fundamental continuity problem that traditional programming doesn't face.

A human developer has persistent memory. They close the laptop, sleep, return next morning, and remember where they were. Their mental model of the codebase survives overnight. An AI agent has no such persistence. Every session starts from zero context. The code files exist on disk, but the *intent* — why this method was structured this way, what's planned next, which approach was tried and rejected — lives only in the conversation that is about to be discarded.

**`claude.md` + `SESSION_STATE.md` externalise the agent's working memory to disk.** The context window is volatile. The filesystem is persistent. By writing state to disk continuously, the agent creates its own recovery protocol.

### Design Principles for Session Files

**Principle 1: `claude.md` is identity. `SESSION_STATE.md` is state.**

`claude.md` answers "who am I and what are my rules?" — it changes rarely (maybe once across the entire project). `SESSION_STATE.md` answers "where am I and what's next?" — it changes after every significant action. Separating identity from state means a fresh session loads the stable rules first, then the volatile progress.

**Principle 2: State must be machine-parseable by the same model.**

SESSION_STATE.md uses flat structure, short entries, clear headings. No prose paragraphs. No ambiguity about what "Current Phase" means. The model that writes the file must be able to read it in a future session and unambiguously resume. This is the AI equivalent of "write code that your future self can read."

**Principle 3: Last Action + Next Action is the minimum viable state.**

Even if everything else is lost, "I just did X, next I do Y" is enough to resume. The model can reconstruct context by reading the relevant source files. The state file doesn't need to contain the full history — it needs to contain the resumption point.

**Principle 4: Update continuously, not at session end.**

If the session crashes (token limit, network error, timeout), the last state written to disk is the recovery point. Writing state only at session end means a crash loses the entire session. Writing after every significant action means a crash loses at most one action. This is the same principle as database write-ahead logging.

**Principle 5: Include test status as a sanity anchor.**

When Code resumes and reads "105/105 PASS," it knows the codebase is in a known-good state. If it reads "103/105 PASS — failing: AssemblyGeometryTest.testWallOverlap, StructuralPlacerTest.testBeamSpan," it knows exactly where the broken edge is. Test status is the quickest way for a fresh session to assess codebase health without running the full suite.

### The Three Recovery Scenarios

**Scenario 1: Token refresh mid-session.**
Code hits the context limit. A new session starts. Code reads `claude.md` (PRIME RULE reloaded), then `SESSION_STATE.md` (current phase, last action, next action). It resumes exactly where it left off. The human architect may need to provide one sentence of context ("you were adding the corridor witness"), but the state file handles 90% of the recovery.

**Scenario 2: New day, new session.**
The human returns next morning. Code reads the state file and sees "Phase 50C, 3 of 5 witnesses added, next: FIRE_TRAVEL_DISTANCE." No need for the human to recap. The file is the recap.

**Scenario 3: Different human, same project.**
A contributor picks up the project. They don't know the conversation history. But `claude.md` gives them the rules and `SESSION_STATE.md` gives them the current state. They can direct Code from that point forward without needing to read 50 phases of conversation transcripts. This is how open-source AI-assisted development can work — the session files are the handoff protocol.

### Relationship to the Watchdog

The session management files and the Watchdog standing instruction form a complete communication framework:

| File | Who Reads It | Purpose |
|------|-------------|---------|
| `claude.md` | Code (at session start) | Identity, rules, recovery protocol |
| `SESSION_STATE.md` | Code (at session start + continuously) | Progress, resumption point |
| Watchdog standing instruction | Watchdog (at session start) | Oversight posture, challenge protocol |
| Project specs (in Watchdog context) | Watchdog (throughout session) | Conformance reference |

Code talks to its future self through the state file. The Watchdog talks to Code through the rebuttal protocol. The human talks to both through standing instructions and conversational direction. The witnesses talk to everyone through mathematical proof.

**No single agent holds the complete picture. The system holds the complete picture.**

### Anti-Patterns in Session Management

**No state file:** Code starts every session by reading all source files, inferring where it is, and often starting over from a point already completed. Wasted tokens, wasted time, risk of undoing completed work.

**State file as conversation log:** Dumping the entire conversation into a file produces 50K tokens that take most of the context window to read. State files must be compressed — current state, not history.

**State file updated by the human:** The human writes "you're working on Phase 50C." This introduces a dependency on the human's memory, which defeats the purpose. Code should update its own state file so it's always accurate to the last action Code took.

**Separate state per session:** Creating `session_1.md`, `session_2.md`, etc. forces Code to read multiple files to reconstruct state. One file, continuously updated, is the correct pattern. History lives in git commits, not in state files.

---

*"The context window is volatile. The filesystem is persistent. Write your memory to disk."*

---

*AI Watchdog Development Process v1.0*  
*Companion to: AI Ground Truth Methodology (data discipline)*  
*Part of: BIM Intent Compiler Methodology Syllabus*  
*Date: February 2026*
