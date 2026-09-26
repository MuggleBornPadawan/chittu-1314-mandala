(ns art.export
  "Export utilities for Chitrapata PCG artwork.
   Emits canonical art.json, standalone SVG, and zero-build interactive Scittle HTML.
   Supports live in-browser Clojure generation without refreshing the page."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [art.artforms.mandala :as mandala]))

;; =============================================================================
;; SVG Serialization
;; =============================================================================

(defn- norm->canvas
  "Map normalized [-1.0, 1.0] coordinate to canvas space [0, size]."
  [v size]
  (let [half (/ size 2.0)]
    (+ half (* v half))))

(defn- format-num [^double n]
  (String/format java.util.Locale/US "%.4f" (to-array [n])))

(defn- primitive->svg
  [prim canvas-size]
  (let [scale-w (fn [w] (* (or w 0.002) canvas-size 0.5))]
    (case (:type prim)
      :circle
      (let [[cx cy] (:center prim)
            x (norm->canvas cx canvas-size)
            y (norm->canvas cy canvas-size)
            r (* (:radius prim) canvas-size 0.5)
            fill (or (:fill prim) "none")
            stroke (or (:stroke prim) "none")
            sw (scale-w (:stroke-width prim))]
        (format "<circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\" />\n"
                (format-num x) (format-num y) (format-num r) fill stroke (format-num sw)))

      :polygon
      (let [pts (mapv (fn [[px py]]
                        (str (format-num (norm->canvas px canvas-size))
                             ","
                             (format-num (norm->canvas py canvas-size))))
                      (:points prim))
            pts-str (str/join " " pts)
            fill (or (:fill prim) "none")
            stroke (or (:stroke prim) "none")
            sw (scale-w (:stroke-width prim))]
        (format "<polygon points=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\" stroke-linejoin=\"round\" />\n"
                pts-str fill stroke (format-num sw)))

      :line
      (let [[x1 y1] (:from prim)
            [x2 y2] (:to prim)
            sx1 (norm->canvas x1 canvas-size)
            sy1 (norm->canvas y1 canvas-size)
            sx2 (norm->canvas x2 canvas-size)
            sy2 (norm->canvas y2 canvas-size)
            stroke (or (:stroke prim) "#1A1A1A")
            sw (scale-w (:stroke-width prim))]
        (format "<line x1=\"%s\" y1=\"%s\" x2=\"%s\" y2=\"%s\" stroke=\"%s\" stroke-width=\"%s\" stroke-linecap=\"round\" />\n"
                (format-num sx1) (format-num sy1) (format-num sx2) (format-num sy2) stroke (format-num sw)))

      :rect
      (let [[cx cy] (:center prim)
            w (* (:w prim) canvas-size 0.5)
            h (* (:h prim) canvas-size 0.5)
            x (- (norm->canvas cx canvas-size) (/ w 2.0))
            y (- (norm->canvas cy canvas-size) (/ h 2.0))
            fill (or (:fill prim) "none")
            stroke (or (:stroke prim) "none")
            sw (scale-w (:stroke-width prim))]
        (format "<rect x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\" />\n"
                (format-num x) (format-num y) (format-num w) (format-num h) fill stroke (format-num sw)))

      "")))

(defn art->svg
  "Convert art data map to high-resolution SVG markup."
  ([art]
   (art->svg art 1400))
  ([art size]
   (let [sorted-layers (sort-by :z-index (:layers art))
         body-prims    (mapcat :primitives sorted-layers)
         prims-svg     (map #(primitive->svg % size) body-prims)]
     (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          (format "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"100%%\" height=\"100%%\">\n" size size)
          "<defs>\n"
          "  <style>\n"
          "    .bg { fill: #061735; }\n"
          "  </style>\n"
          "</defs>\n"
          (format "<rect class=\"bg\" width=\"%d\" height=\"%d\" />\n" size size)
          (str/join "" prims-svg)
          "</svg>\n"))))

;; =============================================================================
;; JSON Exporter
;; =============================================================================

(defn art->json
  "Convert art data map to formatted JSON for WebGPU and USD sharing."
  [art]
  (json/generate-string
   {:stage {:upAxis "Y" :metersPerUnit 1}
    :seed (:seed art)
    :artform (:artform art)
    :params (:params art)
    :palette (:palette art)
    :metadata (:metadata art)
    :layers (:layers art)
    :primitives (:primitives art)}
   {:pretty true}))

;; =============================================================================
;; Scittle Interactive HTML Viewer (Zero-Build Client-Side Clojure)
;; =============================================================================

(defn art->html
  "Generate single-file zero-build interactive HTML viewer with Scittle Clojure engine."
  [art initial-svg-str]
  (let [seed (:seed art)
        symmetry (get-in art [:params :symmetry-order])
        ring-count (get-in art [:params :ring-count])
        mandala-clj-src (slurp "src/art/artforms/mandala.clj")]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"en\">\n"
     "<head>\n"
     "  <meta charset=\"UTF-8\">\n"
     "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
     "  <title>Chitrapata Atelier — Dynamic Mandala (Scittle Clojure Engine)</title>\n"
     "  <style>\n"
     "    :root {\n"
     "      --bg: #061735;\n"
     "      --gold: #B3892C;\n"
     "      --white: #FAF0E6;\n"
     "      --vermilion: #E34234;\n"
     "      --card-bg: rgba(255, 255, 255, 0.05);\n"
     "    }\n"
     "    * { box-sizing: border-box; margin: 0; padding: 0; }\n"
     "    body {\n"
     "      background: var(--bg);\n"
     "      color: var(--white);\n"
     "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n"
     "      min-height: 100vh;\n"
     "      display: flex;\n"
     "      flex-direction: column;\n"
     "      align-items: center;\n"
     "      padding: 1.5rem 1rem;\n"
     "    }\n"
     "    header {\n"
     "      text-align: center;\n"
     "      margin-bottom: 1rem;\n"
     "    }\n"
     "    h1 {\n"
     "      font-size: 1.8rem;\n"
     "      color: var(--gold);\n"
     "      letter-spacing: 0.15em;\n"
     "      text-transform: uppercase;\n"
     "      margin-bottom: 0.25rem;\n"
     "    }\n"
     "    .meta {\n"
     "      font-size: 0.95rem;\n"
     "      opacity: 0.9;\n"
     "    }\n"
     "    .controls {\n"
     "      display: flex;\n"
     "      gap: 0.8rem;\n"
     "      align-items: center;\n"
     "      justify-content: center;\n"
     "      margin-bottom: 1.2rem;\n"
     "      flex-wrap: wrap;\n"
     "      background: var(--card-bg);\n"
     "      padding: 0.6rem 1.2rem;\n"
     "      border-radius: 8px;\n"
     "      border: 1px solid rgba(179, 137, 44, 0.3);\n"
     "    }\n"
     "    .btn {\n"
     "      background: var(--gold);\n"
     "      color: #061735;\n"
     "      font-weight: 600;\n"
     "      border: none;\n"
     "      padding: 0.5rem 1rem;\n"
     "      border-radius: 4px;\n"
     "      cursor: pointer;\n"
     "      font-size: 0.9rem;\n"
     "      transition: background 0.2s, transform 0.1s;\n"
     "    }\n"
     "    .btn:hover { background: #d4a017; transform: translateY(-1px); }\n"
     "    .btn-active { background: var(--vermilion); color: #fff; }\n"
     "    .btn-secondary {\n"
     "      background: transparent;\n"
     "      border: 1px solid var(--gold);\n"
     "      color: var(--white);\n"
     "    }\n"
     "    .btn-secondary:hover { background: rgba(179, 137, 44, 0.2); }\n"
     "    .seed-box {\n"
     "      display: flex;\n"
     "      align-items: center;\n"
     "      gap: 0.4rem;\n"
     "    }\n"
     "    .seed-input {\n"
     "      background: rgba(0, 0, 0, 0.4);\n"
     "      border: 1px solid rgba(179, 137, 44, 0.5);\n"
     "      color: var(--white);\n"
     "      padding: 0.45rem 0.6rem;\n"
     "      border-radius: 4px;\n"
     "      font-family: monospace;\n"
     "      font-size: 0.9rem;\n"
     "      width: 150px;\n"
     "    }\n"
     "    .art-frame {\n"
     "      width: min(78vh, 78vw, 720px);\n"
     "      height: min(78vh, 78vw, 720px);\n"
     "      background: #000;\n"
     "      border: 3px solid var(--gold);\n"
     "      border-radius: 4px;\n"
     "      box-shadow: 0 10px 40px rgba(0, 0, 0, 0.7), 0 0 30px rgba(179, 137, 44, 0.25);\n"
     "      overflow: hidden;\n"
     "      display: flex;\n"
     "      justify-content: center;\n"
     "      align-items: center;\n"
     "      transition: opacity 0.2s;\n"
     "    }\n"
     "    .art-frame svg {\n"
     "      width: 100%;\n"
     "      height: 100%;\n"
     "    }\n"
     "    .palette-strip {\n"
     "      display: flex;\n"
     "      gap: 0.6rem;\n"
     "      margin-top: 1.2rem;\n"
     "      flex-wrap: wrap;\n"
     "      justify-content: center;\n"
     "    }\n"
     "    .swatch {\n"
     "      display: flex;\n"
     "      align-items: center;\n"
     "      gap: 0.4rem;\n"
     "      font-size: 0.8rem;\n"
     "      background: rgba(255, 255, 255, 0.08);\n"
     "      padding: 0.25rem 0.6rem;\n"
     "      border-radius: 12px;\n"
     "      border: 1px solid rgba(179, 137, 44, 0.3);\n"
     "    }\n"
     "    .chip {\n"
     "      width: 14px;\n"
     "      height: 14px;\n"
     "      border-radius: 50%;\n"
     "      border: 1px solid #fff;\n"
     "    }\n"
     "    .engine-status {\n"
     "      font-size: 0.75rem;\n"
     "      opacity: 0.6;\n"
     "      margin-top: 0.8rem;\n"
     "    }\n"
     "  </style>\n"
     "  <!-- Scittle in-browser Clojure interpreter (Local fallback to CDN) -->\n"
     "  <script src=\"js/scittle.js\"></script>\n"
     "  <script>\n"
     "    if (typeof scittle === 'undefined') {\n"
     "      const s = document.createElement('script');\n"
     "      s.src = 'https://cdn.jsdelivr.net/npm/scittle/dist/scittle.js';\n"
     "      document.head.appendChild(s);\n"
     "    }\n"
     "  </script>\n"
     "</head>\n"
     "<body>\n"
     "  <header>\n"
     "    <h1>Chitrapata Atelier</h1>\n"
     "    <p class=\"meta\" id=\"art-meta\">Artform: <strong>Mandala</strong> | Seed: <strong id=\"seed-val\">" seed "</strong> | Symmetry: <strong>D" symmetry "</strong> | Rings: <strong>" ring-count "</strong></p>\n"
     "  </header>\n"
     "\n"
     "  <!-- Interactive Live Controls (Zero Page Reload) -->\n"
     "  <div class=\"controls\">\n"
     "    <button class=\"btn\" id=\"btn-next\" onclick=\"window.scittleNextSeed()\">⚡ Next Seed (Time)</button>\n"
     "    <button class=\"btn btn-active\" id=\"btn-autoplay\" onclick=\"window.scittleToggleAuto()\">⏸ Pause Auto-Evolve</button>\n"
     "    <div class=\"seed-box\">\n"
     "      <input type=\"text\" id=\"seed-input\" class=\"seed-input\" value=\"" seed "\" placeholder=\"Custom seed\" />\n"
     "      <button class=\"btn btn-secondary\" onclick=\"window.scittleApplySeed()\">Apply</button>\n"
     "    </div>\n"
     "  </div>\n"
     "\n"
     "  <!-- Art Frame: Updated in-place by Scittle -->\n"
     "  <div class=\"art-frame\" id=\"art-frame\">\n"
     "    " initial-svg-str "\n"
     "  </div>\n"
     "\n"
     "  <div class=\"palette-strip\" id=\"palette-strip\">\n"
     (str/join "\n"
               (map (fn [{:keys [name hex]}]
                      (format "    <div class=\"swatch\"><span class=\"chip\" style=\"background:%s\"></span><span>%s</span></div>"
                              hex name))
                    (:palette art)))
     "  </div>\n"
     "  <p class=\"engine-status\">Powered by Scittle: 100% in-browser Clojure PCG. Zero build, zero page reload.</p>\n"
     "\n"
     "  <!-- ============================================================== -->\n"
     "  <!-- Embedded Pure Clojure Generator + Scittle DOM Renderer         -->\n"
     "  <!-- ============================================================== -->\n"
     "  <script type=\"application/x-scittle\">\n"
     mandala-clj-src "\n\n"
     "    (ns app.viewer\n"
     "      (:require [art.artforms.mandala :as m]\n"
     "                [clojure.string :as str]))\n"
     "\n"
     "    (defn- norm->canvas [v size]\n"
     "      (let [half (/ size 2.0)]\n"
     "        (+ half (* v half))))\n"
     "\n"
     "    (defn- format-num [n]\n"
     "      (.toFixed (js/Number. n) 4))\n"
     "\n"
     "    (defn- primitive->svg [prim canvas-size]\n"
     "      (let [scale-w (fn [w] (* (or w 0.002) canvas-size 0.5))]\n"
     "        (case (:type prim)\n"
     "          :circle\n"
     "          (let [[cx cy] (:center prim)\n"
     "                x (norm->canvas cx canvas-size)\n"
     "                y (norm->canvas cy canvas-size)\n"
     "                r (* (:radius prim) canvas-size 0.5)\n"
     "                fill (or (:fill prim) \"none\")\n"
     "                stroke (or (:stroke prim) \"none\")\n"
     "                sw (scale-w (:stroke-width prim))]\n"
     "            (str \"<circle cx=\\\"\" (format-num x) \"\\\" cy=\\\"\" (format-num y) \"\\\" r=\\\"\" (format-num r)\n"
     "                 \"\\\" fill=\\\"\" fill \"\\\" stroke=\\\"\" stroke \"\\\" stroke-width=\\\"\" (format-num sw) \"\\\" />\\n\"))\n"
     "\n"
     "          :polygon\n"
     "          (let [pts (mapv (fn [[px py]]\n"
     "                            (str (format-num (norm->canvas px canvas-size))\n"
     "                                 \",\" \n"
     "                                 (format-num (norm->canvas py canvas-size))))\n"
     "                          (:points prim))\n"
     "                pts-str (str/join \" \" pts)\n"
     "                fill (or (:fill prim) \"none\")\n"
     "                stroke (or (:stroke prim) \"none\")\n"
     "                sw (scale-w (:stroke-width prim))]\n"
     "            (str \"<polygon points=\\\"\" pts-str \"\\\" fill=\\\"\" fill \"\\\" stroke=\\\"\" stroke\n"
     "                 \"\\\" stroke-width=\\\"\" (format-num sw) \"\\\" stroke-linejoin=\\\"round\\\" />\\n\"))\n"
     "\n"
     "          :line\n"
     "          (let [[x1 y1] (:from prim)\n"
     "                [x2 y2] (:to prim)\n"
     "                sx1 (norm->canvas x1 canvas-size)\n"
     "                sy1 (norm->canvas y1 canvas-size)\n"
     "                sx2 (norm->canvas x2 canvas-size)\n"
     "                sy2 (norm->canvas y2 canvas-size)\n"
     "                stroke (or (:stroke prim) \"#1A1A1A\")\n"
     "                sw (scale-w (:stroke-width prim))]\n"
     "            (str \"<line x1=\\\"\" (format-num sx1) \"\\\" y1=\\\"\" (format-num sy1)\n"
     "                 \"\\\" x2=\\\"\" (format-num sx2) \"\\\" y2=\\\"\" (format-num sy2)\n"
     "                 \"\\\" stroke=\\\"\" stroke \"\\\" stroke-width=\\\"\" (format-num sw) \"\\\" stroke-linecap=\\\"round\\\" />\\n\"))\n"
     "\n"
     "          :rect\n"
     "          (let [[cx cy] (:center prim)\n"
     "                w (* (:w prim) canvas-size 0.5)\n"
     "                h (* (:h prim) canvas-size 0.5)\n"
     "                x (- (norm->canvas cx canvas-size) (/ w 2.0))\n"
     "                y (- (norm->canvas cy canvas-size) (/ h 2.0))\n"
     "                fill (or (:fill prim) \"none\")\n"
     "                stroke (or (:stroke prim) \"none\")\n"
     "                sw (scale-w (:stroke-width prim))]\n"
     "            (str \"<rect x=\\\"\" (format-num x) \"\\\" y=\\\"\" (format-num y)\n"
     "                 \"\\\" width=\\\"\" (format-num w) \"\\\" height=\\\"\" (format-num h)\n"
     "                 \"\\\" fill=\\\"\" fill \"\\\" stroke=\\\"\" stroke \"\\\" stroke-width=\\\"\" (format-num sw) \"\\\" />\\n\"))\n"
     "\n"
     "          \"\")))\n"
     "\n"
     "    (defn art->svg [art size]\n"
     "      (let [sorted-layers (sort-by :z-index (:layers art))\n"
     "            body-prims    (mapcat :primitives sorted-layers)\n"
     "            prims-svg     (map #(primitive->svg % size) body-prims)]\n"
     "        (str \"<svg xmlns=\\\"http://www.w3.org/2000/svg\\\" viewBox=\\\"0 0 \" size \" \" size \"\\\" width=\\\"100%\\\" height=\\\"100%\\\">\\n\"\n"
     "             \"<rect width=\\\"\" size \"\\\" height=\\\"\" size \"\\\" fill=\\\"#061735\\\" />\\n\"\n"
     "             (str/join \"\" prims-svg)\n"
     "             \"</svg>\\n\")))\n"
     "\n"
     "    ;; Client UI state\n"
     "    (def current-seed (atom " seed "))\n"
     "    (def auto-play? (atom true))\n"
     "\n"
     "    (defn render-ui! [art]\n"
     "      (let [seed (:seed art)\n"
     "            symmetry (get-in art [:params :symmetry-order])\n"
     "            ring-count (get-in art [:params :ring-count])\n"
     "            palette (:palette art)\n"
     "            svg-str (art->svg art 1400)\n"
     "            frame-el (.getElementById js/document \"art-frame\")\n"
     "            meta-el (.getElementById js/document \"art-meta\")\n"
     "            seed-input (.getElementById js/document \"seed-input\")\n"
     "            palette-el (.getElementById js/document \"palette-strip\")]\n"
     "        (when frame-el (set! (.-innerHTML frame-el) svg-str))\n"
     "        (when meta-el\n"
     "          (set! (.-innerHTML meta-el)\n"
     "                (str \"Artform: <strong>Mandala</strong> | Seed: <strong>\" seed\n"
     "                     \"</strong> | Symmetry: <strong>D\" symmetry\n"
     "                     \"</strong> | Rings: <strong>\" ring-count \"</strong>\")))\n"
     "        (when seed-input (set! (.-value seed-input) (str seed)))\n"
     "        (when palette-el\n"
     "          (let [chips (map (fn [{:keys [name hex]}]\n"
     "                             (str \"<div class='swatch'><span class='chip' style='background:\" hex \"'></span><span>\" name \"</span></div>\"))\n"
     "                           palette)]\n"
     "            (set! (.-innerHTML palette-el) (str/join \"\" chips))))))\n"
     "\n"
     "    (defn update-seed! [new-seed]\n"
     "      (reset! current-seed new-seed)\n"
     "      (let [art (m/generate {:seed new-seed})]\n"
     "        (render-ui! art)))\n"
     "\n"
     "    (defn next-time-seed! []\n"
     "      (update-seed! (js/Date.now)))\n"
     "\n"
     "    (defn toggle-auto-play! []\n"
     "      (swap! auto-play? not)\n"
     "      (let [btn (.getElementById js/document \"btn-autoplay\")]\n"
     "        (if @auto-play?\n"
     "          (do\n"
     "            (set! (.-innerText btn) \"⏸ Pause Auto-Evolve\")\n"
     "            (set! (.-className btn) \"btn btn-active\"))\n"
     "          (do\n"
     "            (set! (.-innerText btn) \"▶ Start Auto-Evolve\")\n"
     "            (set! (.-className btn) \"btn\")))))\n"
     "\n"
     "    (defn apply-custom-seed! []\n"
     "      (let [val (.-value (.getElementById js/document \"seed-input\"))\n"
     "            parsed (js/parseInt val 10)]\n"
     "        (when-not (js/isNaN parsed)\n"
     "          (update-seed! parsed))))\n"
     "\n"
     "    ;; Expose functions to window for onclick handlers\n"
     "    (set! (.-scittleNextSeed js/window) next-time-seed!)\n"
     "    (set! (.-scittleToggleAuto js/window) toggle-auto-play!)\n"
     "    (set! (.-scittleApplySeed js/window) apply-custom-seed!)\n"
     "\n"
     "    ;; Start Auto-Evolve timer (every 2.5 seconds)\n"
     "    (js/setInterval\n"
     "      (fn []\n"
     "        (when @auto-play?\n"
     "          (next-time-seed!)))\n"
     "      2500)\n"
     "  </script>\n"
     "</body>\n"
     "</html>\n")))

;; =============================================================================
;; Export Orchestrator
;; =============================================================================

(defn export-all!
  "Generate mandala artwork and write art.json, mandala.svg, and index.html to out-dir."
  ([]
   (export-all! {:seed (System/currentTimeMillis) :out-dir "public"}))
  ([{:keys [seed params out-dir] :or {out-dir "public"}}]
   (let [actual-seed (or seed (System/currentTimeMillis))]
     (fs/create-dirs out-dir)
     (let [art       (mandala/generate {:seed actual-seed :params params})
           svg-str   (art->svg art 1400)
           json-str  (art->json art)
           html-str  (art->html art svg-str)
           json-path (str out-dir "/art.json")
           svg-path  (str out-dir "/mandala.svg")
           html-path (str out-dir "/index.html")]
       (spit json-path json-str)
       (spit svg-path svg-str)
       (spit html-path html-str)
       {:seed      actual-seed
        :json-file json-path
        :svg-file  svg-path
        :html-file html-path
        :primitives-count (count (:primitives art))
        :layers-count     (count (:layers art))}))))
