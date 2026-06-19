# BIM OOTB — User Guide

One browser. **View** a building, **author** geometry, **run** the ERP — all client-side, zero install.

![The Matrix landing — choose your door](assets/matrix_landing.png)

**First visit** drops you at the Matrix front door — the red / blue choice, then the round selector. Pick a
door, or jump straight to any surface by its URL below.

## The surfaces

| Surface | What it is | Open it |
|---|---|---|
| **Matrix landing** | the front door — *choose your door* | [red1oon.github.io/bim-ootb](https://red1oon.github.io/bim-ootb/) |
| **BIM Viewer** | open a building — 3D/2D, clash, 4D/5D, Find | [viewer.html](https://red1oon.github.io/bim-ootb/viewer/viewer.html) · [guide](BIMUserGuide.md) |
| **DAGeVu Modeller** | author B-rep geometry over a signed op-log | [modeller.html](https://red1oon.github.io/bim-ootb/viewer/modeller.html) · [guide](ModellerGuide.md) |
| **Kernel-ERP** | iDempiere-faithful ERP in the browser | [idempiere.html](https://red1oon.github.io/bim-ootb/erp/idempiere.html) · [guide](ERPUserGuide.md) |
| **Buildings gallery** | the building catalog + drop-your-own-IFC | [gallery.html](https://red1oon.github.io/bim-ootb/gallery.html) |

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

---

## Further reading

| Doc | What |
|-----|------|
| [BIM Back Office](BackOfficeUserGuide.md) | Server-side portfolio + reports (Java pipeline) |
| [Migrate & Compare (ERP)](MigrateComparisonPaper.md) | Architecture paper — six pillars, POS, warehouse |
| [ERP.md](ERP.md) | AD-in-browser blueprint |
| [BIM Designer (browser)](BIM_Designer_Browser.md) | Viewer streaming layer reference |
| [2D Plans](BIM_2D_Guide.md) | 2D floor-plan viewer |

---

## BOM Compiler (Java pipeline)

The YAML→3D compiler DSL, build commands, room types, and output formats are in the
[BIM User Guide → BOM Compiler DSL Reference](BIMUserGuide.md#bom-compiler-dsl-reference).

---

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
