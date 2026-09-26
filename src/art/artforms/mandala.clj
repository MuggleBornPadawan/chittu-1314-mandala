(ns art.artforms.mandala
  "Pure Mandala procedural art generator.
   Adheres to Chitrapata PCG contract and D_n dihedral radial symmetry specifications.
   Zero renderer dependencies. Pure data-first geometry in [-1.0, 1.0] local space.
   100% portable across Clojure JVM, Babashka, and Scittle (browser Clojure).")

;; =============================================================================
;; Canonical Pigment Map (Mandala Tradition)
;; =============================================================================

(def pigment-hex-map
  {:lapis-lazuli    "#26619C"
   :vermilion       "#E34234"
   :malachite       "#1E9E50"
   :conch-white     "#FAF0E6"
   :gold-leaf       "#D4A017"
   :cinnabar        "#E44D2E"
   :lampblack       "#1A1A1A"
   :orpiment-yellow "#E6A817"})

(def canonical-palettes
  [[:lapis-lazuli :vermilion :malachite :conch-white :gold-leaf :cinnabar]
   [:gold-leaf :lapis-lazuli :cinnabar :conch-white :malachite :vermilion]
   [:cinnabar :gold-leaf :lapis-lazuli :malachite :conch-white :vermilion]
   [:malachite :gold-leaf :vermilion :lapis-lazuli :conch-white :cinnabar]
   [:lapis-lazuli :gold-leaf :vermilion :malachite :conch-white :orpiment-yellow]])

;; =============================================================================
;; Portable Deterministic PRNG
;; =============================================================================

(defn make-rng
  "Pure functional pseudo-random generator.
   Numerical Recipes LCG (multiplier 1664525, increment 1013904223, mod 2^32).
   Operates strictly within 53-bit integers for 100% identical results on JVM, Babashka, and Scittle/JS."
  [seed]
  (let [s (atom (long (mod (Math/abs (long seed)) 4294967296)))]
    (fn []
      (let [next-s (mod (+ (* @s 1664525) 1013904223) 4294967296)]
        (reset! s next-s)
        (/ (double next-s) 4294967296.0)))))

;; =============================================================================
;; Mathematical Helpers
;; =============================================================================

(defn polar->cart
  "Convert polar coordinates (r, theta) to 2D Cartesian [x y]."
  [r theta]
  [(* r (Math/cos theta))
   (* r (Math/sin theta))])

(defn rotate-point
  "Rotate 2D point [x y] around origin by theta radians."
  [[x y] theta]
  (let [c (Math/cos theta)
        s (Math/sin theta)]
    [(- (* x c) (* y s))
     (+ (* x s) (* y c))]))

(defn rotate-primitive
  "Rotate primitive geometry by theta radians around [0.0 0.0]."
  [prim theta]
  (case (:type prim)
    :circle
    (update prim :center rotate-point theta)

    :arc
    (-> prim
        (update :center rotate-point theta)
        (update :start-angle + theta)
        (update :end-angle + theta))

    :polygon
    (update prim :points (fn [pts] (mapv #(rotate-point % theta) pts)))

    :line
    (-> prim
        (update :from rotate-point theta)
        (update :to rotate-point theta))

    :rect
    (update prim :center rotate-point theta)

    prim))

(defn mirror-primitive
  "Mirror primitive across y-axis (negate x)."
  [prim]
  (case (:type prim)
    :circle
    (update prim :center (fn [[x y]] [(- x) y]))

    :arc
    (-> prim
        (update :center (fn [[x y]] [(- x) y]))
        (update :start-angle -)
        (update :end-angle -))

    :polygon
    (update prim :points (fn [pts] (mapv (fn [[x y]] [(- x) y]) pts)))

    :line
    (-> prim
        (update :from (fn [[x y]] [(- x) y]))
        (update :to (fn [[x y]] [(- x) y])))

    :rect
    (update prim :center (fn [[x y]] [(- x) y]))

    prim))

(defn replicate-d-n
  "Replicate primitives across n angular sectors (dihedral rotational group).
   Pass {:mirror? true} for full dihedral mirror copy per sector."
  ([prims n] (replicate-d-n prims n nil))
  ([prims n {:keys [mirror?]}]
   (let [step (/ (* 2.0 Math/PI) (double n))]
     (mapcat (fn [i]
               (let [rot (* i step)
                     base (mapv #(rotate-primitive % rot) prims)]
                 (if mirror?
                   (concat base (mapv mirror-primitive base))
                   base)))
             (range n)))))

;; =============================================================================
;; Dynamic Parameter Derivation
;; =============================================================================

(defn derive-params
  "Deterministically derive dynamic parameters from seed while honoring visual invariants."
  [seed user-params]
  (let [rng         (make-rng seed)
        next-int    (fn [bound] (int (Math/floor (* (rng) bound))))
        next-double (fn [min-v max-v] (+ min-v (* (rng) (- max-v min-v))))

        ;; Symmetry order in {8, 12, 16} (mandala quadrant multiple of 4)
        sym-options [8 8 12 12 16]
        sym         (get user-params :symmetry-order (nth sym-options (next-int (count sym-options))))

        ;; Palette selection
        pal         (get user-params :palette (nth canonical-palettes (next-int (count canonical-palettes))))

        ;; Dynamic ring radii (strictly ascending, outer ring in [0.78, 0.82])
        r0 (next-double 0.11 0.13)
        r1 (next-double 0.23 0.26)
        r2 (next-double 0.37 0.40)
        r3 (next-double 0.52 0.56)
        r4 (next-double 0.68 0.72)
        r5 (next-double 0.78 0.82)
        radii (get user-params :ring-radii [r0 r1 r2 r3 r4 r5])

        ;; Petal density per ring (multiples of symmetry order)
        petals (get user-params :petal-count-per-ring [sym (* 2 sym) (* 2 sym) (* 4 sym) (* 4 sym) (* 8 sym)])

        ;; Seed-driven shape variety (fixed RNG order: width, curl, jewel)
        width-jitter (get user-params :petal-width-jitter (next-double 0.85 1.15))
        curl (get user-params :petal-curl (next-double -0.15 0.15))
        jewel-density (get user-params :jewel-density (next-double 0.5 1.0))]

    (merge
     {:symmetry-order       sym
      :ring-count           6
      :ring-radii           radii
      :bindu-radius         0.04
      :square-size          1.90
      :torana-width         0.22
      :torana-depth         0.10
      :motif-depths         [0 1 2 2 3 3]
      :petal-count-per-ring petals
      :petal-width-jitter   width-jitter
      :petal-curl           curl
      :jewel-density        jewel-density
      :palette              pal}
     user-params)))

;; =============================================================================
;; Lotus Petal & Motif Generators
;; =============================================================================

(defn- petal-contour
  "Generate a smooth curved lotus petal contour polygon."
  [r-in r-out mid-angle half-width fill stroke-color stroke-width curl]
  (let [n-pts 6
        pts-l (for [i (range n-pts)]
                (let [t (/ (double i) (dec n-pts))
                      r (+ r-in (* (- r-out r-in) t))
                      mid (+ mid-angle (* (or curl 0.0) t))
                      w-factor (* (Math/sin (* (- 1.0 t) (/ Math/PI 2.0)))
                                  (+ 1.0 (* 0.32 (Math/sin (* Math/PI t)))))
                      a (- mid (* half-width w-factor))]
                  (polar->cart r a)))
        pts-r (for [i (range (- n-pts 2) -1 -1)]
                (let [t (/ (double i) (dec n-pts))
                      r (+ r-in (* (- r-out r-in) t))
                      mid (+ mid-angle (* (or curl 0.0) t))
                      w-factor (* (Math/sin (* (- 1.0 t) (/ Math/PI 2.0)))
                                  (+ 1.0 (* 0.32 (Math/sin (* Math/PI t)))))
                      a (+ mid (* half-width w-factor))]
                  (polar->cart r a)))]
    {:type :polygon
     :points (vec (concat pts-l pts-r))
     :fill fill
     :stroke stroke-color
     :stroke-width stroke-width}))

(defn- single-petal-motifs
  "Generate a single petal structure (nested cusps, spine, pearl) at `mid-angle`."
  [inner-r outer-r mid-angle half-width depth col-primary col-secondary col-accent col-gold col-white col-black _width-jitter curl jewel-density]
  (let [jd (or jewel-density 1.0)]
    (case depth
      ;; Depth 0: Core lotus petal with central gold vein and base pearl
      0
      [(petal-contour inner-r outer-r mid-angle (* 0.46 half-width) col-gold col-black 0.002 curl)
       {:type :line
        :from (polar->cart inner-r mid-angle)
        :to (polar->cart (* 0.96 outer-r) mid-angle)
        :stroke col-secondary
        :stroke-width 0.002}
       {:type :circle
        :center (polar->cart (+ inner-r (* 0.40 (- outer-r inner-r))) mid-angle)
        :radius (* 0.007 jd)
        :fill col-white
        :stroke col-black
        :stroke-width 0.001}]

    ;; Depth 1: Radiant lotus petal with spine and jewel
      1
      (let [petal (petal-contour inner-r outer-r mid-angle (* 0.48 half-width) col-secondary col-black 0.002 curl)
            spine {:type :line
                   :from (polar->cart inner-r mid-angle)
                   :to (polar->cart (* 0.96 outer-r) mid-angle)
                   :stroke col-gold
                   :stroke-width 0.002}
            jewel {:type :circle
                   :center (polar->cart (+ inner-r (* 0.45 (- outer-r inner-r))) mid-angle)
                   :radius (* 0.009 jd)
                   :fill col-white
                   :stroke col-black
                   :stroke-width 0.0015}]
        [petal spine jewel])

    ;; Depth 2: Double nested lotus petals with sacred gem
      2
      (let [outer-p (petal-contour inner-r outer-r mid-angle (* 0.49 half-width) col-primary col-black 0.0025 curl)
            inner-top (+ inner-r (* 0.72 (- outer-r inner-r)))
            inner-p (petal-contour inner-r inner-top mid-angle (* 0.28 half-width) col-accent col-black 0.002 curl)
            spine   {:type :line
                     :from (polar->cart inner-r mid-angle)
                     :to (polar->cart (* 0.96 outer-r) mid-angle)
                     :stroke col-gold
                     :stroke-width 0.002}
            gem-r   (+ inner-r (* 0.42 (- outer-r inner-r)))
            gem     {:type :circle
                     :center (polar->cart gem-r mid-angle)
                     :radius (* 0.010 jd)
                     :fill col-gold
                     :stroke col-black
                     :stroke-width 0.0015}
            gem-c   {:type :circle
                     :center (polar->cart gem-r mid-angle)
                     :radius (* 0.005 jd)
                     :fill col-white
                     :stroke col-black
                     :stroke-width 0.001}]
        [outer-p inner-p spine gem gem-c])

    ;; Depth 3+: Triple nested lotus petals with gold core and pearl tip
      (let [outer-p  (petal-contour inner-r outer-r mid-angle (* 0.49 half-width) col-secondary col-black 0.0025 curl)
            mid-top  (+ inner-r (* 0.78 (- outer-r inner-r)))
            mid-p    (petal-contour inner-r mid-top mid-angle (* 0.32 half-width) col-primary col-black 0.002 curl)
            core-top (+ inner-r (* 0.48 (- outer-r inner-r)))
            core-p   (petal-contour inner-r core-top mid-angle (* 0.16 half-width) col-gold col-black 0.0015 curl)
            spine    {:type :line
                      :from (polar->cart inner-r mid-angle)
                      :to (polar->cart (* 0.98 outer-r) mid-angle)
                      :stroke col-white
                      :stroke-width 0.002}
            pearl    {:type :circle
                      :center (polar->cart (+ inner-r (* 0.48 (- outer-r inner-r))) mid-angle)
                      :radius (* 0.008 jd)
                      :fill col-white
                      :stroke col-black
                      :stroke-width 0.0015}]
        [outer-p mid-p core-p spine pearl]))))

(defn- ring-sector-motifs
  "Generate motif primitives for one angular sector of ring `ring-idx`."
  [ring-idx inner-r outer-r sector-angle depth total-petals symmetry-order palette-hex shape]
  (let [k             (max 1 (quot total-petals symmetry-order))
        half-width    (* (/ sector-angle (* 2.0 k)) 0.96 (:width-jitter shape 1.0))
        curl          (:curl shape 0.0)
        jewel-density (:jewel-density shape 1.0)
        col-gold      (get pigment-hex-map :gold-leaf)
        col-white     (get pigment-hex-map :conch-white)
        col-black     (get pigment-hex-map :lampblack)
        col-primary   (nth palette-hex (mod ring-idx (count palette-hex)))
        col-secondary (nth palette-hex (mod (+ ring-idx 2) (count palette-hex)))
        col-accent    (nth palette-hex (mod (+ ring-idx 4) (count palette-hex)))]
    (vec
     (mapcat (fn [j]
               (let [mid-angle (* sector-angle (/ (+ j 0.5) (double k)))]
                 (single-petal-motifs inner-r outer-r mid-angle half-width depth
                                      col-primary col-secondary col-accent
                                      col-gold col-white col-black
                                      (:width-jitter shape 1.0) curl jewel-density)))
             (range k)))))

(defn- lace-sector-motifs
  "Generate pearl chain plus diamond links on mid radius of a ring band."
  [inner-r outer-r sector-angle symmetry-order]
  (let [mid-r (/ (+ inner-r outer-r) 2.0)
        col-gold (get pigment-hex-map :gold-leaf)
        col-white (get pigment-hex-map :conch-white)
        col-black (get pigment-hex-map :lampblack)
        pearl {:type :circle
               :center (polar->cart mid-r (/ sector-angle 2.0))
               :radius 0.006
               :fill col-white
               :stroke col-black
               :stroke-width 0.001}
        d 0.012
        [pcx pcy] (polar->cart mid-r 0.0)
        diamond {:type :polygon
                 :points [[pcx (- pcy d)] [(+ pcx d) pcy] [pcx (+ pcy d)] [(- pcx d) pcy]]
                 :fill col-gold
                 :stroke col-black
                 :stroke-width 0.001}]
    (vec (replicate-d-n [pearl diamond] symmetry-order))))

;; =============================================================================
;; Cardinal Torana Gate Architecture (Sacred Portals)
;; =============================================================================

(defn- torana-gate-cardinal
  "Generate a multi-tiered T-shaped Torana portal along a cardinal boundary edge."
  [orientation gate-pos width depth col-gold col-vermilion col-white col-black]
  (let [hw      (/ width 2.0)
        hw-out  (* hw 1.25)
        hw-top  (* hw 0.75)
        d       depth]
    (case orientation
      :north
      [;; Stepped Lintel (Outer T-crossbeam)
       {:type :polygon
        :id :torana-north
        :points [[(- hw-out) gate-pos]
                 [hw-out gate-pos]
                 [hw-out (- gate-pos (* 0.4 d))]
                 [hw (- gate-pos (* 0.4 d))]
                 [hw (- gate-pos d)]
                 [(- hw) (- gate-pos d)]
                 [(- hw) (- gate-pos (* 0.4 d))]
                 [(- hw-out) (- gate-pos (* 0.4 d))]]
        :fill col-gold
        :stroke col-black
        :stroke-width 0.003}
       ;; Inner threshold vestibule
       {:type :polygon
        :points [[(- hw-top) (- gate-pos d)]
                 [hw-top (- gate-pos d)]
                 [hw-top (- gate-pos (* 1.5 d))]
                 [(- hw-top) (- gate-pos (* 1.5 d))]]
        :fill col-vermilion
        :stroke col-gold
        :stroke-width 0.002}
       ;; Cardinal gem finial
       {:type :circle
        :center [0.0 (- gate-pos (* 1.2 d))]
        :radius 0.018
        :fill col-white
        :stroke col-black
        :stroke-width 0.002}]

      :south
      [;; Stepped Lintel
       {:type :polygon
        :id :torana-south
        :points [[(- hw-out) (- gate-pos)]
                 [hw-out (- gate-pos)]
                 [hw-out (+ (- gate-pos) (* 0.4 d))]
                 [hw (+ (- gate-pos) (* 0.4 d))]
                 [hw (+ (- gate-pos) d)]
                 [(- hw) (+ (- gate-pos) d)]
                 [(- hw) (+ (- gate-pos) (* 0.4 d))]
                 [(- hw-out) (+ (- gate-pos) (* 0.4 d))]]
        :fill col-gold
        :stroke col-black
        :stroke-width 0.003}
       ;; Inner threshold vestibule
       {:type :polygon
        :points [[(- hw-top) (+ (- gate-pos) d)]
                 [hw-top (+ (- gate-pos) d)]
                 [hw-top (+ (- gate-pos) (* 1.5 d))]
                 [(- hw-top) (+ (- gate-pos) (* 1.5 d))]]
        :fill col-vermilion
        :stroke col-gold
        :stroke-width 0.002}
       ;; Cardinal gem finial
       {:type :circle
        :center [0.0 (+ (- gate-pos) (* 1.2 d))]
        :radius 0.018
        :fill col-white
        :stroke col-black
        :stroke-width 0.002}]

      :east
      [;; Stepped Lintel
       {:type :polygon
        :id :torana-east
        :points [[gate-pos (- hw-out)]
                 [gate-pos hw-out]
                 [(- gate-pos (* 0.4 d)) hw-out]
                 [(- gate-pos (* 0.4 d)) hw]
                 [(- gate-pos d) hw]
                 [(- gate-pos d) (- hw)]
                 [(- gate-pos (* 0.4 d)) (- hw)]
                 [(- gate-pos (* 0.4 d)) (- hw-out)]]
        :fill col-gold
        :stroke col-black
        :stroke-width 0.003}
       ;; Inner threshold vestibule
       {:type :polygon
        :points [[(- gate-pos d) (- hw-top)]
                 [(- gate-pos d) hw-top]
                 [(- gate-pos (* 1.5 d)) hw-top]
                 [(- gate-pos (* 1.5 d)) (- hw-top)]]
        :fill col-vermilion
        :stroke col-gold
        :stroke-width 0.002}
       ;; Cardinal gem finial
       {:type :circle
        :center [(- gate-pos (* 1.2 d)) 0.0]
        :radius 0.018
        :fill col-white
        :stroke col-black
        :stroke-width 0.002}]

      :west
      [;; Stepped Lintel
       {:type :polygon
        :id :torana-west
        :points [[(- gate-pos) (- hw-out)]
                 [(- gate-pos) hw-out]
                 [(+ (- gate-pos) (* 0.4 d)) hw-out]
                 [(+ (- gate-pos) (* 0.4 d)) hw]
                 [(+ (- gate-pos) d) hw]
                 [(+ (- gate-pos) d) (- hw)]
                 [(+ (- gate-pos) (* 0.4 d)) (- hw)]
                 [(+ (- gate-pos) (* 0.4 d)) (- hw-out)]]
        :fill col-gold
        :stroke col-black
        :stroke-width 0.003}
       ;; Inner threshold vestibule
       {:type :polygon
        :points [[(+ (- gate-pos) d) (- hw-top)]
                 [(+ (- gate-pos) d) hw-top]
                 [(+ (- gate-pos) (* 1.5 d)) hw-top]
                 [(+ (- gate-pos) (* 1.5 d)) (- hw-top)]]
        :fill col-vermilion
        :stroke col-gold
        :stroke-width 0.002}
       ;; Cardinal gem finial
       {:type :circle
        :center [(+ (- gate-pos) (* 1.2 d)) 0.0]
        :radius 0.018
        :fill col-white
        :stroke col-black
        :stroke-width 0.002}])))

;; =============================================================================
;; Square Outer Boundary Layer (Sacred Enclosure)
;; =============================================================================

(defn- corner-rosette
  "Generate sacred quadrant rosette plus 5-dot vine fan in corner space."
  [[cx cy] col-vermilion col-gold col-white col-black]
  (let [fan-dots (mapv (fn [k]
                         (let [a (+ (/ Math/PI 4.0) (* k (/ Math/PI 24.0)))
                               dx (* 0.09 (Math/cos a))
                               dy (* 0.09 (Math/sin a))
                               sx (if (pos? cx) 1 -1)
                               sy (if (pos? cy) 1 -1)]
                           {:type :circle
                            :center [(+ cx (* sx dx)) (+ cy (* sy dy))]
                            :radius 0.008
                            :fill col-white
                            :stroke col-black
                            :stroke-width 0.001}))
                       (range 5))
        base [{:type :circle
               :center [cx cy]
               :radius 0.055
               :fill col-vermilion
               :stroke col-gold
               :stroke-width 0.003}
              {:type :circle
               :center [cx cy]
               :radius 0.035
               :fill col-gold
               :stroke col-black
               :stroke-width 0.002}
              {:type :circle
               :center [cx cy]
               :radius 0.018
               :fill col-white
               :stroke col-black
               :stroke-width 0.0015}
              {:type :circle
               :center [cx cy]
               :radius 0.075
               :fill nil
               :stroke col-gold
               :stroke-width 0.0015}
              {:type :circle
               :center [cx cy]
               :radius 0.095
               :fill nil
               :stroke col-white
               :stroke-width 0.001}
              {:type :circle
               :center [cx cy]
               :radius 0.115
               :fill nil
               :stroke col-gold
               :stroke-width 0.001}
              {:type :circle
               :center [cx cy]
               :radius 0.004
               :fill col-gold
               :stroke col-black
               :stroke-width 0.001}
              {:type :circle
               :center [(+ cx (if (pos? cx) 0.115 -0.115)) cy]
               :radius 0.004
               :fill col-gold
               :stroke col-black
               :stroke-width 0.001}
              {:type :circle
               :center [cx (+ cy (if (pos? cy) 0.115 -0.115))]
               :radius 0.004
               :fill col-gold
               :stroke col-black
               :stroke-width 0.001}
              {:type :circle
               :center [(+ cx (if (pos? cx) -0.115 0.115)) cy]
               :radius 0.004
               :fill col-gold
               :stroke col-black
               :stroke-width 0.001}
              {:type :circle
               :center [cx (+ cy (if (pos? cy) -0.115 0.115))]
               :radius 0.004
               :fill col-gold
               :stroke col-black
               :stroke-width 0.001}]]
    (vec (concat base fan-dots))))

(defn- square-boundary-layer
  "Generate square outer boundary enclosure enclosing the circular mandala."
  [square-size _torana-w _torana-d]
  (let [s (/ square-size 2.0)
        s-in1 (* s 0.97)
        s-in2 (* s 0.94)
        col-gold      (get pigment-hex-map :gold-leaf)
        col-vermilion (get pigment-hex-map :vermilion)
        col-navy      "#061735" ;; Chitrapata Abyssal Navy background
        col-white     (get pigment-hex-map :conch-white)
        col-black     (get pigment-hex-map :lampblack)
        frames
        [;; Outermost background square courtyard
         {:type :polygon
          :points [[(- s) (- s)] [s (- s)] [s s] [(- s) s]]
          :fill col-navy
          :stroke col-gold
          :stroke-width 0.006}
         ;; First inner golden perimeter
         {:type :polygon
          :points [[(- s-in1) (- s-in1)] [s-in1 (- s-in1)] [s-in1 s-in1] [(- s-in1) s-in1]]
          :fill nil
          :stroke col-gold
          :stroke-width 0.003}
         ;; Second inner conch-white perimeter
         {:type :polygon
          :points [[(- s-in2) (- s-in2)] [s-in2 (- s-in2)] [s-in2 s-in2] [(- s-in2) s-in2]]
          :fill nil
          :stroke col-white
          :stroke-width 0.002}]
        ;; Four corner quadrant rosettes
        corner-dist (* s 0.86)
        corners (mapcat #(corner-rosette % col-vermilion col-gold col-white col-black)
                        [[corner-dist corner-dist]
                         [(- corner-dist) corner-dist]
                         [(- corner-dist) (- corner-dist)]
                         [corner-dist (- corner-dist)]])]
    {:id :square-boundary
     :z-index 0
     :primitives (vec (concat frames corners))}))

(defn- torana-gates-layer
  "Generate four cardinal torana gates as top layer above discs."
  [square-size torana-w torana-d]
  (let [s (/ square-size 2.0)
        col-gold (get pigment-hex-map :gold-leaf)
        col-vermilion (get pigment-hex-map :vermilion)
        col-white (get pigment-hex-map :conch-white)
        col-black (get pigment-hex-map :lampblack)
        gates (concat
               (torana-gate-cardinal :north s torana-w torana-d col-gold col-vermilion col-white col-black)
               (torana-gate-cardinal :south s torana-w torana-d col-gold col-vermilion col-white col-black)
               (torana-gate-cardinal :east s torana-w torana-d col-gold col-vermilion col-white col-black)
               (torana-gate-cardinal :west s torana-w torana-d col-gold col-vermilion col-white col-black))
        hw (/ torana-w 2.0)
        pillar (fn [c] {:type :circle :center c :radius 0.014
                        :fill col-vermilion :stroke col-gold :stroke-width 0.002})
        pillars [(pillar [(+ hw 0.03) (- s 0.02)]) (pillar [(- (+ hw 0.03)) (- s 0.02)])
                 (pillar [(+ hw 0.03) (- (- s) -0.02)]) (pillar [(- (+ hw 0.03)) (- (- s) -0.02)])
                 (pillar [(- s 0.02) (+ hw 0.03)]) (pillar [(- s 0.02) (- (+ hw 0.03))])
                 (pillar [(- (- s) -0.02) (+ hw 0.03)]) (pillar [(- (- s) -0.02) (- (+ hw 0.03))])]]
    {:id :torana-gates
     :z-index 5
     :primitives (vec (concat gates pillars))}))

;; =============================================================================
;; Central Bindu Layer
;; =============================================================================

(defn- bindu-layer
  "Generate central bindu (cosmological origin seed) with halo and 8-seed lotus seat."
  [radius]
  (let [col-gold      (get pigment-hex-map :gold-leaf)
        col-vermilion (get pigment-hex-map :vermilion)
        col-white     (get pigment-hex-map :conch-white)
        col-black     (get pigment-hex-map :lampblack)
        halo {:type :circle
              :center [0.0 0.0]
              :radius (* radius 1.8)
              :fill nil
              :stroke col-gold
              :stroke-width 0.002}
        seat (mapv (fn [k]
                     (let [a (* k (/ (* 2.0 Math/PI) 8.0))
                           [sx sy] (polar->cart (* radius 2.6) a)]
                       {:type :circle
                        :center [sx sy]
                        :radius (* radius 0.16)
                        :fill col-white
                        :stroke col-black
                        :stroke-width 0.001}))
                   (range 8))]
    {:id :bindu
     :z-index 100
     :primitives
     (vec (concat
           [{:type :circle
             :center [0.0 0.0]
             :radius radius
             :fill col-vermilion
             :stroke col-gold
             :stroke-width 0.003}
            {:type :circle
             :center [0.0 0.0]
             :radius (* radius 0.65)
             :fill col-gold
             :stroke col-black
             :stroke-width 0.002}
            {:type :circle
             :center [0.0 0.0]
             :radius (* radius 0.30)
             :fill col-white
             :stroke col-black
             :stroke-width 0.0015}]
           [halo]
           seat))}))

(defn- flame-rim-layer
  "Generate 64-tooth flame rim triangles at r 0.84."
  []
  (let [n 64 r 0.84 w-step (/ Math/PI n 2.0) h 0.03
        col-gold (get pigment-hex-map :gold-leaf)
        col-black (get pigment-hex-map :lampblack)]
    {:id :flame-rim
     :z-index 6
     :primitives
     (vec (for [i (range n)]
            (let [a (* i (/ (* 2.0 Math/PI) n))
                  [bx by] (polar->cart r a)
                  [tx ty] (polar->cart (+ r h) a)
                  pa (+ a w-step)
                  [lx ly] (polar->cart r pa)
                  na (- a w-step)
                  [rx ry] (polar->cart r na)]
              {:type :polygon
               :points [[lx ly] [tx ty] [rx ry] [bx by]]
               :fill col-gold
               :stroke col-black
               :stroke-width 0.0012})))}))

;; =============================================================================
;; Main Pure Generate Function (PCG Contract)
;; =============================================================================

(defn generate
  "Pure Mandala procedural generator.
   Accepts {:seed <int> :params <map>}.
   Uses portable deterministic PRNG to derive dynamic visual variations while strictly preserving invariants."
  ([]
   (generate {:seed 42}))
  ([{:keys [seed params] :or {seed 42}}]
   (let [p             (derive-params seed params)
         {:keys [symmetry-order ring-count ring-radii motif-depths
                 bindu-radius square-size
                 torana-width torana-depth
                 petal-count-per-ring palette]} p
         sector-angle  (/ (* 2.0 Math/PI) (double symmetry-order))
         shape         {:width-jitter (:petal-width-jitter p 1.0)
                        :curl (:petal-curl p 0.0)
                        :jewel-density (:jewel-density p 1.0)}
         palette-hex   (mapv #(get pigment-hex-map % (get pigment-hex-map :gold-leaf)) palette)
         palette-meta  (mapv (fn [k] {:name (name k) :hex (get pigment-hex-map k)}) palette)
         col-gold      (get pigment-hex-map :gold-leaf)
         col-black     (get pigment-hex-map :lampblack)
         valid-rings   (min ring-count (count ring-radii))

          ;; 1. Square Boundary (Layer 0) + Torana Gates (Layer 5, above discs)
         boundary-l    (square-boundary-layer square-size torana-width torana-depth)
         gates-l       (torana-gates-layer square-size torana-width torana-depth)

         ;; 2. Annular Background Bands (Layer 1)
         ;; Drawn in DESCENDING order of radius so inner circles naturally layer over outer circles!
         bg-discs
         (mapv
          (fn [i]
            (let [radius   (nth ring-radii i)
                  bg-color (nth palette-hex (mod i (count palette-hex)))]
              {:type :circle
               :center [0.0 0.0]
               :radius radius
               :fill bg-color
               :stroke col-black
               :stroke-width 0.002}))
          (reverse (range valid-rings)))

         ;; Gold rims at ring perimeters
         gold-rims
         (mapv
          (fn [i]
            (let [radius (nth ring-radii i)]
              {:type :circle
               :center [0.0 0.0]
               :radius radius
               :fill nil
               :stroke col-gold
               :stroke-width 0.003}))
          (range valid-rings))

         backgrounds-l
         {:id :annular-backgrounds
          :z-index 1
          :primitives (vec (concat bg-discs gold-rims))}

         ;; 3. Motif Layers per Ring (Layers 2..k+1)
         ring-layers
         (mapv
          (fn [i]
            (let [inner-r    (if (zero? i) bindu-radius (nth ring-radii (dec i)))
                  outer-r    (nth ring-radii i)
                  depth      (nth motif-depths i 1)
                  petals     (nth petal-count-per-ring i symmetry-order)
                  sector-m   (ring-sector-motifs i inner-r outer-r sector-angle depth petals symmetry-order palette-hex shape)
                  replicated (replicate-d-n sector-m symmetry-order {:mirror? true})]
              {:id (keyword (str "ring-" i))
               :z-index (+ 2 i)
               :primitives (vec replicated)}))
          (range valid-rings))

          ;; 3b. Lace Layers per Ring Band
         lace-layers
         (mapv
          (fn [i]
            (let [inner-r (if (zero? i) bindu-radius (nth ring-radii (dec i)))
                  outer-r (nth ring-radii i)]
              {:id (keyword (str "lace-" i))
               :z-index (+ 30 i)
               :primitives (lace-sector-motifs inner-r outer-r sector-angle symmetry-order)}))
          (range valid-rings))

          ;; 4. Flame rim + Central Bindu (Topmost Layers)
         rim-l         (flame-rim-layer)
         bindu-l       (bindu-layer bindu-radius)

          ;; Compose All Layers in strict z-index order
         all-layers    (into [boundary-l backgrounds-l] (concat ring-layers lace-layers [rim-l gates-l bindu-l]))
         all-prims     (vec (mapcat :primitives all-layers))]

     {:seed       seed
      :artform    :mandala
      :params     p
      :palette    palette-meta
      :primitives all-prims
      :layers     all-layers
      :metadata   {:artform-family   :radial-symmetry
                   :visual-invariants [:n-fold-symmetry
                                       :square-outer-bound
                                       :four-torana-gates
                                       :central-bindu
                                       :dense-ring-fill
                                       :outward-complexity]
                   :coordinate-bounds {:min [-1.0 -1.0] :max [1.0 1.0]}}})))
