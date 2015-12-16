(ns oolon.core
  (:refer-clojure :exclude [agent])
  (:require [oolon.agent :as a]
            [oolon.table :as t]
            [oolon.datalog :as d]
            [oolon.module :as m]))

;; Tables

(def table t/table)

(def scratch t/scratch)

(def channel t/channel)

(def loopback t/loopback)

(def input t/input)

(def output t/output)

;; Modules

(def module m/module)

;; Datalog

(def rule d/rule)

(def rule+ d/rule+)

(def rule- d/rule-)

(def rule+- d/rule+-)

(def rule> d/rule>)

;; Agents

(def agent a/agent)

(def start! a/start!)

(def tick! a/tick!)

(def state a/state)

(def +fact a/+fact)

(def out a/out)
