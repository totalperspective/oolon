(defproject totalperspective/oolon "0.3.0-SNAPSHOT"
  :description "Bloom implementation for Clojure/ClojureScript leaning on Datomic datalog"
  :url "https://github.com/totalperspective/oolon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :test-paths ["test/clj"]
  :source-paths ["src/clj" "src/cljc"]
  :dependencies [[org.clojure/core.match "0.3.0-alpha4"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]
                                  [org.clojure/clojurescript "1.7.122"]
                                  [datascript "0.13.3"]
                                  [midje "1.8.2" :eclusions [org.clojure/clojure]]]
                   :plugins [[lein-cljsbuild "1.1.0"]
                             [lein-npm "0.6.1"]
                             [lein-midje "3.1.3"]
                             [lein-ancient "0.5.5"]
                             [lein-set-version "0.4.1"]]
                   :set-version
                   {:updates [{:path "README.md" :no-snapshot true}]}}})
