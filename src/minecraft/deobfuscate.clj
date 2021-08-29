(ns minecraft.deobfuscate
  (:import
   [java.io File]
   [java.util.jar JarFile]
   [cuchaz.enigma.command CheckMappingsCommand]
   [net.fabricmc.stitch.commands CommandMergeTiny CommandProposeFieldNames CommandReorderTiny CommandRewriteIntermediary]
   [net.fabricmc.stitch.commands.tinyv2 CommandMergeTinyV2 CommandProposeV2FieldNames CommandReorderTinyV2]
   [net.fabricmc.stitch.merge JarMerger]
   [net.fabricmc.tinyremapper OutputConsumerPath TinyRemapper TinyUtils])
  (:require
   [minecraft.utils :as utils]
   [minecraft.sources :as sources]
   [clojure.java.io :as io]))

(def mappings-url "https://github.com/FabricMC/intermediary/raw/master/mappings/")
(def v2-mappings-url "https://maven.fabricmc.net/net/fabricmc/intermediary/")

;; Downloads
(defn download-intermediary [version]
  (utils/download-into-file (str mappings-url version ".tiny")
                            "sources"
                            (str version "-intermediary.tiny")))

(defn download-intermediary-v2 [version]
  (let [target (io/file "artifacts" "intermediary-v2.tiny")
        file (JarFile. (utils/download-into-file (str v2-mappings-url version "/intermediary-" version "-v2.jar")
                                                 "sources"
                                                 (str version "-intermediary-v2.jar")))]
    (io/copy (.getInputStream file (.getEntry file "mappings/mappings.tiny")) target)
    target))

(defn merge-jars [client-file server-file]
  (let [merged-file (File/createTempFile "mc-" "-merged.jar")
        merger (JarMerger. client-file server-file merged-file)]
    (.merge merger)
    (.close merger)
    merged-file))

(defn map-jar [output input mappings lib-files from to]
  (let [remapper (.. (TinyRemapper/newRemapper)
                     (withMappings (TinyUtils/createTinyMappingProvider (str mappings) from to))
                     (renameInvalidLocals true)
                     (rebuildSourceFilenames true)
                     (build))]
    (try
      (let [output-consumer (OutputConsumerPath. (str output))]
        (.addNonClassFiles output-consumer (str input))
        (.readInputs remapper (str input))
        (doseq [lib lib-files]
          (.readClassPath remapper (str lib)))
        (.apply remapper output-consumer)
        (.close output-consumer)
        (.finish remapper))
      (catch Exception e
        (.finish remapper)
        (throw (RuntimeException. "Failed to remap jar" e))))))

(defn map-intermediate-jar [version lib-files intermediary-file merged-file]
  (let [target (io/file "artifacts" (str version "-intermediary.jar"))]
    (map-jar target merged-file intermediary-file lib-files "official" "intermediary")))

(defn invert-intermediary [intermediary-file]
  (let [inverted-file (io/file "artifacts" "-intermediary-inverted.tiny")
        reorderer (CommandReorderTiny.)]
    (.run reorderer (into-array
                     [(str intermediary-file)
                      (str inverted-file)
                      "intermediary"
                      "official"]))
    inverted-file))

(defn patch-intermediary [merged-file intermediary-file]
  (let [patched-file (io/file "artifacts" "-intermediary-full.tiny")
        patcher (CommandRewriteIntermediary.)]
    (.run patcher (into-array
                   [(str merged-file)
                    (str intermediary-file)
                    (str patched-file)
                    "--writeAll"]))
    patched-file))

(def client-file (sources/download-source "1.17.1" :type :client))
(def server-file (sources/download-source "1.17.1" :type :server))
(def lib-files (sources/download-libs "1.17.1"))
(def merged-file (merge-jars client-file server-file))
(def intermediary-file (download-intermediary "1.17.1"))
#_(def patched-file (patch-intermediary merged-file intermediary-file))

(def intermediate-jar (map-intermediate-jar "1.17.1" lib-files intermediary-file merged-file))
