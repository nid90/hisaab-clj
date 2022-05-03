(ns statement
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(def narration-key "Narration")
(def debit-amount-key "Debit Amount")
(def credit-amount-key "Credit Amount")
(def closing-balance-key "Closing Balance")
(def debit-filters ["CLEARING" "LIC" "NEW FD", "CBDT", "BAJAJFINANCE"])
(def credit-filters ["FD PREMAT", "MUTUAL FUND", "MF", "NILENSO", "BDCPG5295B", "AUTO_REDE"])
(def narration-idx 1)

(defn fetch! [f]
  (with-open [rd (io/reader (io/file f))]
    (->> (doall (csv/read-csv rd))
         (rest)
         (map #(map s/trim %)))))

(defn assoc-at
  "For a given vector,
  Cram an element at the specified position
  and push the rest ahead

  eg.,

  (assoc-at [1 3 4 5] 2 1)
  => [1 2 3 4 5]
  "
  [coll e at]
  (concat
   (conj (subvec coll 0 at) e)
   (subvec coll at)))

(defn recombine-narration [header-count row]
  (let [column-count (count row)
        row-vec (vec row)
        diff (- column-count header-count)
        overflow? (pos? diff)]
    (if overflow?
      (let [narration (subvec row-vec narration-idx (+ 1 narration-idx diff))
            recomb-narration (s/join narration)]
        (-> (set narration)
            (remove row)
            (vec)
            (assoc-at recomb-narration narration-idx)))
      row-vec)))

(defn adjust-narrations [[header & value-rows]]
  (->> value-rows
       (map (partial recombine-narration (count header)))
       (concat [header])))

(defn header->row [[header & value-rows]]
  (map #(zipmap header %) value-rows))

(defn numeralize [row-maps]
  (letfn [(parse-float [s] (Float/parseFloat s))]
    (map
     #(-> %
          (update debit-amount-key parse-float)
          (update credit-amount-key parse-float)
          (update closing-balance-key parse-float))
     row-maps)))

(defn matches-narration? [row matcher]
  (boolean (re-find (re-pattern matcher)
                    (get row narration-key))))

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
   (map #(get % closing-balance-key)
        row-maps)))

(defn total-amount [row-maps column]
  (reduce +
          0.00
          (map #(get % column) row-maps)))

(defn total-debit-amount [row-maps]
  (total-amount row-maps debit-amount-key))

(defn total-credit-amount [row-maps]
  (total-amount row-maps credit-amount-key))

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
  (letfn [(titleize [s] (-> s
                            (name)
                            (s/replace #"-" " ")
                            (s/split #"\b")
                            (as-> words (map s/capitalize words))
                            (s/join)))]
    (let [point "→ "
          value-formatter ": %.2f"
          nl "\n"
          keys (keys statement)
          vals (vals statement)
          titles (map titleize keys)
          formatted-titles (map #(str point % value-formatter nl) titles)
          left-hand-side (s/join formatted-titles)]
      (->> vals
           (cons left-hand-side)
           (apply format)
           (println)))))

(defn process [file]
  (-> file
      fetch!
      adjust-narrations
      header->row
      numeralize
      filter-debits
      filter-credits
      gen-statement
      pretty-print!))

(comment
  (def f "/Users/kitallis/Code/scripts/hisaab/april.txt")
  (process f))
