(ns statement
  (:require [sundry :as e]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [clojure.set :as cset]
            [config :refer [tags]]))

(def header-keys
  {"Date"            :date
   "Narration"       :narration
   "Value Dat"       :value-date
   "Debit Amount"    :debit-amount
   "Credit Amount"   :credit-amount
   "Chq/Ref Number"  :ref-number
   "Closing Balance" :closing-balance})

(def debit-filters ["CLEARING" "LIC" "NEW FD", "CBDT", "BAJAJFINANCE"])
(def credit-filters ["FD PREMAT", "MUTUAL FUND", "MF", "NILENSO", "BDCP", "AUTO_REDE"])
(def narration-idx 1)

(defn fetch! [f]
  (->> f
       (e/read-csv)
       (rest)
       (map #(map s/trim %))))

(defn recombine-narration [header-count row]
  (let [column-count (count row)
        row-vec (vec row)
        diff (- column-count header-count)
        overflow? (pos? diff)]
    (if overflow?
      (let [narration (subvec row-vec narration-idx (+ 1 narration-idx diff))
            narration-str (s/join narration)]
        (-> row
            (e/rem-subvec narration)
            (e/cram-at narration-str narration-idx)))
      row-vec)))

(defn adjust-narrations [[header & value-rows]]
  (->> value-rows
       (map (partial recombine-narration (count header)))
       (concat [header])))

(defn header->row [[header & value-rows]]
  (let [header-keywords (map #(get header-keys %) header)]
    (map #(zipmap header-keywords  %) value-rows)))

(defn numeralize [row-maps]
  (letfn [(parse-float-and-round [s] (int (Float/parseFloat s)))]
    (map
     #(-> %
          (update :debit-amount parse-float-and-round)
          (update :credit-amount parse-float-and-round)
          (update :closing-balance parse-float-and-round))
     row-maps)))

(defn matches-narration? [row matcher]
  (boolean (re-find (re-pattern matcher)
                    (get row :narration))))

(defn matching-narrations [row matchers]
  (map (partial matches-narration? row) matchers))

(defn reject-narrations [row-maps matchers]
  (remove #(some true? (matching-narrations % matchers)) row-maps))

(defn filter-debits [row-maps]
  (reject-narrations row-maps debit-filters))

(defn filter-credits [row-maps]
  (reject-narrations row-maps credit-filters))

(defn closing-balance [row-maps]
  (last
   (map #(get % :closing-balance)
        row-maps)))

(defn total-amount [row-maps column]
  (reduce +
          0.00
          (map #(get % column) row-maps)))

(defn total-debit-amount [row-maps]
  (total-amount row-maps :debit-amount))

(defn total-credit-amount [row-maps]
  (total-amount row-maps :credit-amount))

(defn gen-statement [row-maps]
  (let [withdrawls (total-debit-amount row-maps)
        deposits (total-credit-amount row-maps)
        expenditure (- withdrawls deposits)
        closing-balance (closing-balance row-maps)]
    {:withdrawls withdrawls
     :deposits deposits
     :expenditure expenditure
     :closing-balance closing-balance}))

(defn pretty-print! [statement]
  (let [point "→ "
        value-formatter ": %.2f"
        nl "\n"
        keys (keys statement)
        vals (vals statement)
        titles (map e/titleize keys)
        formatted-titles (map #(str point % value-formatter nl) titles)
        left-hand-side (s/join formatted-titles)]
    (->> vals
         (cons left-hand-side)
         (apply format)
         (println))))

(defn filter-narrations [row-maps matchers]
  (filter #(some true? (matching-narrations % matchers)) row-maps))

(defn group-data [data]
  (let [grouped-data (reduce-kv (fn [m k v]
                                  (assoc m k (filter-narrations data v)))
                                {} tags)
        ungrouped-data (cset/difference (set data)
                                               (set (flatten (vals grouped-data))))]
    (assoc grouped-data :untagged ungrouped-data)))

(defn group-totals [grouped-data]
  (reduce-kv (fn [m k v]
               (assoc m k {:debit (apply + (map :debit-amount v))
                           :credit (apply + (map :credit-amount v))}))
       {} grouped-data))

(defn process [file]
  (let [data           (-> file
                           fetch!
                           adjust-narrations
                           header->row
                           numeralize)
        totals         (-> data
                           filter-debits
                           filter-credits
                           gen-statement)
        grouped-totals (-> data
                           group-data
                           group-totals)]
    (pp/pprint {:totals totals :group-totals grouped-totals})))
