(ns sundry
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defn cram-at
  "For a given vector,
  cram an element at the specified position
  and push the rest ahead.

  Throws an IndexOutOfBoundsException if 'at' is out of bounds.

  eg.,
  => (cram-at [1 3 4 5] 2 1)
  => [1 2 3 4 5]"
  [vec-coll e at]
  (concat
   (conj (subvec vec-coll 0 at) e)
   (subvec vec-coll at)))

(defn rem-subvec
  "For a given vector,
  remove the subvec (removables),
  and return a new vector.

  eg:
  => (remove-subvec [1 2 3 4 5] [2 3 4])
  => [1 5]"
  [vec-coll removables]
  (vec (remove (set removables) vec-coll)))

(defn titleize
  "For a given string, keyword or symbol,
  return a string with the first letter of all words in upper case.
  Also replaces hyphens with spaces."
  [input]
  (-> input
      (name)
      (s/replace #"-" " ")
      (s/split #"\b")
      (as-> words (map s/capitalize words))
      (s/join)))

(defn read-csv
  "For a given absolute csv file path,
  read and return the entire file into memory."
  [f]
  (with-open [rd (io/reader (io/file f))]
    (doall (csv/read-csv rd))))

(defmacro nil-on-exceptions
  "Catch any Exception from the body and return nil."
  [& body]
  `(try
     ~@body
     (catch Exception e#
       nil)))

(defn parse-rounded-float
  "Parse string as float and then round it.
  Return nil if unparseable."
  [s]
  (nil-on-exceptions
   (-> s
       Float/parseFloat
       Math/round
       int)))
