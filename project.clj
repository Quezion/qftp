(defproject qftp "0.1.0"
  :description "A prototype FTP server written over a week in Clojure. Note: Does not support file transfers over ~max packet size."
  :url "https://github.com/quezion/qftp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins     [; Documentation generation tool. Can be run with "lein marg"
                ; NOTE: Temporarily using a fork due to an issue with Clojure 1.7.0. See https://clojars.org/michaelblume/marginalia
                [lein-marginalia "0.9.0"]
                ]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]

                 ; Using a non-canon version temporarily due to slow Clojure 1.8 support
                 [org.clojars.semperos/automat "0.2.0-alpha3"]
                 ;[automat "0.1.3"]

                 ; Provides binding to sockets via core.async channels
                 [com.gearswithingears/async-sockets "0.1.0"]

                 ; File system utility functions
                 [me.raynes/fs "1.4.6"]

                 ; Explicit theading macros (diamond wand)
                 [swiss-arrows "1.0.0"]]
  :main ^:skip-aot qftp.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
