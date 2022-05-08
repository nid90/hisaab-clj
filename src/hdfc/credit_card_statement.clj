(ns hdfc.credit-card-statement
  (:require [clojure.string :as s]
            [clojurewerkz.money.amounts :as ma]
            [clojurewerkz.money.currencies :as mc]
            [clojurewerkz.money.format :as mf]
            [config :refer [conf]]
            [pdfboxing.text :as text]))

(defn description->tag [description]
  (let [selected-tag (->> (get-in @conf [:bank-statement :tags])
                          (filter (fn [[tag descs]]
                                    (some #(s/includes? description %) descs)))
                          ffirst)]
    (or selected-tag :untagged)))

(defn row->map [credit? row]
  (let [fields             (->> (s/split row #" ")
                                (remove #(= % "")))
        [date & remaining] fields

        remaining          (if credit?
                             (drop-last remaining)
                             remaining)
        amount             (ma/parse (str "INR" (s/replace (last remaining) #"," "")))
        description        (s/join " " (drop-last remaining))]
    {:date        date
     :amount      amount
     :description description
     :tag         (description->tag description)}))

(defn gen-statement [credits debits]
  {:total-credits   (mf/format (reduce ma/plus (map :amount credits)))
   :total-debits    (mf/format (reduce ma/plus (map :amount debits)))
   :debit-breakdown (->> (group-by :tag debits)
                         (map (fn [[tag expenses]]
                                [tag (mf/format (reduce ma/plus
                                                        (map :amount expenses)))]))
                         (into {}))})

(defn process [filename]
  (let [file-content   (-> filename
                           text/extract
                           (s/split #"(Domestic|International) Transactions"))
        rows           (->> file-content
                            (drop 1)
                            (map #(s/split % #"\n"))
                            (drop-last))
        value-rows     (reduce (fn [coll page]
                                 (concat coll (subvec page 7 (-  (count page) 5))))
                               nil rows)
        {debits  false
         credits true} (group-by #(s/ends-with? % "Cr") value-rows)
        credit-maps    (map (partial row->map true) credits)
        debit-maps     (map (partial row->map false) debits)]
    (gen-statement credit-maps debit-maps)))