# AD-Gen-from-Dictionary — spec (the "just show it" + installer-prep step)

**Scope (one bounded UI session).** Generate the iDempiere **Application Dictionary** (AD_Table · AD_Column ·
AD_Window · AD_Tab · AD_Field · AD_Menu · AD_TreeNodeMM, + a minimal AD_Reference set) **from a foreign-ERP
adapter's table/column dictionary** (`scripts/sap_adapter.js` `SCHEMA_MAP` / `SCHEMA_MAP_FLIGHT`, and the same
for `odoo_adapter.js`), load it into an `ad_seed`-shaped SQLite, and let the **existing** renderer
(`idempiere.html` / `ad_parser.js`) draw the foreign ERP's tables as a navigable iDempiere UI **with zero
renderer changes**. This is the visible half of interoperability — the *dictionary* fold made tangible — and
the core of the **installer** (ERPMaker): fold → generate AD → emit a showable, self-contained app.

**Spec-first; witness-led; §-log first; deterministic / NON-INVENT** (every AD row derives from the adapter
dictionary — never a hand-authored screen). **EXPLICIT GO before any deploy.**

**Read first:** `docs/IDEMPIERE_RENDERER_SPEC.md` (the renderer this REUSES) · `prompts/ERP_AD_UI.md`
(`ad_parser.js`/`ad_data.js`, the AD parser to feed — do not duplicate) · `scripts/export_ad.sh` (the AD column
contract, copied below) · `scripts/sap_adapter.js` (the dictionary source + flight second-source) ·
`docs/ERPMaker.md` (the installer this prepares) · the witnessed folds `build/erp/{odoo,sap}_fold.log`.

---

## §0 Why — the seam this closes

The migration campaign proved a foreign ERP's **rows + behaviour** fold into the 5-table bridge through the six
verbs (`build/erp/odoo_fold.log`: 6 Odoo chains, `newVerbs=[]`; `sap_adapter.js`: SAP hypothesis + the
license-free `/DMO/` flight second-source). What the user has not yet *seen* is those tables **rendered**. The
unlock (user, 2026-06-03): the renderer is already **AD-driven** — it folds AD_Menu→AD_Window→AD_Tab→AD_Field
out of SQLite and draws screens generically. So we do not build SAP screens; we **generate the AD rows** from the
adapter's table/column dictionary and the existing renderer shows them. Crucially this needs **no transaction
oracle** — AD is *metadata*, and the dictionary (`SCHEMA_MAP`) is real, public, documented. Structure shows now;
transaction rows fill the grids when a real oracle arrives (the SD+FI sample still being hunted).

**Once it renders (the proof gate below), the same generator is the installer's core** — ERPMaker emits the
generated AD + the bridge data as a signed offline HTML app (`docs/ERPMaker.md`). The installer step is gated on
this proof: *prove the AD-gen renders, then wire the installer.*

## §1 Input — the adapter dictionary (extend it to carry column types)

Today `SCHEMA_MAP[t]` carries `{bridge, doc_type, note, key_fields:[name,…]}`. `key_fields` is **names only** —
not enough to pick an AD display type without guessing. **Non-invent fix: the adapter declares the type** (the
adapter IS the cross-ERP data dictionary). Extend each mapped table to:

```
'VBAK': { bridge:'documents', doc_type:'C_Order', columns:[
   { name:'VBELN', ref:'String',  len:10, key:true,  identifier:true,  label:'Sales Document' },
   { name:'KUNNR', ref:'TableDir', len:10, label:'Sold-To' },
   { name:'NETWR', ref:'Amount',  len:0,  label:'Net Value' },
   { name:'WAERK', ref:'String',  len:5,  label:'Currency' },
   { name:'VBTYP', ref:'List',    len:1,  label:'Doc Category' } ] }
```

`ref ∈ {String, Integer, Amount, Quantity, Date, YesNo, List, TableDir}` — the small AD reference set §3 seeds.
`key_fields` stays as a back-compat shorthand the generator expands to `columns` with `ref:'String'` defaults +
a `// TODO type` flag, so the generator runs before every type is hand-declared — but a declared `columns` block
is the non-invent target. **No type is invented silently**: an un-typed field renders as String and is COUNTED
in the witness (`untyped=N`) so the gap is visible, not hidden.

