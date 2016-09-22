(defproject dithcord "0.2.0-SNAPSHOT"
  :description "A Discord Bot Library written in Clojure (which is a lisp, get it?)"
  :url "https://github.com/eslachance/dithcord"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [cheshire "5.6.3"]
                 [http.async.client "1.2.0"]]
  :main ^:skip-aot dithcord.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
