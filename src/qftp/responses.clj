(ns qftp.responses
  "This namespace contains response messages for the server. It defines a simple 'compilation' function
  that takes the session and the current command's args so that responses can refer to session state."
  (require [clojure.tools.logging :as log]))

(def opening-data-directory-150
  ["150 Opening data channel for directory listing"])

(def opening-data-upload-150
  ["150 Opening data channel for file upload to server of " :arg0])

(def opening-data-download-150
  ["150 Opening data channel for file download from server of " :arg0])

(def port-success-200
  ["200 Port command successful"])

(def type-set-200
  ["200 Type set to " :transfer-type])

(def features-211
  ["211-Features:\n"])

(def end-211
  ["211 End"])

(def syst-info-215
  ["215 JVM on an unknown system"])

(def goodbye-221
  ["221 Goodbye " :user "!"])

(def transfer-success-226
  ["226 Successfully transferred " :arg0])

(def logged-on-230
  ["230 Logged On"])

(def cwd-success-250
  ["250 CWD successful. \"" :working-directory "\" is current directory."])

(def removed-250
  ["550 \"" :arg0 "\" succesfully removed"])

(def mkdir-success-257
  ["257 \"" :arg0 "\" successfully created"])

(def working-directory-257
  ["257 \"" :working-directory "\" is current directory."])

(def password-required-331
  ["331 Password required for " :user])

(def command-unrecognized-500
  "Server response when presented with invalid command."
  ["500 Syntax error, command unrecognized."])

(def tls-not-allowed-502
  "Server response when queried about unsupported AUTH modes"
  ["502 Explicit TLS authentication not allowed"])

(def invalid-credentials-530
  ["530 Wrong username and/or password"])

(def directory-not-found-550
  ["550 CWD failed. \"" :arg0 "\" directory not found."])

(def rmd-failed-550
  ["550 Remove failed. " :arg0 "\" could not be deleted."])

(def directory-exists-550
  ["550 Directory already exists"])

(def invalid-path-550
  ["550 Invalid path"])

(def is-directory-550
  ["550 Operation failed because file at \"" :arg0 "\" is a directory"])

(def no-directory-550
  ["550 \"" :arg0 "\" No such directory"])

(def no-file-550
  ["550 \"" :arg0 "\" No such file"])

(def mkd-failed-550
  ["550 \"" :arg0 "\" Could not make directory"])

(def message-variable-keywords
  "Keys are the variable IDs allowed in responses.
  Values are functions that take the session and args and returns the value matching the ID."
  {:user (fn [session args] (get session :user))
   :pass (fn [session args] (get session :pass))
   :transfer-type (fn [session args] (get session :transfer-mode))
   :working-directory (fn [session args] (get session :working-directory))
   :arg0 (fn [session args] (nth args 0))
   :arg1 (fn [session args] (nth args 1))
   :arg2 (fn [session args] (nth args 2))})

(defn- populate-response
  "Maps over a coll and replaces keywords with strings using values from the session/args.
  If the keyword is unrecognized, does not change it."
  [response session args]
  (map (fn [part]
         (let [has-handler (and (keyword? part) (contains? message-variable-keywords part))]
           (if has-handler ((get message-variable-keywords part) session args) part)))
       response))

(defn realize
  "Given a seq representing a response, populates its variables and returns the string response"
  [response session args]
  (reduce str (populate-response response session args)))
