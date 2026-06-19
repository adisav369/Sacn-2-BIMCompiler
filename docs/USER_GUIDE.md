# BIM OOTB — User Guide

One browser. **View** a building, **author** geometry, **run** the ERP — all client-side, zero install.

![The Matrix landing — choose your door](assets/matrix_landing.png)

**First visit** drops you at the Matrix front door — the red / blue choice, then the round selector. Pick a
door, or jump straight to any surface by its URL below.

## The surfaces

| Surface | What it is | Open it |
|---|---|---|
| **Matrix landing** | the front door — *choose your door* | [red1oon.github.io/bim-ootb](https://red1oon.github.io/bim-ootb/) |
| **BIM Viewer** | the **Buildings / IFC** door — drop your own IFC or open a ready-made City / Landmark building → opens the 3D/2D viewer | [front door](https://red1oon.github.io/bim-ootb/) → **Buildings / IFC** · [guide](BIMUserGuide.md) |
| **DAGeVu Modeller** | author B-rep geometry over a signed op-log | [modeller.html](https://red1oon.github.io/bim-ootb/viewer/modeller.html) · [guide](ModellerGuide.md) |
| **Kernel-ERP** | the ERP **bubbles** — click/explode or long-press a bubble → iDempiere; **⋯** → Glass / Gravity | [erp.html](https://red1oon.github.io/bim-ootb/erp/erp.html) · [guide](ERPUserGuide.md) |

**Bookmark any of these.** Going straight to `idempiere.html`, the Modeller, or the Viewer — bypassing the
landing — is perfectly fine; that's what they're for. To come back to the front door, press **Home** on any
surface (or open the root URL). On a return visit the landing shows the compact `⋯` launcher; **refresh** to
summon the full round selector again.

---

## BIM Viewer

Open a building, navigate in 3D/2D, run clash detection, track 4D progress. Desktop and mobile.

→ **[Viewer Guide](BIMUserGuide.md)**

---

## DAGeVu Modeller

Author geometry in the browser — insert library components, sketch, extrude, sweep — where the signed
operation log *is* the feature tree. Early/work-in-progress; desktop.

→ **[Modeller Guide](ModellerGuide.md)**

---

## Kernel-ERP

Login, install the demo tenant, run the POS, view financial statements. The browser kernel renders the full
iDempiere Application Dictionary from SQLite — no Java, no server, no install.

→ **[ERP User Guide](ERPUserGuide.md)**

**ERP practitioners** — implementers, power users, developers, and other ERP projects — start with
**[Migrate & Compare](MigrateComparisonPaper.md)**: how Kernel-ERP folds the iDempiere (and a live Odoo)
tenant onto one signed op-log, what stands comparison with a legacy stack, and the honest gaps.

---

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
