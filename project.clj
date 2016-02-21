(defproject sf_clojure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
  	[org.clojure/clojure "1.7.0"]
  	[http-kit "2.1.18"]
  	[org.clojure/data.json "0.2.6"]
  	[aleph "0.4.1-beta4"]
  	; [stylefruits/gniazdo "0.4.1"]
  	[incanter "1.5.7"]
  ]
  :main ^:skip-aot sf-clojure.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
