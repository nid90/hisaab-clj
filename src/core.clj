(ns core
  (:require [config]
            [hdfc.credit-card-statement :as cc]
            [hdfc.bank-statement :as bs]))

(defn -main [& args]
  (config/read!)
  (let [[type filename & _rest] args]
    (case type
      "confgen" (do (config/gen!)
                    (println "Successfully wrote config to: " config/default-file-name))
      "cc"      (do (cc/process filename)
                    (println "Finished generating Credit Card Report!"))
      "bank"    (do (bs/process filename)
                    (println "Finished generating Bank Statement Report!"))
      (prn "Invalid statement type"))))
