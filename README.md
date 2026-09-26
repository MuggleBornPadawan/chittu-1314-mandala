# Chitrapata Atelier — Procedural Mandala

> **View it live:** https://mugglebornpadawan.github.io/chittu-1314-mandala/

A mandala that never repeats itself. Each visit grows a new one from a single number — a seed. Note the seed down, and that exact mandala is yours to return to, forever.

---

## The Artwork

Every mandala opens at the **bindu** — a vermilion seed at the very centre, ringed in gold, haloed, seated on eight white lotus seeds. In Indian sacred geometry the bindu is the point before beginning. It is never empty here.

From the bindu, six concentric rings bloom outward:

- An eight-petal lotus heart in lapis and white.
- Bands of layered petals in vermilion, conch white, and deep malachite, each petal veined in gold and tipped with a jewel or pearl.
- Petals overlap like roof shingles, each laid over the last in a steady clockwise rhythm.
- Between the rings run fine chains of seed pearls and gold diamonds — quiet lace that holds the composition together.
- The outermost band closes in a 64-tooth flame rim, beaten gold against the dark.

The whole circle sits inside a square courtyard of abyssal navy, edged in gold and conch white. Four **torana gateways** — stepped temple portals — guard the cardinal directions, flanked by vermilion pillar stones. The four corners hold rosettes with fans of vine dots, so no corner of the square sits empty.

Complexity grows as the eye travels out: small and calm at the centre, dense and flaming at the edge. That gradient is deliberate. It is the grammar of the form.

## The Palette

Six canonical pigments, drawn from the Indian miniature and mural traditions:

- **Lapis Lazuli** `#26619C` — the blue of Krishna's skin, of deep water.
- **Vermilion** `#E34234` — sindoor red, auspicious and loud.
- **Malachite Green** `#1E9E50` — deep and quiet, used with restraint.
- **Conch White** `#FAF0E6` — warm ivory, the ground the brights sing against.
- **Gold Leaf** `#D4A017` — veins, rims, teeth, halos. The light in the work.
- **Cinnabar Red** `#E44D2E` — the outer fire band.

Each seed re-deals these colours across the rings, so the same geometry can feel like dawn in one seed and embers in another.

## Sitting With It

Open the [live viewer](https://mugglebornpadawan.github.io/chittu-1314-mandala/). It evolves on its own — a new mandala every few seconds.

- **⚡ Next Seed**: grow a new mandala right now.
- **⏸ Pause / ▶ Resume**: hold one still, or let the cycle run.
- **Seed box**: type any number to revisit an exact mandala.
- **Symmetry**: choose eight-, twelve-, or sixteen-fold geometry.
- **Palette**: deal the colours in five different orders.
- **Download PNG**: keep the one you love.

There is no reload, no waiting. Each mandala is computed in your browser, in the moment.

---

## For the Technically Inclined

Everything below this line is implementation. The art above stands without it.

### How It Works

- **Pure functional core.** Geometry is data, built with zero side effects and zero renderer dependencies (`src/art/artforms/mandala.clj`).
- **True dihedral symmetry.** Designs repeat in $D_8$, $D_{12}$, or $D_{16}$ — rotated *and* mirrored.
- **Seeded everything.** One integer seed drives symmetry, palette, ring radii, petal curl, petal width, jewel density, and petal overlap. Same seed yields the identical mandala on JVM Clojure, Babashka, and in-browser Scittle.
- **Time-seeding.** No seed given means the current timestamp is used — infinite fresh variation, each one reproducible.
- **Normalized space.** All geometry lives in $[-1.0, 1.0]$. Renderers only scale.
- **Deterministic PRNG.** A portable LCG (multiplier 1664525, mod $2^{32}$) kept inside 53-bit integers so all three runtimes agree bit-for-bit.

### Project Structure

```text
├── .github/workflows/pages.yml      # GitHub Pages deploy (public/)
├── bb.edn                         # Babashka tasks (test, export, serve)
├── deps.edn                       # Clojure JVM CLI dependencies
├── README.md                      # This file
├── atelier/chitrapata.org           # Master ontology (submodule → chitrapata-ontology)
├── scripts/
│   └── live_check.bb              # Live deployed-page smoke test (16 checks)
├── src/
│   └── art/
│       ├── artforms/
│       │   └── mandala.clj        # Pure mandala generator
│       ├── export.clj             # SVG, art.json, and Scittle HTML exporter
│       └── server.clj             # Zero-dependency Babashka HTTP server
├── test/
│   └── art/
│       └── artforms/
│           └── mandala_test.clj   # Unit tests (22,691 assertions)
└── public/
    ├── index.html                 # Interactive live Scittle browser viewer
    ├── mandala.svg                # Resolution-independent vector export
    ├── mandala.png                # Rendered raster preview
    ├── art.json                   # Canonical USD-aligned PCG data exchange format
    └── js/
        └── scittle.js             # Local offline in-browser Clojure interpreter
```

### Quickstart

Run the tests:

```bash
bb test
```

This verifies the PCG contract schema, determinism across seeds (`42`, `108`, `1314`, `2026`), coordinate bounds within $[-1.0, 1.0]$, and all 6 sacred visual invariants.

Export an artwork (timestamp seed, or a fixed one):

```bash
bb export
bb export 42
```

Outputs go to [`public/`](public/): `art.json`, `mandala.svg`, `index.html`.

Serve locally:

```bash
bb serve
```

Then open [http://localhost:8000](http://localhost:8000).

Test the deployed page (16 checks: status, viewer markers, JSON contract, SVG glow, PNG bytes):

```bash
bb scripts/live_check.bb
```

### REPL Usage (Emacs / CIDER / Babashka)

```clojure
(require '[art.artforms.mandala :as mandala])

;; Generate with explicit seed and default parameters
(def artwork (mandala/generate {:seed 42}))

;; Inspect primitives count and layers
(count (:primitives artwork)) ; => 1855
(mapv :id (:layers artwork))  ; => [:square-boundary :annular-backgrounds :ring-0 ... :lace-0 ... :flame-rim :torana-gates :bindu]

;; Override parameters interactively
(def custom-mandala
  (mandala/generate
    {:seed 108
     :params {:symmetry-order 16
              :petal-overlap 1.4
              :palette [:gold-leaf :lapis-lazuli :vermilion :conch-white]}}))
```

---

## License

GNU General Public License v3.0 with the GNU Classpath / EPL Section 7 Linking Exception.
See [`atelier/chitrapata.org`](atelier/chitrapata.org) for atelier ontology and licensing topology.
