(ns store
  (:require [next.jdbc :as jdbc]))

(def db-spec {:dbtype "sqlite" :dbname "hisaab.db"})

(defn schema->sql [schema]
  (-> schema
      (map (fn [[k v]] (format "%s %s" (name k) v)))
      (clojure.string/join ", ")))

(defn init-db [name schema]
  (jdbc/execute! db [(format "CREATE TABLE hdfc-bank (%s)"
                             (schema->sql schema))]))
