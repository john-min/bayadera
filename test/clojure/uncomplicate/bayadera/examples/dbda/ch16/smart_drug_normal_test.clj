(ns ^{:author "Dragan Djuric"}
    uncomplicate.bayadera.examples.dbda.ch09.smart-drug-normal-test
  (:require [midje.sweet :refer :all]
            [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release let-release wrap-float]]
            [uncomplicate.fluokitten.core :refer [op]]
            [uncomplicate.neanderthal
             [core :refer [row native dot imax imin scal! col submatrix transfer dim]]
             [real :refer [entry entry!]]
             [math :refer [sqrt]]
             [native :refer [sv sge]]]
            [uncomplicate.bayadera
             [protocols :as p]
             [core :refer :all]
             [util :refer [bin-mapper hdi]]
             [opencl :refer [with-default-bayadera]]
             [mcmc :refer [mix! info anneal! burn-in! acc-rate! run-sampler!]]]
            [uncomplicate.bayadera.opencl.models
             :refer [gaussian-likelihood cl-distribution-model]]
            [uncomplicate.bayadera.toolbox
             [processing :refer :all]
             [plots :refer [render-sample render-histogram]]]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(def all-data (atom {}))
(def state (atom nil))

(def smart-drug-prior
  (cl-distribution-model [(slurp (io/resource "uncomplicate/bayadera/opencl/distributions/gaussian.h"))
                          (slurp (io/resource "uncomplicate/bayadera/opencl/distributions/uniform.h"))
                          (slurp (io/resource "uncomplicate/bayadera/examples/dbda/ch16/smart-drug-normal.h"))]
                         :name "smart_drug" :params-size 4 :dimension 2
                         :limits (sge 2 2 [80 120 0 40])))


(let [in-file (slurp (io/resource "uncomplicate/bayadera/examples/dbda/ch16/smart-drug.csv"))]
  (let [data (loop [s 0 p 0 data (drop 1 (csv/read-csv in-file))
                    smart (transient []) placebo (transient [])]
               (if data
                 (let [[b c] (first data)]
                   (case c
                     "Smart Drug"
                     (recur (inc s) p (next data) (conj! smart (double (read-string b))) placebo)
                     "Placebo"
                     (recur s (inc p) (next data) smart  (conj! placebo (double (read-string b))))))
                 [(op [s] (persistent! smart))
                  (op [p] (persistent! placebo))]))]
    (def params {:smart-drug (sv (data 0))
                 :placebo (sv (data 1))})))

(defn analysis []
  (with-default-bayadera
    (with-release [prior (distribution smart-drug-prior)
                   prior-dist (prior (sv 100 4 0 100))
                   smart-drug-post (posterior "smart_drug" (gaussian-likelihood (dim (:smart-drug params))) prior-dist)
                   smart-drug-dist (smart-drug-post (:smart-drug params))
                   smart-drug-sampler (sampler smart-drug-dist)
                   placebo-post (posterior "placebo" (gaussian-likelihood (dim (:placebo params))) prior-dist)
                   placebo-dist (placebo-post (:placebo params))
                   placebo-sampler (sampler placebo-dist)]
      (println (time (mix! smart-drug-sampler {:step 128})))
      (println (info smart-drug-sampler))
      (println (time (mix! placebo-sampler {:step 128})))
      (println (info placebo-sampler))
      {:smart-drug (histogram! smart-drug-sampler 10)
       :placebo (histogram! placebo-sampler 10)})))

(defn setup []
  (reset! state
          {:data @all-data
           :smart-drug-mean (plot2d (qa/current-applet) {:width 500 :height 500})
           :smart-drug-std (plot2d (qa/current-applet) {:width 500 :height 500})
           :placebo-mean (plot2d (qa/current-applet) {:width 500 :height 500})
           :placebo-std (plot2d (qa/current-applet) {:width 500 :height 500})}))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (q/background 0)
    (q/image (show (render-histogram (:smart-drug-mean @state) (:smart-drug @all-data) 0)) 0 0)
    (q/image (show (render-histogram (:smart-drug-std @state) (:smart-drug @all-data) 1)) 520 0)
    (q/image (show (render-histogram (:placebo-mean @state) (:placebo @all-data) 0)) 0 520)
    (q/image (show (render-histogram (:placebo-std @state) (:placebo @all-data) 1)) 520 520)))

(defn display-sketch []
  (q/defsketch diagrams
    :renderer :p2d
    :size :fullscreen
    :display 2
    :setup setup
    :draw draw
    :middleware [pause-on-error]))
