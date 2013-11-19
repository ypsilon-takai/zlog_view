(ns zlog-view.zlog
  (require [serial-port :as sp]))

;; connect control
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


;; data send/receive
(defn- make-command [type & nums]
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

;; exported funcs
(defn get-device-info [port-name]
  (->> (command-and-read port-name
                         (send-command conn :version))
       (map char ,,)
       (apply str ,,)
       (#(clojure.string/split % #"\r\n*") ,,)))

(defn erase-data [port-name]
  (->> (command-and-read port-name
                         (send-command conn :erase))
       (map char ,,)
       (apply str ,,)
       (#(clojure.string/split % #"\r\n*") ,,)))

(defn reboot-device [port-name]
  (->> (command-and-read port-name
                         (send-command conn :reboot))
       (map char ,,)
       (apply str ,,)
       (#(clojure.string/split % #"\r\n*") ,,))  )

(defn factory-reset-device [port-name]
  (->> (command-and-read port-name
                         (send-command conn :reset))
       (map char ,,)
       (apply str ,,)
       (#(clojure.string/split % #"\r\n*") ,,))  )


(defn get-list [port-name]
  (let [output-lines (->> (command-and-read port-name
                                            (send-command conn :dataset))
                          (map char ,,)
                          (apply str ,,)
                          (#(clojure.string/split % #"\r\n*") ,,))]
    (for [line output-lines :when (re-matches #"\d+,\d+" line)]
      (->> (clojure.string/split line #",")
           (map #(Integer/parseInt %) ,,)
           ((fn [[idx cnt]] {:idx idx :data-count cnt}) ,, )))))


(defn- destruct-dataset [byte-seq]
  (let [[_ rate1 rate2 samples1 samples2 trigger & alt-data] byte-seq]
    {:rate (+ (* rate1 0xff) rate2)
     :sample-count (+ (* samples1 0xff) samples2)
     :trigger trigger
     :alt-data (->> alt-data
                    (partition 2 ,,) 
                    (map (fn [[l r]] (+ (* l 0xff) r)) ,,) ; byte * 2 -> short
                    (map #(if (>= % 0x8000) (+ (- 0xffff %) 1) %) ,,)) ; unsigend short -> signed
     }))

(defn get-dataset [port-name data-idx]
  (let [output-bytes (-> (command-and-read port-name
                                           (send-command conn :getdata data-idx))
                          )]
    (->> output-bytes
         (drop-while #(not= % 0x80),,)
         (destruct-dataset ,,))))

