(ns zlog-view.viewer
  (require [seesaw.core :as ss]
           [zlog-view.zlog :as zlog]))

;; (require '[seesaw.dev :as ssd])



;; control pain
;; connection control
(defn update-port-names
  [w]
  (ss/config! w :model (zlog/port-names)))


(defn connection-control
  []
  ["Port"
   " point to reflesh"
   (ss/combobox
    :id :portselect
    :model ["-none-"]
    :listen [:mouse-entered update-port-names])
   (ss/button
    :id :connectbtn
    :text "connect"
    :listen [:action-performed])])

;; zlog info
(defn zlog-info-view
  []
  ["ZLOG info"
   (ss/text
    :id :zloginfo
    :text "zloginfo"
    :multi-line? true
    :rows 7
    :editable? false)])

;; data 
(defn data-list
  []
  ["Data list"
   (ss/listbox
    :id :datalist
    :model ["data1" "data2"])])


;; second level
(defn control-pain
  []
  (ss/vertical-panel
   :id :controlpanel
   :bounds [30 40 50 60]
   :items (concat (connection-control)
                  (zlog-info-view)
                  (data-list))))

(defn graph-pain
  []
  (ss/tabbed-panel
   :id :glaph
   :placement :top
   :overflow :scroll
   :tabs [{:title "test"
           :content "content"}]))


;; toplevel
(defn main-window
  []
  (ss/frame
   :title "zlog viewer"
   :on-close :dispose
   :content (ss/border-panel
             :west (control-pain)
             :center (graph-pain))))


