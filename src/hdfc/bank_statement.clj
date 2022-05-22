(ns hdfc.bank-statement
  (:require [clojure.set :as cset]
            [clojure.string :as s]
            [config :refer [conf]]
            [sundry :as e]))

(def header-keys
  {"Date"            :date
   "Narration"       :narration
   "Value Dat"       :value-date
   "Debit Amount"    :debit-amount
   "Credit Amount"   :credit-amount
   "Chq/Ref Number"  :ref-number
   "Closing Balance" :closing-balance})

(def narration-idx 1)

(defn fetch! [f]
  (->> f
       (e/read-csv)
       (rest)
       (map #(map s/trim %))))

(defn recombine-narration [header-count row]
  (let [column-count (count row)
        row-vec      (vec row)
        diff         (- column-count header-count)
        overflow?    (pos? diff)]
    (if overflow?
      (let [narration     (subvec row-vec narration-idx (+ 1 narration-idx diff))
            narration-str (s/join narration)]
        (-> row
            (e/rem-subvec narration)
            (e/cram-at narration-str narration-idx)))
      row-vec)))

(defn adjust-narrations
  "Narrations with commas break CSV parsing correctness.
  This undoes that and puts a comma-less narration back in the right place."
  [[header & value-rows]]
  (->> value-rows
       (map (partial recombine-narration (count header)))
       (concat [header])))

(defn header->row [[header & value-rows]]
  (let [header-keywords (map #(get header-keys %) header)]
    (map #(zipmap header-keywords  %) value-rows)))

(defn numeralize [row-maps]
  (map
   #(-> %
        (update :debit-amount e/parse-rounded-float)
        (update :credit-amount e/parse-rounded-float)
        (update :closing-balance e/parse-rounded-float))
   row-maps))

(defn datefy [row-maps]
  (map
   #(-> %
        (update :date e/parse-ddmmyy)
        (update :value-date e/parse-ddmmyy))
   row-maps))

(defn case-insensitive-regex [matcher]
  (re-pattern (str "(?i)" matcher)))

(defn matches-narration? [row matcher]
  (boolean (re-find (case-insensitive-regex matcher)
                    (get row :narration))))

(defn matching-narrations [row matchers]
  (map (partial matches-narration? row) matchers))

(defn reject-narrations [row-maps matchers]
  (remove #(some true? (matching-narrations % matchers)) row-maps))

(defn filter-narrations [row-maps matchers]
  (filter #(some true? (matching-narrations % matchers)) row-maps))

(defn filter-debits [row-maps]
  (reject-narrations row-maps
                     (get-in @conf [:bank-statement :filters :debit])))

(defn filter-credits [row-maps]
  (reject-narrations row-maps
                     (get-in @conf [:bank-statement :filters :credit])))

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
  (let [withdrawls      (total-debit-amount row-maps)
        deposits        (total-credit-amount row-maps)
        expenditure     (- withdrawls deposits)
        closing-balance (closing-balance row-maps)]
    {:withdrawls      withdrawls
     :deposits        deposits
     :expenditure     expenditure
     :closing-balance closing-balance}))

(defn ungrouped-data [data grouped-data]
  (cset/difference (set data)
                   (set (flatten (vals grouped-data)))))

(defn grouped-data
  "Keeps a track of used rows to only group them against one category."
  [initial-data]
  (->> [:bank-statement :tags]
       (get-in @conf)
       (reduce-kv (fn [m tag matchers]
                    (let [data         (:data m)
                          matched-data (filter-narrations data matchers)]
                      (-> m
                          (assoc-in [:grouped-data tag] matched-data)
                          (assoc :data (e/rem-subvec data matched-data)))))
                  {:grouped-data {} :data initial-data})
       (:grouped-data)))

(defn tagged-data [data]
  (let [grouped-data (grouped-data data)]
    (assoc grouped-data
           :untagged (ungrouped-data data grouped-data))))

(defn tagged-totals [grouped-data]
  (reduce-kv (fn [m group row]
               (assoc m group {:debit  (reduce + (map :debit-amount row))
                               :credit (reduce + (map :credit-amount row))}))
             {}
             grouped-data))

(defn process [file]
  (let [data          (-> file
                          fetch!
                          adjust-narrations
                          header->row
                          numeralize
                          datefy)
        totals        (-> data
                          filter-debits
                          filter-credits
                          gen-statement)
        tagged-totals (-> data
                          tagged-data
                          tagged-totals)
        [from to]     (e/min-max-dates data)]
    {:from from :to to :totals totals :tagged-totals tagged-totals}))
