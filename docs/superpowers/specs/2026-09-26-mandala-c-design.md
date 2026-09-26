# Mandala Upgrade C — Design Spec (2026-09-26)

## Goal

- Make mandala richer and more varied.
- Keep pure functional core.
- Keep determinism: same seed = same art.
- Keep all 6 sacred invariants.

## Non-goals

- No new runtime deps.
- No change to `art.json` schema shape.
- No change to `[-1.0, 1.0]` bounds.

## Current faults

- Gates at `square-boundary-layer` sit under outer disc. They look cut off.
- Corners are empty navy void. Only 3-circle rosette.
- `replicate-d-n` only rotates. No mirror. Not true D_n.
- RNG only sets radii, symmetry, palette. See `derive-params`.
- Petal shape is fixed. All seeds look same.
- Flat fills. Heavy black strokes. Neon `#0BDA51` dominates.
- No inter-ring lace. No 64-tooth flame rim.
- SVG has no gradients or glow. See `art/export.clj art->svg`.

## Design: 6 slices

### 1. Fix structure

- File: `src/art/artforms/mandala.clj`
- Draw gates above discs with higher z-index. No notch cut.
- Cap outer ring radius at 0.82.
- Keep gate positions at square edge `s = 0.95`.
- Test: gates visible. No prim outside `[-1.0, 1.0]`.

### 2. True D_n mirror

- File: `replicate-d-n`.
- Add option `:mirror? true`.
- For each rotation, emit one mirrored copy.
- Mirror = negate angle, flip x.
- Keeps `D_8, D_12, D_16`.
- Test: mirrored prim count = 2x. Symmetry holds.

### 3. Inter-ring lace layer

- New layer `:lace-i` between each ring.
- Contents per sector:
  - pearl chain: small `:circle` dots on mid radius.
  - arc links: thin `:polygon` diamonds or `:arc` strokes.
  - density = multiple of symmetry order.
- Pure fn: `(lace-layer inner-r outer-r symmetry palette rng)`.
- Z-index between rings.
- Test: lace prims inside ring band. Count > 0.

### 4. Light and color

- Mute malachite: `#0BDA51` -> deep `#1E9E50` or use sparingly.
- Limit saturated area: bright colors only on small gems.
- Strokes: outer 0.0012, inner 0.002. Less black.
- SVG only: add radial gradients + soft glow filter in `art->svg`.
- Keep `art.json` fills flat. Gradients are render concern.
- Test: SVG contains `<radialGradient>`. JSON has no gradient keys.

### 5. Seed variety

- Extend `derive-params` with shape params:
  - `:petal-width-jitter` in `[0.85, 1.15]`.
  - `:petal-curl` in `[-0.15, 0.15]`.
  - `:motif-pick-per-ring` e.g. `[:petal :arc :dot]`.
  - `:jewel-density` in `[0.5, 1.0]`.
- All from same LCG RNG stream. Keep order fixed.
- Thread params into `single-petal-motifs` and `ring-sector-motifs`.
- Test: seed 42 vs 99 differ in shape, not just color. Same seed = equal.

### 6. Sacred details + viewer UI

- Torana: add 2nd lintel tier + side pillars. Keep T-shape.
- Corners: quarter-fan + vine dots. Keep inside square.
- Rim: 64-tooth flame triangles at `r = 0.84`.
- Bindu: add halo ring + 8-seed lotus seat.
- Viewer `art->html`:
  - Add palette select, symmetry select.
  - Add PNG download button.
  - Add 200ms fade on `render-ui!`.
- Test: rim count = 64. Bindu center still `[0.0 0.0]`.

## Data flow

- `generate {:seed :params}` -> `derive-params` -> layers -> prims.
- No new input keys required. All new params have defaults.
- Old seeds still render. Shapes shift slightly. This is accepted.

## Error handling

- Clamp radii ascending. If user passes bad radii, sort + clamp to `[0.05, 0.90]`.
- Clamp jitter to bounds. If prim escapes `[-1.0, 1.0]`, scale ring down.
- SVG: if gradient id missing, fall back to flat fill.

## Testing

- Run: `bb test`.
- Keep 10,787 assertions green. Update bounds tests for new layers.
- New tests:
  - mirror doubles count.
  - lace inside band.
  - seed shape variance.
  - rim = 64 teeth.
  - gates on top (z-index).
  - SVG has gradient defs.
- Run: `clj-kondo --lint src` and `cljfmt check`.

## Risks

- More prims -> slower Scittle. Mitigate: cap lace dots. Target < 2000 prims.
- Shape change breaks old snapshots. Mitigate: keep seed 42 as golden reference, update once.
- Glow filter heavy on mobile. Mitigate: SVG filter only, off by default in auto-evolve.

## Acceptance

- Gates fully visible.
- Corners filled, no void.
- Two seeds look clearly different in shape.
- No neon blowout.
- `bb test` passes. `bb export 42` works. Viewer loads offline.
