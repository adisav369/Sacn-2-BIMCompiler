# DONE — Headline callout boxes on 9 major specs
> Commit: a7bc37ad [S85-headlines]

Add a headline callout box near the top of each major spec. The goal: a
first-time reader should grasp the page's purpose and excitement in 2 seconds,
before hitting the dense tables.

Style: HTML div with a coloured border (like index.md's golden box and BBC.md's
blue box). Each callout should be:
- 2-3 sentences max
- Written for a human, not a spec reader — engage, don't bore
- State what this page proves or enables, not what it contains
- Domain-agnostic where possible (BOM recipe, not "building")

## Pages to add callouts (read each page first to understand its purpose)

1. **MANIFESTO.md** — already has strong opening. Add a callout box after the
   title that captures "A building is a manufactured product with coordinates.
   ERP already knows how to do this."

2. **DATA_MODEL.md** — "4 databases, 120 tables, zero ambiguity. Every element
   traces from library to placement."

3. **TestArchitecture.md** — "6 mathematical gates. Not sampled — proven. If a
   building compiles, every element is accounted for."

4. **BIM_COBOL.md** — "64 verbs that turn BOM recipes into geometry. The domain
   vocabulary sits here — the compiler underneath is generic."

5. **TheRosettaStoneStrategy.md** — "35 real buildings, recompiled from their
   BOMs. If every element lands at the same coordinates, the grammar is certified."

6. **SourceCodeGuide.md** — "Where to start reading. Entry points, DAOs, the
   pipeline — from YAML to verified 3D output."

7. **WorkOrderGuide.md** — "From a 30-line YAML to a verified 3D building in
   one command. This is how you run the compiler."

8. **DISC_VALIDATE_SRS.md** — "9 disciplines, one compiler. Each discipline
   brings its own rules — the compiler enforces all of them."

9. **DocValidate.md** — "Spatial rules from real buildings + regulatory rules
   from building codes. Together they validate every placement."

10. **ACTION_ROADMAP.md** — already has clean header. Skip.

These are suggestions — read each page and write something that fits its actual
content. Don't force the suggested text if it doesn't match.

## Style template

```html
<div style="max-width: 620px; margin: 24px auto; padding: 20px 32px;
  background: linear-gradient(to right, #e3f2fd, #e8eaf6, #e3f2fd);
  border-left: 4px solid #1565c0; border-right: 4px solid #1565c0;">
<b>Headline.</b> Supporting sentence that engages, not describes.
</div>
```

Vary the colours per page — don't make them all blue. Use warm tones for
guides, cool for specs, amber for strategy/roadmap docs.

Do NOT change any content below the callout. Only add the box near the top
(after title and any existing tagline).

Commit message prefix: [S85-headlines].
