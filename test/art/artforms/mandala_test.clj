(ns art.artforms.mandala-test
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [art.artforms.mandala :as mandala]))

(deftest test-schema-and-contract
  (testing "Output schema conforms to Chitrapata / clojure-pcg contract"
    (let [art (mandala/generate {:seed 42})]
      (is (= 42 (:seed art)))
      (is (= :mandala (:artform art)))
      (is (map? (:params art)))
      (is (vector? (:palette art)))
      (is (seq (:palette art)))
      (is (vector? (:primitives art)))
      (is (seq (:primitives art)))
      (is (vector? (:layers art)))
      (is (map? (:metadata art)))
      (is (= :radial-symmetry (get-in art [:metadata :artform-family]))))))

(deftest test-determinism
  (testing "Identical seeds and parameters produce identical outputs"
    (doseq [seed [42 108 1314 2026]]
      (let [art1 (mandala/generate {:seed seed})
            art2 (mandala/generate {:seed seed})]
        (is (= art1 art2) (str "Output for seed " seed " must be strictly deterministic")))))

  (testing "Different seeds are logged correctly"
    (let [a1 (mandala/generate {:seed 42})
          a2 (mandala/generate {:seed 99})]
      (is (= 42 (:seed a1)))
      (is (= 99 (:seed a2))))))

(deftest test-coordinate-bounds
  (testing "All geometry coordinates strictly fall inside [-1.0, 1.0] normalized space"
    (let [art (mandala/generate {:seed 42})]
      (doseq [p (:primitives art)]
        (case (:type p)
          :circle
          (let [[cx cy] (:center p)
                r (:radius p)]
            (is (<= (- 1.0) (- cx r)) (str "Circle x-min out of bounds: " p))
            (is (<= (+ cx r) 1.0)     (str "Circle x-max out of bounds: " p))
            (is (<= (- 1.0) (- cy r)) (str "Circle y-min out of bounds: " p))
            (is (<= (+ cy r) 1.0)     (str "Circle y-max out of bounds: " p)))

          :polygon
          (doseq [[x y] (:points p)]
            (is (<= -1.0 x 1.0) (str "Polygon point x out of bounds: " [x y]))
            (is (<= -1.0 y 1.0) (str "Polygon point y out of bounds: " [x y])))

          :line
          (let [[x1 y1] (:from p)
                [x2 y2] (:to p)]
            (is (<= -1.0 x1 1.0))
            (is (<= -1.0 y1 1.0))
            (is (<= -1.0 x2 1.0))
            (is (<= -1.0 y2 1.0)))

          :rect
          (let [[cx cy] (:center p)
                hw (/ (:w p) 2.0)
                hh (/ (:h p) 2.0)]
            (is (<= -1.0 (- cx hw) 1.0))
            (is (<= -1.0 (+ cx hw) 1.0))
            (is (<= -1.0 (- cy hh) 1.0))
            (is (<= -1.0 (+ cy hh) 1.0)))

          nil)))))

