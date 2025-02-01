(ns hdfc.bank-statement
  (:require [clojure.set :as cset]
            [clojure.string :as s]
            [config :refer [conf]]
            [sundry :refer :all]
            [clojure.pprint :refer [print-table]]))

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
       (read-csv)
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
            (rem-subvec narration)
            (cram-at narration-str narration-idx)))
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
        (update :debit-amount parse-rounded-float)
        (update :credit-amount parse-rounded-float)
        (update :closing-balance parse-rounded-float))
   row-maps))

(defn datefy [row-maps]
  (map
   #(-> %
        (update :date parse-ddmmyy)
        (update :value-date parse-ddmmyy))
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
                          (assoc :data (rem-subvec data matched-data)))))
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
        [from to]     (min-max-dates data)]
    (def *dbg (tagged-data data))
    {:from from :to to :totals totals :tagged-totals tagged-totals}))

(defn format-statement [data]
  (let [summary-keys [:withdrawls :deposits :expenditure :closing-balance]
        period {:period (str (:from data) " to " (:to data))}
        totals (-> (:totals data) (select-keys summary-keys))
        summary (merge period totals)
        tags (:tagged-totals data)
        rows (for [[tag {:keys [debit credit]}] tags]
               {:category (name tag)
                :debit (or debit 0)
                :credit (or credit 0)
                :net (- (or credit 0) (or debit 0))})]
    (println)
    (print "==> Summary")
    (print-table (cons :period summary-keys) [summary])
    (println)
    (print "==> Breakdown")
    (print-table (keys (first rows)) (sort-by :category rows))))
