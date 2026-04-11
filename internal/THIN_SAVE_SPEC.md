---
# Thin Save — Meshless .blend Toggle
**Version:** 1.1 | **Date:** 2026-04-11 | **Status:** SUPERSEDED by DLOD

## 1. Problem

Federation save handlers strip meshes unconditionally on every Ctrl+S.
Small projects lose meshes they want to keep. Large projects need thin saves
for scalability. The user must choose.

## 2. Solution

Scene-level boolean: `BIMFederationProperties.thin_save` — checkbox in the
federation N-panel. Default: OFF.

## 3. Behavior Matrix

| thin_save | Save | Open | Library click |
|-----------|------|------|---------------|
| OFF | Normal .blend with meshes | Normal — meshes visible immediately | Links from library.blend (additive) |
| ON | Strip meshes → stub .blend (~100KB) | R-tree bboxes only (stubs) | Re-links from library.blend |

## 4. Guard Logic

```python
@persistent
def federation_save_pre(dummy):
    props = bpy.context.scene.BIMFederationProperties
    if not getattr(props, 'thin_save', False):
        return   # normal save — keep meshes
    strip_template_meshes()

@persistent
def federation_load_post_meshes(dummy):
    # GN templates always restore (legacy path)
    # Library-linked: only restore if thin_save is OFF
    props = bpy.context.scene.BIMFederationProperties
    if not getattr(props, 'thin_save', False):
        _restore_library_linked()  # auto-restore on open
    # else: leave stubs — user clicks Library
```

## 5. Property Registration

```python
thin_save: bpy.props.BoolProperty(
    name="Thin Save (meshless)",
    description="Strip mesh geometry on save. Reopening shows R-tree only — "
                "click Library to reload full geometry. Recommended for 10K+ elements",
    default=False,
)
```

## 6. UI

Federation N-panel, below the R-Tree / Library / Clear buttons:

```
[R-Tree]  [Library]  [Clear]
☐ Thin Save (meshless)
```

When element count > 10,000, show hint text: "Recommended: enable Thin Save"

## 7. FINE Logging

```
[S174][SAVE] §THIN_SAVE=OFF — keeping meshes (normal save)
[S174][SAVE] §THIN_SAVE=ON — stripping N meshes ...
[S174][OPEN] §THIN_SAVE=OFF — auto-restoring library-linked meshes
[S174][OPEN] §THIN_SAVE=ON — stubs remain, user clicks Library
```

## 8. Constraints

- Flag persists in .blend (scene property) — each project remembers its choice
- No effect on non-federation .blend files (no BIMFederationProperties → no strip)
- Camera angle, collections, object positions, custom properties all survive strip
- Materials are re-created from extracted.db on Library click (not saved in .blend)
- Bonsai core save/open completely unaffected

## 9. SUPERSEDED: DLOD Solves This Architecturally

**Date:** 2026-04-11 | **Conclusion:** The thin_save checkbox is unnecessary.

With DLOD active (§DLOD_SPEC.md), most elements at any camera position are already
LOD-0 bbox proxies (8 verts, 12 faces). The `.blend` file is naturally small because
the viewport is naturally sparse. No user toggle needed.

The save/open cycle becomes:
- **Save:** writes current LOD state (near=full mesh, far=bbox proxy). Naturally thin.
- **Open:** restores exactly what was visible. DLOD handler resumes on first camera move.
- **No checkbox.** No mode switch. No extra click. File size scales with viewport, not building.

Bonsai's normal save/open is never disrupted because:
1. No federation collections → handlers exit immediately (no-op)
2. Federation loaded but no DLOD → all meshes save as-is (normal)
3. Federation + DLOD → current LOD state saved, DLOD resumes on open

The `thin_save` property and guards in `__init__.py` remain as dead code (default=OFF).
They can be removed or repurposed if needed. The DLOD handler is the correct solution.