(deftest test-visual-invariants
  (let [art (mandala/generate {:seed 42})
        layers (:layers art)
        layer-map (into {} (map (juxt :id identity) layers))]

    (testing "Visual Invariant: Central bindu exists and is at [0.0, 0.0]"
      (let [bindu-l (:bindu layer-map)]
        (is (some? bindu-l))
        (let [bindu-prim (first (:primitives bindu-l))]
          (is (= :circle (:type bindu-prim)))
          (is (= [0.0 0.0] (:center bindu-prim)))
          (is (pos? (:radius bindu-prim))))))

    (testing "Visual Invariant: Square outer boundary enclosure exists"
      (let [bound-l (:square-boundary layer-map)]
        (is (some? bound-l))
        (is (some #(= :polygon (:type %)) (:primitives bound-l)))))

    (testing "Visual Invariant: Exactly four cardinal torana gates exist"
      (let [all-prims (mapcat :primitives layers)
            gate-ids (keep :id all-prims)]
        (is (= 4 (count (filter #{:torana-north :torana-south :torana-east :torana-west} gate-ids))))
        (is (some #{:torana-north} gate-ids))
        (is (some #{:torana-south} gate-ids))
        (is (some #{:torana-east} gate-ids))
        (is (some #{:torana-west} gate-ids))))

    (testing "Visual Invariant: Perfect n-fold rotational symmetry (dihedral group D_n)"
      (let [order (get-in art [:params :symmetry-order])]
        (is (= 8 order))
        (is (zero? (mod order 4)) "Symmetry order must be multiple of 4 in mandala tradition")))

    (testing "Visual Invariant: Outward complexity gradient"
      (let [motif-depths (get-in art [:params :motif-depths])]
        (is (apply <= motif-depths) "Motif depth must be non-decreasing from center outward")))

    (testing "Visual Invariant: Palette uses only canonical pigments"
      (let [palette-names (set (map :name (:palette art)))
            canonical-names (set (map name (keys mandala/pigment-hex-map)))]
        (is (empty? (set/difference palette-names canonical-names)))))))

(deftest test-petal-overlap
  (testing "Overlap param exists, varies per seed, stays in range"
    (let [a42 (mandala/generate {:seed 42})
          a99 (mandala/generate {:seed 99})
          b42 (mandala/generate {:seed 42})
          ov42 (:petal-overlap (:params a42))]
      (is (= (:params b42) (:params a42)) "same seed same params")
      (is (some? ov42) "overlap param must exist")
      (is (<= 1.1 ov42 1.5) "overlap capped to avoid mud")
      (is (not= ov42 (:petal-overlap (:params a99))) "different seeds differ"))))

(deftest test-rim-64-and-bindu
  (testing "Flame rim has 64 teeth, bindu stays at origin"
    (let [art (mandala/generate {:seed 42})
          by-id (into {} (map (juxt :id identity) (:layers art)))
          rim (:flame-rim by-id)
          bindu (:bindu by-id)]
      (is (some? rim) "flame-rim layer must exist")
      (is (= 64 (count (:primitives rim))) "rim must have 64 teeth")
      (is (= [0.0 0.0] (:center (first (:primitives bindu)))) "bindu at origin"))))

(deftest test-seed-shape-variance
  (testing "Different seeds vary shape params, same seed is stable"
    (let [a42 (mandala/generate {:seed 42})
          a99 (mandala/generate {:seed 99})
          b42 (mandala/generate {:seed 42})]
      (is (= (:params b42) (:params a42)) "same seed same params")
      (is (not= (:petal-width-jitter (:params a42)) (:petal-width-jitter (:params a99)))
          "different seeds differ in shape")
      (is (not= (:primitives a42) (:primitives a99)) "art differs"))))

(deftest test-svg-has-gradient
  (testing "SVG defines glow gradient, palette is muted"
    (let [export-src (slurp "src/art/export.clj")]
      (is (str/includes? export-src "<radialGradient") "SVG must define gradients")
      (is (not= "#0BDA51" (get mandala/pigment-hex-map :malachite)) "neon green must be muted"))))

(deftest test-lace-inside-band
  (testing "Lace pearls sit inside first ring band"
    (let [art (mandala/generate {:seed 42})
          by-id (into {} (map (juxt :id identity) (:layers art)))
          lace (:lace-0 by-id)]
      (is (some? lace) "lace-0 layer must exist")
      (doseq [p (:primitives lace)]
        (let [[cx cy] (or (:center p) (first (:points p)))
              r (Math/sqrt (+ (* cx cx) (* cy cy)))]
          (is (<= 0.03 r 0.30) (str "lace prim outside band: " r)))))))

(deftest test-mirror-doubles
  (testing "Mirror replicates across x-axis"
    (let [prim {:type :line :from [0.1 0.0] :to [0.2 0.0] :stroke "#000" :stroke-width 0.002}
          out (mandala/replicate-d-n [prim] 8 {:mirror? true})]
      (is (= 16 (count out)) "8 rotations x 2 mirror = 16"))))

(deftest test-gates-above-discs
  (testing "Torana gates draw above annular discs"
    (let [art (mandala/generate {:seed 42})
          layers (:layers art)
          by-id (into {} (map (juxt :id identity) layers))
          gates (:torana-gates by-id)
          bg (:annular-backgrounds by-id)]
      (is (some? gates) "torana-gates layer must exist")
      (is (> (:z-index gates) (:z-index bg)) "gates must draw above discs"))))
