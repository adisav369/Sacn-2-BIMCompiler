# The Workforce Lens — attendance & task management, the sacred transaction applied to people-work

*The people side of the one model: **clock-in** (attendance) and **task assignment/management**. Same doctrine as
the goods lenses — presence is a party-act, the worklist is a fold, the supervisor orchestrates and the worker acts —
and **no reinvention**, because the horizontal ([LensFamily](LensFamily.md)) already carries the employee and the
task. Sibling to [POSLens](POSLens.md), [WMSLens](WMSLens.md), [SocialPlatformLens](SocialPlatformLens.md),
[CreditLedgerLens](CreditLedgerLens.md).*

---

## 1. Clock-in — a non-falsifiable presence act (rolling QR)

The supervisor lens (a desktop or wall screen) displays a **rolling, single-use QR** — a "ticket." A worker scans it
with their phone; the scan produces a **signed clock-in op** bound to that live challenge. It is the sacred
transaction applied to *time*, and it kills buddy-punching the way the shelf kills double-sell:

- **Single-use + rotating ("ticket taken up").** Each QR is consumed on scan and the screen shows a fresh one. A
  screenshot or an old code is already spent/expired → **replay defeated.** And two workers can't claim the same code
  — *one ticket, one taker* (the one-item-one-scan rule, applied to attendance).
- **Signed by the worker's own device key → identity-bound.** A buddy can't punch you in without *your* phone/key.
- **Physical presence.** You must be at the supervisor's live screen to scan the current code — physics serializes,
  exactly as the bin scan (WMS) and the delivery GPS do.

The result is **multi-factor sacred attendance with no biometric hardware** — a screen with a rolling QR plus the
workers' phones. `§HR-CLOCKIN worker=… ticket=<nonce> single-use=Y signed=Y present=Y → in|out`.

### 1a. Honest anti-fraud boundary (the layering)
Rolling + single-use defeats **replay** (screenshot, reused code). It does *not* alone defeat a **live relay** (a
friend video-calls the current QR to an absent worker). Close that with the third serializer — bind the clock-in to
**presence**: device GPS at the worksite, or premises wifi/BLE. So the robust stack is **rolling QR (anti-replay) +
device key (identity) + presence (anti-relay)**. State the layering; the QR alone is strong, not total.

## 2. Attendance is a fold — the timesheet computes itself

Hours worked = `Σ` of clock-in→clock-out intervals — **never a keyed timesheet.** Time enters only as a scan (a
party-act), so there are *no free numbers*: nobody types "8 hours." A missed clock-out is a *named exception*, not a
silent zero. The timesheet, like perpetual inventory and the AR balance, is **always known, derived, zero-effort** —
no manual time entry, and any discrepancy (a long gap, an odd pattern) surfaces as a fold exception.

## 3. Task assignment & management — the worklist fold, for people

Task management is the same primitive as WMS picking, only the work is people-work:

- **Tasks are items** in a fold; **assignment is a *claim*** — a worker takes a task (a signed "taken up", serialized
  one-taker-per-task); **due** is an attribute (like a payment term); **completion** is a signed act.
- **Supervisor lens (big screen — orchestrate):** the kanban board — *who has taken what, due when, status* — plus the
  rolling-QR dispenser. Sees the whole fold.
- **Worker lens (phone — act):** "my tasks" — claim, do, confirm (scan/tap). Directed work, glanceable, literacy-light.

Nobody keys "task done by X at Y"; the claim and the completion are the signed acts the board folds. `§HR-TASK
task=… claimed-by=… due=… status=open|claimed|done`.

## 4. Two lenses — orchestrate and act

Phone = act, big screen = orchestrate, again: the **supervisor** holds the board and the QR dispenser (the
orchestration view); the **worker** holds the phone (the action surface — clock in, claim a task, complete it). Same
fold, role-shaped lenses, no central control — the board is a fold over the workers' signed acts, computable by any
node.

## 5. No reinvention — rides the horizontal

EXTRACT, not invent: the employee is `AD_User` / `C_BPartner`; the task/assignment is `R_Request` (iDempiere's
request model); attendance and task events are signed ops over the same log. The horizontal already models people and
their work — the Workforce lens *surfaces* it, adding only the rolling-QR presence guard, which is itself just the
sacred-transaction discipline pointed at time.

## 6. Honest scope

- **Build (lens + a guard):** the rolling/single-use QR dispenser (supervisor) + the signed presence clock-in + the
  task-claim/complete worklist + the supervisor board. The QR-rotation/nonce + presence binding is the one new bit of
  glue; everything else is folds over `AD_User`/`R_Request`.
- **Ride:** native scan (camera), the device key (signing), GPS/wifi for presence — all guaranteed device features.
- **Discipline:** a lens over the proven horizontal, on the staircase like the others; build when a real operation
  needs attendance/tasks, not before. Same fold, no fork.

The promise: **attendance and task management with no timesheet to key, no biometric hardware, and no central
clock-server — presence is a signed scan of a code that can't be replayed, work is a worklist the worker claims, and
the supervisor's board is just a fold over both.**

---

*Companions:* [LensFamily](LensFamily.md) (hub) · [POSLens](POSLens.md) · [WMSLens](WMSLens.md) ·
[SocialPlatformLens](SocialPlatformLens.md) · [CreditLedgerLens](CreditLedgerLens.md) ·
[GuaranteedChannels](GuaranteedChannels.md) · [DistributedERP](DistributedERP.md).
