(ns qftp.ftpl.core
  "This namespace provides FTPL compilation. FTPL is a DSL for the server written for higher-level scripting of FTP logic.
  Due to time constraints, it's only used for a few of the FTP responses. It exists here as proof-of-concept/basis
  for a better (i.e. compile time) implementation."
  (:refer-clojure :exclude [compile])
  (require [clojure.tools.logging :as log]
           [clojure.core.async :refer [>! <! go go-loop] :as async]
           [swiss.arrows :refer [-<> -!<>]])) ; wand threading macros

; NOTE: Define alternative set of FTPL command handlers for unit testing purposes.

(defn get-ftpl-command-fn
  "Given a body where the first argument is a keyword or symbol matching an FTPL command handler,
  returns a function that matches the command"
  [command-map body]
  (let [command (first body)
        command-fn (get command-map command)]
    (if (nil? command-fn) (throw (IllegalArgumentException. "Invalid FTPL command passed to compiler")))
    command-fn))

(defn session-eval
  "Performs a psuedo-eval over a compiled FTPL-LIST representation.
  It inserts the session and args as the second and third arguments to the function in the body
  and the arguments from the connection as the rest.
  NOTE: despite the name, eval is not actually called at any point in this code."
  [session args body]
  (let [new-body (map #(if (coll? %) (session-eval session args %) %) body)
        command  (first new-body)
        body-args (rest new-body)
        command-args (conj body-args args session)]
    (apply command command-args)))

(defn compile-ftpl-list
  "Given a list representing a single FTPL command with N arguments that may also be
  lists with FTPL commands, returns matching data structure with all FTPL keyword commands
  having been replaced by handler functions.
  Ex: '(:UPDATE-SESSION :user (:ARG 0)) becomes
      '(update-session-fn session args :user (get-arg 0))"
  [command-map body]
  ; If the first argument command is a keyword, replace it with the corresponding handler fn

  (-<> (if (symbol? (first body))
         (apply list (get-ftpl-command-fn command-map body) (rest body))
         body)

       ; Then recurse into the arguments if they're lists, otherwise return existing value
       ; TODO: should be a reduce. due to time constraints, leaving this as a map
       (map #(if (list? %) (compile-ftpl-list command-map %) %) <>)))

(defn compile
  "Given a map of FTPL keyword commands to functions handling that command
  and a coll containing one or more FTPL commands as lists wrapped by a coll,
  compiles them and returns a function that takes the session and and arguments
  and reduces it into an updated session, sending out appropriate FTP responses in the process."
  [command-map body]
  ; NOTE: The below session and arg bindings in the fn are used by the eval'd code
  ;       This is a kludge due to time constraints.
  ;       But unless you're fixing it, leave them alone.
  (fn [session args]
    (-<> (map #(compile-ftpl-list command-map %) body)
         (reduce #(session-eval %1 args %2) session <>))))
