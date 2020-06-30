(ns core
  (:require [clojure.pprint :as pp]
            [clojure.string :as s]
            [clojurewerkz.money.amounts :as ma]
            [clojurewerkz.money.currencies :as mc]
            [clojurewerkz.money.format :as mf]
            [pdfboxing.text :as text]))

(def tags
  {:transport     ["uber" "ola"]
   :f&b           ["swiggy" "zomato" "bar" "kitchen" "wine"]
   :groceries     ["grofers" "bigbasket" "carry fresh" "dunzo"]
   :shopping      ["amazon" "flipkart" "paypal" "retro days" "retail"]
   :subscriptions ["netflix" "apple" "mygiftcard" "nintendo" "google"]
   :donations     ["wikimedia" "milaap"]
   :medicines     ["apollo"]})

(defn description->tag [description]
  (let [description  (.toLowerCase description)
        selected-tag (->> tags
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

(defn print-report [credits debits]
  (pp/pprint
   {:from            (-> debits first :date)
    :to              (-> debits last :date)
    :total-credits   (mf/format (reduce ma/plus (map :amount credits)))
    :total-debits    (mf/format (reduce ma/plus (map :amount debits)))
    :debit-breakdown (->> (group-by :tag debits)
                          (map (fn [[tag expenses]]
                                 [tag (mf/format (reduce ma/plus
                                                         (map :amount expenses)))]))
                          (into {}))}))

(defn -main [filename & _args]
  (let [file-content   (-> filename
                           text/extract
                           (s/split #"(Domestic|International) Transactions"))
        rows           (->> file-content
                            (drop 1)
                            (map #(s/split % #"\n"))
                            flatten
                            (map s/trim)
                            (remove s/blank?)
                            (map #(re-matches #"^[0-3][0-9]/.*" %))
                            (remove nil?))
        {debits  false
         credits true} (group-by #(s/ends-with? % "Cr") rows)
        credit-maps    (map (partial row->map true) credits)
        debit-maps     (map (partial row->map false) debits)]
    (print-report credit-maps debit-maps)))
