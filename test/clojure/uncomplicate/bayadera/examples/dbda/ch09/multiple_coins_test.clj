;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    uncomplicate.bayadera.examples.dbda.ch09.multiple-coins-test
  (:require [midje.sweet :refer :all]
            [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release let-release wrap-float]]
            [uncomplicate.neanderthal
             [core :refer [row native dot imax imin scal! col submatrix]]
             [real :refer [entry entry!]]
             [native :refer [fv fge]]]
            [uncomplicate.bayadera
             [protocols :as p]
             [core :refer :all]
             [util :refer [bin-mapper hdi]]
             [opencl :refer [with-default-bayadera]]
             [mcmc :refer [mix! info anneal! burn-in! acc-rate! run-sampler!]]]
            [uncomplicate.bayadera.opencl.models
             :refer [cl-likelihood-model cl-distribution-model]]
            [uncomplicate.bayadera.toolbox
             [processing :refer :all]
             [plots :refer [render-sample render-histogram]]]
            [clojure.java.io :as io]))

(def all-data (atom {}))
(def state (atom nil))

(def multiple-coins-prior
  (cl-distribution-model [(slurp (io/resource "uncomplicate/bayadera/opencl/distributions/beta.h"))
                          (slurp (io/resource "uncomplicate/bayadera/examples/dbda/ch09/multiple-coins.h"))]
                         :name "multiple_coins" :params-size 3 :dimension 3
                         :limits (fge 2 3 [0 1 0 1 0 1])))

(def multiple-coins-likelihood
  (cl-likelihood-model [(slurp (io/resource "uncomplicate/bayadera/opencl/distributions/binomial.h"))
                        (slurp (io/resource "uncomplicate/bayadera/examples/dbda/ch09/multiple-coins-lik.h"))]
                       :name "multiple_coins" :params-size 4))

(defn analysis []
  (with-default-bayadera
    (let [walker-count (* 256 44 32)
          sample-count (* 16 walker-count)
          z0 3 N0 15
          z1 4 N1 5]
      (with-release [prior (distribution multiple-coins-prior)
                     prior-dist-5 (prior (fv 2 2 5))
                     prior-sampler-5 (time (doto (sampler prior-dist-5) (mix!)))
                     prior-dist-75 (prior (fv 2 2 75))
                     prior-sampler-75 (time (doto (sampler prior-dist-75) (mix!)))
                     post-model (posterior-model multiple-coins-likelihood multiple-coins-prior)
                     post (posterior post-model)
                     post-dist-5 (post (fv N0 z0 N1 z1 2 2 5))
                     post-sampler-5 (time (doto (sampler post-dist-5) (mix!)))
                     post-sample-5 (dataset (sample post-sampler-5))
                     post-dist-75 (post (fv N0 z0 N1 z1 2 2 75))
                     post-sampler-75 (time (doto (sampler post-dist-75) (mix!)))
                     post-sample-75 (dataset (sample post-sampler-75))]

        (println "Bayes Factor p(D|k=5)/p(D|k=75) = "
                 (/ (evidence post-dist-5 post-sample-5)
                    (evidence post-dist-75 post-sample-75)))

        {:prior-5 (histogram prior-sampler-5)
         :prior-75 (histogram prior-sampler-75)
         :posterior-5 (time (histogram post-sampler-5))
         :posterior-75 (time (histogram post-sampler-75))}))))

(defn setup []
  (reset! state
          {:data @all-data
           :plots (repeatedly 12 (partial plot2d (qa/current-applet) {:width 300 :height 300}))}))

(defn draw-plots [[omega theta0 theta1] data ^long x-position ^long y-position]
  (q/image (show (render-histogram omega data 2))
           x-position y-position)
  (q/image (show (render-histogram theta0 data 0))
           (+ x-position 20 (width omega)) y-position)
  (q/image (show (render-histogram theta1 data 1))
           (+ x-position 20 (+ (width omega) (width theta1))) y-position))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (q/background 0)
    (draw-plots (:plots @state) (:prior-5 @all-data) 0 0)
    (draw-plots (drop 3 (:plots @state)) (:posterior-5 @all-data) 0 320)
    (draw-plots (drop 6 (:plots @state)) (:prior-75 @all-data) 0 640)
    (draw-plots (drop 9 (:plots @state)) (:posterior-75 @all-data) 0 960)))

(defn display-sketch []
  (q/defsketch diagrams
    :renderer :p2d
    :size :fullscreen
    :display 2
    :setup setup
    :draw draw
    :middleware [pause-on-error]))
