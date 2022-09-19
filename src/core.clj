(ns core
  (:require [config]
            [clojure.pprint :as pp]
            [hdfc.credit-card-statement :as cc]
            [hdfc.bank-statement :as bs]
            [http.server :as server]))

(defn load-config! []
  (when (= (config/get!)
           :config/defaults-used)
    (println "Parsing config file *failed*, using defaults...\n")))

(defn cli-parse [[type filename & _rest]]
  (case type
    "confgen" (do (config/put!)
                  (print "Successfully wrote config to:" config/file))
    "cc"      (do (load-config!)
                  (pp/pprint (cc/process filename))
                  (print "\nFinished generating the HDFC Credit Card Report!"))
    "bank"    (do (load-config!)
                  (pp/pprint (bs/process filename))
                  (print "\nFinished generating the HDFC Bank Statement Report!"))
    "web"     (do (load-config!)
                  (println "Starting web server")
                  (server/start!))
    (print "Invalid statement type!")))

(defn -main [& args]
  (cli-parse args))
