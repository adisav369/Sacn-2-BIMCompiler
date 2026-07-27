# ⚠ DO NOT REMOVE
**Scope:** reusable OUTWARD-FACING technical descriptions of what this FOSS project is and what a
user gets from it. Written for posts, README intros, conference blurbs and demo captions.
**Every claim here must be traceable to a measured number or a named file** — no marketing adjectives
that a technical reader can test and disprove. When a number changes, update it here in the same
session. §NOT-CLAIMS at the bottom is as important as the rest: it is what keeps the whole thing
credible with the audience that actually matters.

---

# §STATEMENT_TERMINAL_FILM (2026-07-28) — compact version, written for sharing

## The one-liner
**A 49-second architectural film, rendered entirely in a web browser, straight from an IFC-derived
database — no game engine, no cloud render farm, no proprietary scene format.** The building in the
film is the same file a quantity surveyor would query.

## What is actually in the frame
| | measured |
|---|---|
| Model | 48,433 elements · 9,395 geometries · one self-contained 280 MB SQLite file |
| Draw calls | **155** for the whole building (BatchedMesh + InstancedMesh consolidation) |
| Output | H.264 1852×960, 15 fps, 731 frames, 48.7 s — baked frame-by-frame in-browser, not screen-captured |
| Hardware | one laptop GPU (RTX 4060), no server-side rendering |
| Lighting | 814 luminaires as **one** additive point cloud = 1 draw call |
| Materials | 37 live triplanar PBR materials (IFC carries **no UVs** — triplanar removes the blocker) |
| Per frame | 16-sample TAA accumulation + 24-frame ambient occlusion + real photographed HDRI |

## What you actually get from it
- **Open a real IFC-derived model in a browser and fly it.** No install, no licence, no upload to
  someone's cloud. The database is the deliverable and it is portable.
- **Cinematic output without authoring.** The camera path is *computed* from the building's own
  ARC bounding box and sun angle — the same code produces a different film for a different building
  with zero keyframing.
- **The model's meaning drives the picture.** Which facade is lit warm follows the sun; which floor
  could be reflective is read from the model's own finish names (`Procelain` yes, `nonslip` never);
  which fittings glow is resolved from IFC class and family naming. **A general 3D engine cannot do
  this — it does not know what a duct is.**
- **Additions are real BIM data, not compositing.** The billboard in this film is a physical panel
  injected as four database rows plus a geometry blob, mounted on a wall chosen by clicking it; its
  four floodlights are genuine `IfcLightFixture` elements, adopted by the building's existing
  night-lighting system with no new rendering code. It is pickable, quantifiable and casts shadows.
- **Everything is CC0 or ours.** Textures, HDRI and staffage are public-domain with a `NOTICE.txt`;
  the vehicle mesh is re-used from the project's own IFC entourage rather than a stock library.

## §NOT-CLAIMS — do not say these, a technical audience will test them
- **Not "first with real reflections/realism".** Enscape, Twinmotion and Lumion have had these for
  years. The defensible claim is **"in a browser, straight from IFC, on one laptop"**.
- **Not "photorealistic".** This is high-quality real-time archviz. It is not path-traced.
- **Not "free rendering".** The selection is free — a semantic query against the loaded SQLite runs
  in milliseconds. The pixels are still the GPU's, and every effect is budgeted and measured.

## Provenance of the numbers above
All from `§`-tagged logs and file metadata on 2026-07-28, not estimates: `§CONTRACT_CHECK` /
`§BATCHED_FLUSH` (element and draw-call counts), `ffprobe` (container), `§PHOTO_GLOW_SPRITE`
(luminaire count), `§STILL_REFINE` + `§PHOTO_AO` (accumulation), `§TRIPLANAR_INIT` (material count).
Full engineering detail: `PHOTOREAL_STILL_RENDER.md` §11–§14 and its `▶▶ NEXT SESSION` handover.
