(ns art.server
  "Zero-dependency static file HTTP server for Babashka.
   Serves public directory without any external Maven or npm dependencies."
  (:require [clojure.string :as str]))

(def mime-types
  {"html" "text/html; charset=utf-8"
   "svg"  "image/svg+xml"
   "png"  "image/png"
   "json" "application/json"
   "js"   "application/javascript"
   "css"  "text/css"})

(defn- handle-connection
  [sock dir]
  (with-open [in (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream sock)))
              out (.getOutputStream sock)]
    (let [req-line (.readLine in)]
      (when req-line
        (let [parts (str/split req-line #" ")
              method (first parts)
              raw-path (second parts)]
          (when (contains? #{"GET" "HEAD"} method)
            (let [clean-path (first (str/split (or raw-path "/") #"\?"))
                  rel-path (if (or (= clean-path "/") (empty? clean-path)) "/index.html" clean-path)
                  file (java.io.File. dir (subs rel-path 1))]
              (if (and (.exists file) (.isFile file))
                (let [ext (last (str/split (.getName file) #"\."))
                      mime (get mime-types ext "application/octet-stream")
                      bytes (java.nio.file.Files/readAllBytes (.toPath file))]
                  (doto (java.io.PrintWriter. out)
                    (.print "HTTP/1.1 200 OK\r\n")
                    (.print (str "Content-Type: " mime "\r\n"))
                    (.print (str "Content-Length: " (count bytes) "\r\n"))
                    (.print "Connection: close\r\n\r\n")
                    (.flush))
                  (when (= method "GET")
                    (.write out bytes)
                    (.flush out)))
                (doto (java.io.PrintWriter. out)
                  (.print "HTTP/1.1 404 Not Found\r\n")
                  (.print "Content-Type: text/plain\r\n")
                  (.print "Content-Length: 9\r\n")
                  (.print "Connection: close\r\n\r\n")
                  (.print "Not Found")
                  (.flush))))))))))

(defn serve!
  ([dir] (serve! dir 8000))
  ([dir port]
   (let [ss (java.net.ServerSocket. port)]
     (println (str "Chitrapata HTTP Server active on http://localhost:" port))
     (println (str "Serving directory: " dir " (Press Ctrl+C to stop)"))
     (try
       (loop []
         (when-not (.isClosed ss)
           (with-open [sock (.accept ss)]
             (handle-connection sock dir))
           (recur)))
       (catch java.lang.Exception e
         (println (str "Server stopped: " (.getMessage e))))
       (finally
         (when-not (.isClosed ss)
           (.close ss)))))))
