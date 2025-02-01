(ns config
  (:require [toml.core :as toml]
            [sundry :as e]
            [clojure.java.io :as io]))

(def conf (atom {}))
(def home-dir (System/getProperty "user.home"))
(def file (.toString (io/file home-dir "hisaab.conf.toml")))

(def ^:private defaults
  {:bank-statement
   {:tags
    {:transport     ["UBER" "OLA"]
     :fnb           ["SWIGGY" "ZOMATO" "BAR" "KITCHEN" "WINE"]
     :groceries     ["GROFERS" "BIGBASKET" "CARRY FRESH" "DUNZO"]
     :shopping      ["AMAZON" "FLIPKART" "PAYPAL" "RETRO DAYS" "RETAIL" "MALL"]
     :subscriptions ["NETFLIX" "APPLE" "MYGIFTCARD" "NINTENDO" "GOOGLE"]
     :donations     ["WIKIMEDIA" "MILAAP"]
     :medicines     ["APOLLO"]
     :investments   ["MF" "CLEARING" "LIC"]
     :household     ["URBAN COMPANY" "BROADBAND" "BBMP" "ELECTRICITY"]}
    :filters
    {:debit  ["CLEARING" "LIC" "NEW FD", "CBDT", "BAJAJFINANCE"]
     :credit ["FD PREMAT", "MUTUAL FUND", "MF", "NILENSO", "BDCP", "AUTO_REDE"]}}})

(defn get! []
  (if-let [data (e/nil-on-exceptions (-> file slurp (toml/read :keywordize)))]
    (reset! conf data)
    (do
      (reset! conf defaults)
      ::defaults-used)))
