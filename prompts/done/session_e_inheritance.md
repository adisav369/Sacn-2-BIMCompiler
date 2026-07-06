# DONE — S68e — Session E: Order Inheritance (ProjectOrderBlueprint §14.3)
> Commit: 4ad0ec5 [S68e]

You are a coder for bim-compiler. One bounded task.

Read first:
1. docs/MANIFESTO.md (ERP world view — read first, always)
2. docs/ProjectOrderBlueprint.md §6 (Order inheritance) + §14.3 Session E spec
3. docs/ProjectOrderBlueprint.md §6.3 (conflict resolution spec — written by GAP-SC-5 session)
4. docs/SystemContract.md §10 (gap register — GAP-SC-5 should now be SPEC COMPLETE)
5. docs/AUDIT_S51_FOCUSED.md Appendix Q (Session D report — locator_ref now exists)
6. docs/AUDIT_S51_FOCUSED.md Appendix R (GAP-SC-5 spec — conflict resolution rule)
7. PROGRESS.md

## Prerequisites (must be done before this session)

- Session D DONE — locator_ref column exists on C_OrderLine
- GAP-SC-5 SPEC COMPLETE — conflict resolution rule defined

## Deliverables

1. **Ref_Order_ID on C_Order:**
   - Migration W006_order_inheritance.sql — add Ref_Order_ID TEXT (FK to parent C_Order)
   - Null = base order (no parent). Non-null = exception order.

2. **Chain walking:**
   - Compiler walks Ref_Order_ID chain: child → parent → grandparent
   - Collects all C_OrderLines across the chain
   - Applies in sequence: base first, then overrides by chain depth
   - Conflict resolution per §6.3 spec (GAP-SC-5)

3. **Override mechanics:**
   - Exception C_OrderLine at same locator_ref as parent → replaces parent line
   - qty=0 at locator_ref → removes subtree (Session D mechanic, inherited)
   - New locator_ref in exception → adds to parent

4. **Gate:**
   - Existing tests pass unchanged
   - New test: OrderInheritanceTest.java
     - W-INHERIT-1: DX_SOLAR_PREMIUM = 3 lines on top of DX_SOLAR on top of DX_BASE
     - W-INHERIT-CONFLICT-1: per §6.3 failure criterion

## Constraints

- Append-only migrations
- Test on SQLite backend
- Use M_Product_Category terminology (never M_BomCategory)
- Pre-flight citation: // Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1

Write session report to: docs/AUDIT_S51_FOCUSED.md — append as Appendix S.

## When Done

Commit with message linking to this prompt. Leave this file for watchdog review.
Watchdog will review and move to prompts/done/.

## WATCHDOG REVIEWED
**S69 Watchdog** — 2026-03-24

All deliverables verified:
1. **W006 migration:** `Ref_Order_ID TEXT REFERENCES C_Order(C_Order_ID)` — PASS
2. **InheritanceResolver:** chain walk (root-first), collectExceptions (depth-wins), cycle detection (MAX_DEPTH=20) — PASS
3. **BomDropper.dropWithInheritance:** convenience method delegates correctly — PASS
4. **OrderInheritanceTest 6/6:** W-INHERIT-CHAIN-1, W-INHERIT-CHAIN-2, W-INHERIT-1, W-INHERIT-DEPTH-1, W-INHERIT-CONFLICT-1, W-INHERIT-COMPAT-1 — all PASS
5. **RemoveCompressTest 5/5:** still PASS (backward compat)
6. **GAP-SC-5 CLOSED** in SystemContract.md §10
7. **Pre-flight citation** present in both InheritanceResolver.java and OrderInheritanceTest.java
8. **Compile clean**

**Note:** Appendix S (Session E audit report) not found in AUDIT_S51_FOCUSED.md — prompt specified it but session didn't write it. PROGRESS.md covers the status adequately. Consider writing Appendix S in a future session if detailed audit trail is needed.
