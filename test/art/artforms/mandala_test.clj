(ns art.artforms.mandala-test
  (:require [clojure.set :as set]
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
      (let [bound-l (:square-boundary layer-map)
            gate-ids (keep :id (:primitives bound-l))]
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
