(ns minecraft.utils
  (:require
   [clojure.java.io :as io]
   [digest :as d]))

(defn download-into-file [url dir name]
  (let [file (io/file dir name)]
    (some->
     (io/input-stream url)
     (io/copy file))
    file))

(defn validate-sha1 [file sha1]
  (let [calc-sha (d/sha1 file)]
    (if (not= sha1 calc-sha)
      (throw (ex-info "SHA1 mismatch" {:file-sha calc-sha
                                       :expected-sha sha1}))
      file)))
