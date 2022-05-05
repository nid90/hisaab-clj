(ns core
  (:require [credit-card-statement]
            [statement]))

(defn -main [& args]
  (let [[type filename & _rest] args]
    (case type
      "cc"   (credit-card-statement/process filename)
      "bank" (statement/process filename)
      (prn "Invalid statement type"))))
