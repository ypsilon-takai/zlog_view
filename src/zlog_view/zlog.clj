(ns zlog-view.zlog
  (require [serial-port :as sp]))

;; connet control
(defn- connect [port-id]
  (try
    (sp/open port-id)
    (catch Exception e false)))

(defn- disconnect [port]
  (sp/close port))


;; data send/receive
(def command-format
  {:dataset \s
   :getdata \a
   :version \v
   :erase \x
   :reboot \R
   :reset \*})

(defn- send-command [port command & args]
  (let [command-char (int (get command-format command))
        command-seq (if (= command :getdata)
                      (->> args
                           (map int ,,)
                           (apply conj [command-char] ,,))
                      [command-char])]
    (sp/write-int-seq port command-seq)))

(defn- read-data [port])

(defn get-dataset [port-id]
  (if-let [port (connect port-id)]
    (do 
      (send-command port :dataset)
      (read-data port))
    false))
