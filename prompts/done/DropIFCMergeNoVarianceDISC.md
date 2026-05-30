# ⚠ DO NOT REMOVE — Scope: Drop IFC merge behavior, variance guard, discipline from filename. Read the log after every run.

# Drop IFC — Merge, No-Variance Guard, Discipline from Filename

## Status: IN PROGRESS

---

## Requirements

### 1. Merge Modal
- Always show Merge/New when existing projects exist in IndexedDB
- User chooses: merge into existing project OR create new

### 2. Variance Guard
- Auto-diff (variance comparison) triggers ONLY if filename contains "revised"
- No other keywords — only "revised" (case insensitive)
- If not "revised", merge adds version but does NOT run diff

### 3. Discipline from Filename
- If filename contains a known discipline (ARC, STR, PLB, ACMV, ELEC, FP, VENT, HEAT, SAN, COOL, VOID), use it for ALL elements in that file
- Check last segment split by `_` or `-` (e.g. `LTU_AHouse_HEAT.ifc` → HEAT)
- Valid disciplines: `ARC, STR, MEP, PLB, ACMV, ELEC, FP, VENT, HEAT, SAN, COOL, VOID`
- If filename has no known disc, fall back to IFC class classification (DISC_MAP)

---

## Files

| File | What |
|---|---|
| `deploy/dev/import_worker.js` | `discFromFilename()` extracts disc from filename, `classifyDisc()` uses override |
| `deploy/landing2.html` | Merge modal (always), variance guard (`/revised/i` only) |
| `scripts/extractIFC2DB.js` | `--disc` CLI arg for Node.js batch extraction |

---

## Witnesses

- `§MERGE_PROMPT existing=N file=X` — modal shown
- `§MERGE_ACCEPT target=X version=vN` — merged
- `§MERGE_REJECT file=X` — user chose New
- `§DISC_OVERRIDE filename=X disc=Y` — discipline from filename applied

---

## Anti-Drift

- Do NOT trigger variance on anything except "revised" in filename
- Do NOT invent other keywords (rev, revision, _v1, etc.)
- Do NOT override discipline if filename has no match — fall back to IFC class
- Do NOT change the VALID_DISCS list without updating all three files
