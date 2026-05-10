---
name: 2D pick identity — separate responsibility
description: Clicking 2D card items (contours, arcs, labels) must show real IFC names. Code exists but unproven by runtime logs. User wants explicit identity layer.
type: project
---

2D pick identity is UNPROVEN. picking.js reads userData.guid from contour meshes but no § log proves the runtime chain: click → hit → guid → DB → IFC name displayed.

**Why:** User expects clicking a door arc shows "Doors_IntSgl:810x2110mm", clicking a seat shows "Chair - Dining". Current state: code path exists but only whitebox string-matching tests verify it. No runtime evidence.

**How to apply:**
- Need a SEPARATE RESPONSIBILITY pass that assigns IFC identity to every 2D drawn item
- Furniture is NOT a contour (it's a 3D mesh in retainSet) — pick path for furniture is different from contour pick
- Must add §PICK_2D runtime logs that fire on actual click and show resolved IFC name
- Only § logs prove truth. Source code is suspect.
- kernel_ops could persist card state for instant reload
