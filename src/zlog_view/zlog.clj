(ns zlog-view.zlog
  (require [serial-port :as sp]))

(def ^:dynamic *data-timeout* 3000)

;; connection control
(defmacro command-and-read [port-id & commands]
  `(let [conn# (sp/open ~port-id)
         buff# (atom [])]
     (try
       (sp/on-byte conn# #(swap! buff# conj %))
       (-> conn#
           ~@commands)
       (loop [last-count# (count @buff#)]
         (Thread/sleep *data-timeout*)
         (when (> (count @buff#) last-count#)
           (recur (count @buff#))))
       (into [] @buff#)
       (finally
         (sp/remove-listener conn#)
         (sp/close conn#)))))

#_(defmacro command-and-read [port-id & commands]
  `(try
     (let [conn# (sp/open ~port-id)
           buff# (atom [])]
       (try
         (sp/on-byte conn# #(swap! buff# conj %))
         (-> conn#
             ~@commands)
         (loop [last-count# (count @buff#)]
           (Thread/sleep *data-timeout*)
           (when (> (count @buff#) last-count#)
             (recur (count @buff#))))
         (into [] @buff#)
         (finally
           (sp/remove-listener conn#)
           (sp/close conn#))))
     (catch Exception e [])))

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


(defn- bytes->lines [byte-seq]
  (->> byte-seq
       (map char ,,)
       (apply str ,,)
       (clojure.string/split-lines ,,)))

;; exported funcs
(defn port-names
  []
  (mapv #(.getName %) (sp/port-ids)))

(defn get-device-info
  "Returns a map of device information from zlog which conncet port-name.
   Contents:
      :name -> Device name.
      :model -> Zlog model name.
      :fw-ver -> Zlog firmware version.
      :fw-date -> zlog firmware release date."
  [port-name]
  (->> (command-and-read port-name
                         (send-command ,,, :version))
       (map char ,,)
       (apply str ,,)
       (clojure.string/split-lines ,,)
       (zipmap [:name :model :fw-ver :fw-date] ,,)))

(defn erase-data
  "Erase all data in connected zlog."
  [port-name]
  (-> (command-and-read port-name
                        (send-command ,,, :erase))
      (bytes->lines)))

(defn reboot-device
  "Power on and off connected zlog to make new configuration active."
  [port-name]
  (-> (command-and-read port-name
                         (send-command ,,, :reboot))
      (bytes->lines)))

(defn factory-reset-device
  "Reset device to factory configurations."
  [port-name]
  (-> (command-and-read port-name
                         (send-command ,,, :reset))
      (bytes->lines)))

(defn get-list
  "Get data indices which stores in the connected zlog"
  [port-name]
  (let [output-lines (-> (command-and-read port-name
                                            (send-command ,,, :dataset))
                         (bytes->lines))]
    (for [line output-lines :when (re-matches #"\d+,\d+" line)]
      (->> (clojure.string/split line #",")
           (map #(Integer/parseInt %) ,,)
           (zipmap [:idx :aux] ,, )))))


(defn- destruct-dataset
  "Read byte sequences and translate them into altitude detas."
  [byte-seq]
  (let [[_ rate1 rate2 samples1 samples2 trigger & alt-data] byte-seq
        sample-count (let [count-in (+ (* samples1 0xff) samples2)]
                       (if (pos? count-in)
                         count-in
                         (quot (count alt-data) 2)))]
    {:rate (+ (* rate1 0xff) rate2)
     :sample-count sample-count
     :excess (= samples1 samples2 0)
     :trigger trigger
     :alt-data (->> alt-data
                    (partition 2 ,,) 
                    (map (fn [[l r]] (+ (* l 0x100) r)) ,,) ; byte * 2 -> short
                    (map #(if (>= % 0x8000) (+ (- 0xffff %) 1) %) ,,) ; unsigend short -> signed
                    (take sample-count ,,))                 
     }))

(defn get-dataset
  "Get altitude data which specified by index from zlog and returans altitude data seq"
  [port-name data-idx]
  (let [output-bytes (-> (command-and-read port-name
                                           (send-command ,,, :getdata data-idx))
                          )]
    (->> output-bytes
         (drop-while #(not= % 0x80),,)
         (destruct-dataset ,,))))