## §2 Output — the AD rows (match `export_ad.sh` columns EXACTLY; the renderer's contract)

Generate into an `ad_seed`-shaped SQLite (or `.sql`), the SAME tables/columns the renderer already folds:

- **AD_Table** `(AD_Table_ID, TableName, Name, Description, AD_Window_ID, IsActive)` — one per mapped source table.
- **AD_Column** `(AD_Column_ID, AD_Table_ID, ColumnName, Name, Description, AD_Reference_ID, AD_Val_Rule_ID,
  FieldLength, IsMandatory, IsKey, IsIdentifier, DefaultValue, ValueMin, ValueMax, IsActive)` — one per `columns[]`.
- **AD_Window** `(AD_Window_ID, Name, Description, Help, WindowType, IsActive)` — one per table (`WindowType='M'`).
- **AD_Tab** `(AD_Tab_ID, AD_Window_ID, Name, …, AD_Table_ID, TabLevel, SeqNo, IsSingleRow, IsReadOnly,
  WhereClause, OrderByClause, IsActive)` — one tab per window (`TabLevel=0, SeqNo=10`). (Header/line tables MAY
  nest as TabLevel 0/1 when the dictionary records a parent link — e.g. VBAP under VBAK, /DMO/BOOKING under TRAVEL.)
- **AD_Field** `(AD_Field_ID, AD_Tab_ID, AD_Column_ID, Name, …, SeqNo, IsDisplayed, DisplayLogic, IsMandatory,
  IsReadOnly, DefaultValue, IsActive)` — one per column, `SeqNo` in declared order, `IsDisplayed='Y'`.
- **AD_Menu** `(AD_Menu_ID, Name, Description, IsSummary, Action, AD_Window_ID, AD_Process_ID, AD_Form_ID,
  IsActive)` — one **summary** node per ERP/module (e.g. "SAP — SD", "SAP — FI", "SAP — Flight") + one **leaf**
  per window (`Action='W'`, `AD_Window_ID` set).
- **AD_TreeNodeMM** `(AD_Tree_ID, Node_ID, Parent_ID, SeqNo)` — the menu tree: module summaries under a root,
  window leaves under their module.
- **AD_Reference** + **AD_Ref_List** — the minimal type set §3 (so AD_Column.AD_Reference_ID resolves).

**ID policy (deterministic, §0.21):** IDs are minted from a fixed high offset per ERP (e.g. SAP base
`9_100_000`) + a per-table sequence, so re-running the generator is byte-stable and two ERPs never collide.
NO `Date.now`/`Math.random`. (Mirror `erp_kernel` edge-mint discipline.)

## §3 The generation rules (deterministic, one pass over the dictionary)

For each `SCHEMA_MAP[t]` with a `bridge` that is a real projection table (skip `(flow-graph)`):
1. mint `AD_Table` (TableName=`t`, Name=`note||t`).
2. for each `columns[i]`: mint `AD_Column` (ColumnName, Name=`label||name`, AD_Reference_ID=`REF[ref]`,
   FieldLength=`len`, IsKey/IsIdentifier/IsMandatory from flags).
3. mint `AD_Window` + one `AD_Tab` bound to the table; for each column mint an `AD_Field` (SeqNo=i*10+10).
4. mint an `AD_Menu` leaf (Action='W') → window; attach under the module summary node via `AD_TreeNodeMM`.
Seed `AD_Reference`+`AD_Ref_List` ONCE for `{String, Integer, Amount, Quantity, Date, YesNo, List, TableDir}`.

