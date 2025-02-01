(ns hdfc.credit-card-statement
  (:require [clojure.string :as s]
            [clojurewerkz.money.amounts :as ma]
            [clojurewerkz.money.currencies :as mc]
            [clojurewerkz.money.format :as mf]
            [config :refer [conf]]
            [java-time :as time]
            [pdfboxing.text :as text]
            [sundry :refer :all]
            [clojure.pprint :refer [print-table]]))

(defn description->tag [description]
  (let [selected-tag (->> (get-in @conf [:bank-statement :tags])
                          (filter (fn [[tag descs]]
                                    (some #(s/includes? (s/upper-case description) %) descs)))
                          ffirst)]
    (or selected-tag :untagged)))

(defn sanitize-date
  "Works iff date has dd, mm and yyyy separated by slash"
  [date]
  (let [len              (count date)
        valid-date-chars 10]
    (subs date (- len valid-date-chars) len)))

(defn clobber-until-date-slash [fields]
  (drop-while #(not (boolean (re-find #"/" %))) fields))

(defn handle-null-at-row-beg [fields]
  (drop-while #(= "null" %) fields))

(defn row->map [credit? row]
  (let [fields             (->> (s/split row #" ")
                                (remove #(= % ""))
                                handle-null-at-row-beg
                                clobber-until-date-slash)
        [date & remaining] fields
        sanitized-date     (sanitize-date date)
        remaining          (if credit?
                             (drop-last remaining)
                             remaining)
        amount             (ma/parse (str "INR" (s/replace (last remaining) #"," "")))
        description        (s/join " " (drop-last remaining))]
    {:date        (parse-ddmmyyyy sanitized-date)
     :amount      amount
     :description description
     :tag         (description->tag description)}))

(defn gen-statement [credits debits]
  (let [[from to] (min-max-dates (concat credits debits))]
    {:from            from
     :to              to
     :total-credits   (mf/format (reduce ma/plus (map :amount credits)))
     :total-debits    (mf/format (reduce ma/plus (map :amount debits)))
     :debit-breakdown (->> (group-by :tag debits)
                           (map (fn [[tag expenses]]
                                  [tag (mf/format (reduce ma/plus
                                                          (map :amount expenses)))]))
                           (into {}))}))

(def drop-points (fn [page] (take-while (partial not= "Reward Points Summary") page)))
(def drop-unnecessary-header-rows (partial drop 7))
(def drop-unnecessary-footer-rows (partial drop-last 5))

(defn process [filename]
  (let [file-content                (-> filename
                                        text/extract
                                        (s/split #"(Domestic|International) Transactions"))
        pages                       (->> file-content
                                         (drop 1)
                                         (map #(s/split % #"\n")))
        butlast-page-rows           (mapcat #(->> %
                                                  drop-unnecessary-footer-rows
                                                  drop-unnecessary-header-rows)
                                            (butlast pages))
        last-page-rows              (->> pages
                                         last
                                         drop-points
                                         drop-unnecessary-header-rows)
        sanitized-rows              (concat butlast-page-rows last-page-rows)
        {debits false credits true} (group-by #(s/ends-with? % "Cr") sanitized-rows)
        credit-maps                 (map (partial row->map true) credits)
        debit-maps                  (map (partial row->map false) debits)]
    (def *dbg debit-maps)
    (gen-statement credit-maps debit-maps)))

(defn format-statement [data]
  (let [summary {:period (str (:from data) " to " (:to data))
                :total-credits (:total-credits data)
                :total-debits (:total-debits data)}
        breakdown (for [[category amount] (:debit-breakdown data)]
                    {:category (name category) :amount amount})]
    (println)
    (print "==> Summary")
    (print-table (keys summary) [summary])
    (println)
    (print "==> Expenditure Breakdown")
    (print-table (keys (first breakdown)) breakdown)))
