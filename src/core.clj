(ns core
  (:require [config]
            [hdfc.credit-card-statement :as cc]
            [hdfc.bank-statement :as bs]))

(defn load-config! []
  (when (= (config/get!)
           :config/defaults-used)
    (println "Parsing config file *failed*, using defaults..")))

(defn cli-parse [[type filename & _rest]]
  (case type
    "cc"      (do (load-config!)
                  (-> filename cc/process cc/format-statement)
                  (println)
                  (print "Finished generating the HDFC Credit Card Report!"))
    "bank"    (do (load-config!)
                  (-> filename bs/process bs/format-statement)
                  (println)
                  (print "Finished generating the HDFC Bank Statement Report!"))
    (print "Invalid statement type!")))

(defn -main [& args]
  (cli-parse args))