The whole pass is a **pure function `genAD(SCHEMA_MAP) → {ad_table[], ad_column[], …}`** — no DB writes inside;
a thin loader writes the rows (mirror the kernel's "handler returns ops, loader writes" separation).

## §4 Non-invent guardrails

- Every AD row traces to a dictionary entry. The witness asserts `handAuthored=0` and prints per-table counts.
- No screen is hand-built; if the renderer needs a field the dictionary lacks, that is a **named gap**, logged,
  not filled with a guess.
- Untyped columns render as String and are COUNTED (`untyped=N`) — a visible debt, never silent.
- Generated AD goes to a SEPARATE seed (e.g. `deploy/dev/sap_ad_seed.{sql,db}`), never overwriting the iDempiere
  `ad_seed.db`. Production `deploy/live/*` is untouched. The renderer loads whichever seed the page selects.

## §5 Witnesses — "prove it works" (the gate before the installer)

§-log first; these are the only evidence:
- `§AD-GEN erp=SAP tables=N columns=N windows=N tabs=N fields=N menu=N from-dictionary=Y handAuthored=0 untyped=K`
  — counts come from `genAD(SCHEMA_MAP)`, not hand data (the I1 burden of proof, mirrored).
- `§AD-GEN ids deterministic rerunA==rerunB=Y` — same dictionary → byte-identical AD (no Date.now/random).
- `§AD-RENDER idempiere.html loaded sap_ad_seed → menu nodes=N windows openable=N grid columns=N` — the EXISTING
  renderer draws the generated AD with zero renderer edits (whitebox §-log first; Playwright only for "scripts
  load / tree renders / a window opens", per the CLAUDE.md browser-testing rule).
- `§AD-RENDER round-trip SAP VBAK opens → fields shown == AD_Field count for that tab` — a real SAP table shows
  as a faithful window/grid. (Grid rows EMPTY until a transaction oracle exists — stated, not faked.)

## §6 The installer tie-in (what this prepares)

Once §5 passes, `genAD` + the loader are the heart of the ERPMaker installer (`docs/ERPMaker.md`): **fold a
foreign ERP → `genAD(adapter) → AD seed → emit a signed, offline, self-contained HTML app** carrying the
generated AD + (when present) the folded bridge data. The installer is **gated on the §5 proof** — do NOT build
the emit/sign step until the AD-gen demonstrably renders. This spec deliberately stops at "it renders"; the
offline-HTML/signing pipeline is its own later spec section in `ERPMaker.md`.

## §7 Scope boundaries (stated, not hidden)

- **Metadata only.** This shows STRUCTURE (tables/fields as navigable screens). Transaction GRID DATA needs a
  real oracle (SD+FI sample still being hunted; flight `/DMO/` export for the document half). Empty grids are
  honest, not a bug.
- **No reference/validation/process generation** (AD_Val_Rule, AD_Process, display logic) this session — fields
  render as plain editors. Named as the next increment.
- **One renderer (#1 iDempiere).** Odoo/ERPNext/Glassbowl slots reuse the SAME generated AD later (renderer-neutral).

## §8 Acceptance / Definition of Done

`genAD(SCHEMA_MAP)` is a pure, deterministic function; the loader produces a separate `sap_ad_seed`; `idempiere.html`
renders SAP's tables as menu→window→tab→grid with **`handAuthored=0`** and counts traced to the dictionary
(`§AD-GEN`/`§AD-RENDER` PASS); re-run is byte-stable; untyped-field debt is counted. Then — and only then —
the installer emit step is unblocked. Update `PROGRESS.md` + `docs/ERPMaker.md` with the witnessed state.

## §9 Session task breakdown

1. **T1 — dictionary types:** extend `sap_adapter.SCHEMA_MAP`/`SCHEMA_MAP_FLIGHT` (and `odoo_adapter`) entries
   from `key_fields` to typed `columns[]` (the back-compat expander handles un-migrated tables). Witness `untyped=K`.
2. **T2 — `genAD`:** pure `scripts/gen_ad.js` `genAD(SCHEMA_MAP) → AD row-sets` per §3, deterministic IDs.
3. **T3 — loader + seed:** write `deploy/dev/sap_ad_seed.{sql,db}` (separate from `ad_seed.db`). `§AD-GEN`.
4. **T4 — render proof:** point `idempiere.html` at the seed; whitebox §-log the fold counts; Playwright wiring
   check (tree renders, a window opens). `§AD-RENDER`. NO value-verification Playwright (use §-logs).
5. **T5 — docs:** PROGRESS + ERPMaker (installer unblocked once §5 green). Hand off the emit/sign step as next spec.
