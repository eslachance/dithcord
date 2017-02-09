(defproject org.eslachance/dithcord "0.0.10"
  :description "A Discord Bot Library written in Clojure"
  :url "https://github.com/eslachance/dithcord"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [cheshire "5.6.3"]
                 [http.async.client "1.2.0"]
                 [org.slf4j/slf4j-simple "1.7.12"]
                 [aleph "0.4.1"]
                 [datascript "0.15.5"]]

  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]]
  :repl-options {
                 :init-ns dithcord.core
                 }
  ;:main ^:skip-aot dithcord.core
  ;:main ^:skip-aot dithcord.storage
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
