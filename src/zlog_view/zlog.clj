(ns zlog-view.zlog
  (require [serial-port :as sp]))

;; connect control
(defmacro with-connection [port-id & body]
  `(let [~'conn (sp/open ~port-id)]
     (try
       ~@body
       (finally
         (sp/close ~'conn)))))

(defmacro command-and-read [port-id & commands]
  `(let [~'conn (sp/open ~port-id)
         buff# (atom [])]
     (try
       (sp/on-byte ~'conn #(swap! buff# conj %))
       ~@commands
       (Thread/sleep 10000)
       (into [] @buff#)
       (finally
         (sp/remove-listener ~'conn)
         (sp/close ~'conn)))))

(defmacro command-and-read [port-id & commands]
  `(let [~'conn (sp/open ~port-id)
         buff# (atom [])]
     (try
       (sp/on-byte ~'conn #(swap! buff# conj %))
       ~@commands
       (loop [last-count# (count @buff#)]
         (Thread/sleep 3000)
         (when (> (count @buff#) last-count#)
           (recur (count @buff#))))
       (into [] @buff#)
       (finally
         (sp/remove-listener ~'conn)
         (sp/close ~'conn)))))


(defn read-data
  ([port]
     (read-data port 3))
  ([port timeout]
     (let [buff (atom [])]
       )))

;; data send/receive
(defn make-command [type & nums]
  (->> (condp = type
        :dataset [\s] 
        :version [\v]
        :erase [\x]
        :reboot [\R]
        :reset [\*]
        :getdata (concat [\a] nums))
      (map int ,,)))

(defn- send-command [port command & args]
  (sp/write-int-seq port (apply make-command command args )))

(defn- read-data [port])

(defn get-dataset [port-name]
  (let [output (command-and-read port-name
                                 (send-command conn :dataset))
        output-str (clojure.string/split (apply str (map char output)) #"\r\n")]
    output-str
    ))

(defn get-dataset [port-name]
  (let [output-lines (->> (command-and-read port-name
                                            (send-command conn :dataset))
                          (map char ,,)
                          (apply str ,,)
                          (#(clojure.string/split % #"\r\n*") ,,))]
    (for [line output-lines :when (re-matches #"\d+,\d+" line)]
      (-> (clojure.string/split line #",")
          ((fn [[ r l]] (vector (Integer/parseInt r) l)) ,,)))))

(defn get-dataset [port-name]
  (let [output-lines (->> (command-and-read port-name
                                            (send-command conn :dataset))
                          (map char ,,)
                          (apply str ,,)
                          (#(clojure.string/split % #"\r") ,,))]
    output-lines))
