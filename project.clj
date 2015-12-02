(defproject oolon "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :test-paths ["test/clj"]
  :source-paths ["src/clj" "src/cljc"]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]
                                  [com.datomic/datomic-free "0.9.5327" :exclusions [joda-time]]
                                  [midje "1.7.0" :eclusions [org.clojure/clojure]]]
                   :plugins [[lein-cljsbuild "1.0.5"]
                             [lein-npm "0.6.1"]
                             [lein-midje "3.1.3"]
                             [lein-ancient "0.5.5"]]}})
