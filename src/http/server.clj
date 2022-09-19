(ns http.server
  (:require [bidi.ring :as bidi]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as res]))

(defonce server (atom nil))

(def handler
  (bidi/make-handler ["/" [["" (fn [_] (res/response "hisaab made easy"))]
                           [true (fn [_] (res/not-found "boo, bad path"))]]]))

(defn start! []
  (reset! server (jetty/run-jetty handler {:port  3000
                                           :join? false})))

(defn stop! []
  (.stop @server)
  (reset! server nil))
