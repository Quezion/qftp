(ns qftp.channels
  "This namespace provides miscellaneous async wiring to ensure asynchronous connection handling."
  (:require [com.gearswithingears.async-sockets :refer [socket-server] :as asockets]
            [clojure.core.async :refer [>! <! go go-loop] :as async]
            [clojure.tools.logging :as log]
            [qftp.router :as router]
            [qftp.fsm :as fsm]
            [swiss.arrows :refer [-<> -!<>]] ; wand threading macros
            [clojure.string :as str]))         ; regex string split

(def intro-code
  "Indicates server has received connection"
  220)
(def server-name "Pure FTP Server (Development)")
(def server-motd "A Clojure Implementation on top of Java Sockets")

; when a command is received,
;  IF the fsm in the atom is in :authenticated, fsm goes from :authenticated to :processing in a swap
;  and the session + command is put into a "processing" channel

;  the "processing" channel derefs the atom and responds appropriately to the input
;  then sets the FSM in the atom back to :authenticated and performs a reset! to update the atom

(defn process-input
  "Given a line of input sent over the control connection,
  uses the current state of the FSM to find and execute a handler function for that command"
  [session input]
  (let [connection-state (fsm/state (:fsm-state session))
        split            (str/split input #"\s+")
        command          (first split)
        handler          (router/get-command-handler connection-state command)
        args             (rest split)]
    (handler session args)))

(def processing-chan (async/chan))
(go-loop []
  (when-let [event (<! processing-chan)]
      (-<> (process-input (:session event) (:input event))
           (assoc <> :processing? false)
           (reset! (:session-atom event) <>)))
  (recur))

(defn queue-for-processing
  [session session-atom input]
  (go (>! processing-chan {:session (assoc session :processing? true) :session-atom session-atom :input input})))

(defn connection-go-loop
  "When given a session-atom, wraps it in a go-loop that listens to it as long as it persists"
  [session-atom]
  (let [session-in-channel (:in (:control-connection (deref session-atom)))]
    (go-loop []
      (when-let [input (<! session-in-channel)]
        (log/info "Received message: " input)

        ; Note: this discards any commands that're received while one is processing
        (swap! session-atom #(if (:processing? %)
                              %
                              (queue-for-processing % session-atom input))))

      ; TODO: !!! this will create a recurring block for every connection
      ;       ensure it does not recur once the async channels for this session is closed
      (recur)
      )))

; Every time a connection comes in, it needs a new go-loop assigned to it
; Unlike the general ones on the server, the new go-loop only recurs as long as
; the connection is alive, otherwise it terminates.

(def intro
  "When a connection is passed onto this channel, it will send the server introduction to it."
  (async/chan))
(go-loop []
  (when-let [session-atom (<! intro)]
    (let [session (deref session-atom)]
      (>! (:out (:control-connection session)) (str intro-code "-" server-name))
      (>! (:out (:control-connection session)) (str intro-code " " server-motd))
      (connection-go-loop session-atom)))
  (recur))

