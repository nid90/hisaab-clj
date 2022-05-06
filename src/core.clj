(ns core
  (:require [config]
            [clojure.pprint :as pp]
            [hdfc.credit-card-statement :as cc]
            [hdfc.bank-statement :as bs]))

(defn cli-parse [[type filename & _rest]]
  (case type
    "confgen" (do (config/put!)
                  (print "Successfully wrote config to:" config/file))
    "cc"      (do (pp/pprint (cc/process filename))
                  (print "\nFinished generating the HDFC Credit Card Report!"))
    "bank"    (do (pp/pprint (bs/process filename))
                  (print "\nFinished generating the HDFC Bank Statement Report!"))
    (print "Invalid statement type!")))

(defn -main [& args]
  (config/get!)
  (cli-parse args))
