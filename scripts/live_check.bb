#!/usr/bin/env bb
;; Live page smoke test for the deployed mandala viewer.
;; Run: bb scripts/live_check.bb [base-url]
;; Checks HTTP status, page markers, art.json contract, SVG defs, PNG bytes.
;; Exit 0 = all pass. Exit 1 = any failure.
(require '[babashka.http-client :as http]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(def base-url (or (first *command-line-args*)
                  "https://mugglebornpadawan.github.io/chittu-1314-mandala"))

(def failures (atom []))

(defn check [label ok? detail]
  (println (str (if ok? "PASS" "FAIL") " " label (when detail (str " — " detail))))
  (when-not ok? (swap! failures conj label)))

(defn fetch
  ([path] (fetch path nil))
  ([path opts]
   (try
     (http/get (str base-url path) (merge {:throw false :timeout 15000} opts))
     (catch Exception e {:status -1 :body (str "exception: " (.getMessage e))}))))

;; 1. Page loads with viewer markers
(let [{:keys [status body]} (fetch "/")]
  (check "page-200" (= 200 status) (str "status " status))
  (when (= 200 status)
    (check "page-art-frame" (str/includes? body "art-frame") nil)
    (check "page-edition" (str/includes? body "Edition No.") nil)
    (check "page-scittle" (str/includes? body "scittle") nil)
    (check "page-controls" (str/includes? body "seed-input") nil)))

;; 2. art.json contract + freshness
(let [{:keys [status body]} (fetch "/art.json")]
  (check "json-200" (= 200 status) (str "status " status))
  (when (= 200 status)
    (let [art (try (json/parse-string body true) (catch Exception _ nil))]
      (check "json-valid" (some? art) nil)
      (when art
        (doseq [k [:seed :artform :params :palette :layers :primitives]]
          (check (str "json-key-" (name k)) (contains? art k) nil))
        (check "json-seed-42" (= 42 (:seed art)) (str "seed " (:seed art)))
        (check "json-prims" (pos? (count (:primitives art)))
               (str (count (:primitives art)) " prims"))
        ;; Freshness: overlap param only exists in post-upgrade exports
        (check "json-fresh-overlap"
               (contains? (:params art) :petal-overlap)
               "stale deploy if missing")))))

;; 3. SVG renders with glow defs
(let [{:keys [status body]} (fetch "/mandala.svg")]
  (check "svg-200" (= 200 status) (str "status " status))
  (when (= 200 status)
    (check "svg-root" (str/includes? body "<svg") nil)
    (check "svg-glow" (str/includes? body "radialGradient")
           "stale deploy if missing")))

;; 4. PNG preview bytes (raw, no string decoding)
(let [{:keys [status body]} (fetch "/mandala.png" {:as :bytes})]
  (check "png-200" (= 200 status) (str "status " status))
  (when (= 200 status)
    (let [magic (mapv #(bit-and % 0xff) (take 4 (seq body)))]
      (check "png-magic" (= [137 80 78 71] magic) (str "magic " magic))
      (check "png-size" (> (count body) 100000) (str (count body) " bytes")))))

(let [total 17]
  (println (str "\n" (- total (count @failures)) "/" total " checks passed.")))
(System/exit (if (empty? @failures) 0 1))
