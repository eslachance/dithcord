(defproject dithcord "0.1.0-SNAPSHOT"
  :description "A Discord Bot Library written in Clojure (which is a lisp, get it?)"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [cheshire "5.6.3"]
                 [clj-http "2.2.0"]
                 [stylefruits/gniazdo "1.0.0"]]
  :main ^:skip-aot dithcord.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
