(ns qftp.ftpl.commands
  "This namespace defines implementation of FTPL commands."
  (:refer-clojure :exclude [send])
  (require [clojure.tools.logging :as log]
           [clojure.core.async :refer [>! <! go go-loop] :as async]
           [qftp.responses :as responses]
           [qftp.fsm :as fsm]
           [swiss.arrows :refer [-<> -!<>]]))    ; wand threading macros

(def message-ids
  "Maps message IDs that may be used in the send command to responses that can be cutomized with the session state."
  {:opening-data-directory-150 responses/opening-data-directory-150
   :opening-data-upload-150    responses/opening-data-upload-150
   :opening-data-download-150  responses/opening-data-download-150
   :port-success-200           responses/port-success-200
   :type-set-200               responses/type-set-200
   :features-211               responses/features-211
   :end-211                    responses/end-211
   :syst-info-215              responses/syst-info-215
   :goodbye-221                responses/goodbye-221
   :transfer-success-226       responses/transfer-success-226
   :login-success-230          responses/logged-on-230
   :cwd-success-250            responses/cwd-success-250
   :removed-250                responses/removed-250
   :mkdir-success-257          responses/mkdir-success-257
   :directory-not-found-550    responses/directory-not-found-550
   :directory-exists-550       responses/directory-exists-550
   :working-directory-257      responses/working-directory-257
   :password-required-331      responses/password-required-331
   :command-unrecognized-500   responses/command-unrecognized-500
   :tls-not-allowed-502        responses/tls-not-allowed-502
   :invalid-credentials-530    responses/invalid-credentials-530
   :invalid-path-550           responses/invalid-path-550
   :is-directory-550           responses/is-directory-550
   :no-directory-550           responses/no-directory-550
   :rmd-failed-550             responses/rmd-failed-550
   :mkd-failed-550             responses/mkd-failed-550
   :no-file-550                responses/no-file-550})

(defn compile-message
  "Given the session, the args, and a keyword matching a message,
  returns a string response matching the keyword and appropriate to the session."
  [session args id]
  (let [message (get message-ids id)]
    (if message
      (responses/realize message session args)
      (throw (IllegalArgumentException. "FTPL Message Error: specified message ID could not be resolved to string")))))

(defn send
  "Given the session map and a message-id keyword,
  sends out the matching string message over the outbound connection from the session.
  If the second argument is not a keyword or more than two arguments were passed,
  throws an exception."
  [session args message-id]
  ; Get the async channel representing the "out" on the connection and
  (let [message    (compile-message session args message-id)]
    (go (>! (get-in session [:control-connection :out]) message))
    (log/info "Sent message: " message))
  session)

(defn fsm-advance
  "Advances the FSM. Does not provide any error handling, so the FTP logic
  attempting to shift to an invalid state will throw an error and crash the application."
  [session args state]
  ; TODO: cleaner handling would be catching it and immediately ending this connection.
  (-<> (fsm/advance (:fsm-state session) state)
       (assoc session :fsm-state <>)))

(defn update-session
  "Updates the session with the specified key."
  [session args key value]
  (assoc session key value))

(defn arg
  "Returns the argument to the command 0 inclusive"
  [session args n]
  (nth args n))

(def command-map
  "Maps keyword FTPL commands to their appropriate handling functions"
  {'SEND send
   'FSM-ADVANCE fsm-advance
   'UPDATE-SESSION update-session
   'ARG arg})
