(ns statement
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(def debit-filter-keywords ["CLEARING" "LIC" "NEW FD", "CBDT", "BAJAJFINANCE"])
(def credit-filter-keywords ["FD PREMAT", "MUTUAL FUND", "MF", "NILENSO", "BDCPG5295B", "AUTO_REDE"])

(defn read-file-and-parse [file-name]
  (->>  (with-open [reader (io/reader file-name)]
          (doall (csv/read-csv reader)))
        (rest)
        (map #(map s/trim %))))

(defn remove-keyword-rows [row-maps keywords]
  (remove #(some true?
                 (map (fn [kw]
                        (boolean
                         (re-find (re-pattern kw)
                                  (get % "Narration"))))
                      keywords))
          row-maps))

(defn remove-nth-element-from-vec [coll i]
  (concat (subvec coll 0 i)
          (subvec coll (inc i))))

(defn get-total-amount [rows column]
  (apply + (map (fn [r] (Float/parseFloat (get r column))) rows)))

(defn get-total-expenditure [file-name]
  (let [[header & actual-rows] (read-file-and-parse file-name)
        row-minus-bad-row      (remove #(>= (count %) 8) actual-rows)
        bad-row                (first (filter #(>= (count %) 8) actual-rows))
        fixed-bad-row          (when bad-row (remove-nth-element-from-vec (vec bad-row) 2))
        row-maps               (map #(zipmap header %) (if fixed-bad-row
                                                         (conj row-minus-bad-row fixed-bad-row)
                                                         row-minus-bad-row))
        withdrawals            (remove-keyword-rows row-maps debit-filter-keywords)
        deposits               (remove-keyword-rows row-maps credit-filter-keywords)]
    (format "%.2f"
            (- (get-total-amount withdrawals "Debit Amount")
               (get-total-amount deposits "Credit Amount")))))
