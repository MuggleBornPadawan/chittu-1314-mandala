# Chitrapata Atelier — Procedural Mandala Generator

> **Live Demo:** https://mugglebornpadawan.github.io/chittu-1314-mandala/

Procedural Indian sacred geometry generator for **Chittu 13.14**.
Written in pure functional **Clojure** and executable with **Babashka** (`bb`) and **Scittle**.

---

## Features

- **Pure Functional Core**: Decoupled geometry generation with zero side effects and zero renderer dependencies.
- **Strict Dihedral Symmetry**: Generates $D_8, D_{12}$, or $D_{16}$ sacred quadrant geometry.
- **Normalized Coordinates**: All vertices and paths map to $[-1.0, 1.0]$ space.
- **Cross-Platform Determinism**: Pure LCG pseudo-random generator produces identical outputs on JVM Clojure, Babashka, and in-browser Scittle.
- **Dynamic Time-Seeding**: Uses timestamp seeds (`System/currentTimeMillis` or `js/Date.now`) for infinite dynamic variations with full reproducibility.
- **Live In-Browser Updates**: Interactive Scittle viewer runs directly in browser memory without full page reloads.
- **Zero-Dependency Local Server**: Built-in Babashka HTTP server with zero Maven or npm downloads.

---

## Visual Invariants (Sacred Grammar)

1. **Central Bindu**: Concentric seed circle at origin `[0.0, 0.0]`. Never empty.
2. **Dense Concentric Rings**: 6 annular bands filled with lotus petals, golden spines, jewels, and seed pearls.
3. **Square Outer Boundary**: Outermost frame aligned to `[-0.95, 0.95]` in Abyssal Navy (`#061735`) with golden and white perimeter borders.
4. **Four Torana Gateways**: Stepped T-shaped portals guarding cardinal axes (North, South, East, West).
5. **Outward Radiation**: Complexity increases outward from inner 8-petal core to 64-tooth flame rim.
6. **Canonical Pigments**:
   - **Lapis Lazuli**: `#26619C`
   - **Vermilion**: `#E34234`
   - **Malachite Green**: `#0BDA51`
   - **Conch White**: `#FAF0E6`
   - **Gold Leaf**: `#D4A017`
   - **Cinnabar Red**: `#E44D2E`

---

## Project Structure

```text
├── .github/workflows/pages.yml      # GitHub Pages deploy (public/)
├── bb.edn                         # Babashka task definitions (test, export, serve)
├── deps.edn                       # Clojure JVM CLI dependencies and aliases
├── README.md                      # Project documentation and guide
├── atelier/chitrapata.org           # Master ontology (submodule → chitrapata-ontology)
├── src/
│   └── art/
│       ├── artforms/
│       │   └── mandala.clj        # Pure mandala PCG generator
│       ├── export.clj             # Vector SVG, art.json, and Scittle HTML exporter
│       └── server.clj             # Zero-dependency Babashka HTTP server
├── test/
│   └── art/
│       └── artforms/
│           └── mandala_test.clj   # Unit tests (10,787 assertions)
└── public/
    ├── index.html                 # Interactive live Scittle browser viewer
    ├── mandala.svg                # Resolution-independent vector export
    ├── mandala.png                # Rendered raster preview
    ├── art.json                   # Canonical USD-aligned PCG data exchange format
    └── js/
        └── scittle.js             # Local offline in-browser Clojure interpreter
```

---

## Quickstart

### 1. Run Automated Unit Tests

```bash
bb test
```

Verifies:
- PCG contract schema (`:seed`, `:artform`, `:params`, `:palette`, `:layers`, `:primitives`).
- Mathematical determinism across seeds (`42`, `108`, `1314`, `2026`).
- Coordinate boundaries strictly within $[-1.0, 1.0]$.
- Enforcement of all 6 sacred visual invariants.

### 2. Export Artwork

Export a dynamic artwork seeded by the current system timestamp:

```bash
bb export
```

Export a reproducible artwork for a specific seed:

```bash
bb export 42
```

Outputs written to [`public/`](public/):
- [`public/art.json`](public/art.json) (USD-aligned scene data)
- [`public/mandala.svg`](public/mandala.svg) (High-precision vector markup)
- [`public/index.html`](public/index.html) (Live browser viewer)

### 3. Launch Live Browser Viewer

```bash
bb serve
```

Open [http://localhost:8000](http://localhost:8000) in your browser:
- **⚡ Next Seed (Time)**: Evaluates a new timestamp seed instantly in browser memory.
- **⏸ Pause / ▶ Resume Auto-Evolve**: Cycles through new mandalas every 2.5 seconds without page reload.
- **Custom Seed Input**: Enter any seed number to inspect or reproduce that exact artwork.

---

## REPL Usage (Emacs / CIDER / Babashka)

```clojure
(require '[art.artforms.mandala :as mandala])

;; Generate with explicit seed and default parameters
(def artwork (mandala/generate {:seed 42}))

;; Inspect primitives count and layers
(count (:primitives artwork)) ; => 834
(mapv :id (:layers artwork))  ; => [:square-boundary :annular-backgrounds :ring-0 ... :bindu]

;; Override parameters interactively
(def custom-mandala
  (mandala/generate
    {:seed 108
     :params {:symmetry-order 16
              :palette [:gold-leaf :lapis-lazuli :vermilion :conch-white]}}))
```

---

## License

GNU General Public License v3.0 with the GNU Classpath / EPL Section 7 Linking Exception.
See [`atelier/chitrapata.org`](atelier/chitrapata.org) for atelier ontology and licensing topology.
