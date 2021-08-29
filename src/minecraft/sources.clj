(ns minecraft.sources
  (:require
   [minecraft.utils :as utils]
   [clojure.data.json :as json]
   [clojure.string :as str]))

(def version-manifest
  (delay (-> "https://launchermeta.mojang.com/mc/game/version_manifest.json"
             slurp
             (json/read-str :key-fn keyword)
             (update :versions (partial into {} (map (juxt :id #(dissoc % :id))))))))

(def latest-version
  (delay (get-in @version-manifest [:latest :release])))

(defn download-source [version & {:keys [type]
                                  :or {type :client}}]
  (let [{:keys [sha1 url]} (-> (get-in @version-manifest [:versions version :url])
                               slurp
                               (json/read-str :key-fn keyword)
                               :downloads
                               type)]
    (utils/validate-sha1 (utils/download-into-file url "sources" (str version "-" (name type) ".jar")) sha1)))

(defn download-libs [version]
  (let [libs (-> (get-in @version-manifest [:versions version :url])
                 slurp
                 (json/read-str :key-fn keyword)
                 :libraries)]
    (into []
          (for [lib libs
                :let [{:keys [sha1 url]} (get-in lib [:downloads :artifact])
                      filename (str/trim (last (str/split url #"/")))]]
            (utils/validate-sha1 (utils/download-into-file url "sources/libs" filename) sha1)))))

;; Steps, get official jar -> mapNamedJar -> decompile
