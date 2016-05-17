(ns qftp.fsm
  "The FSM that defines valid transitions in the connections state.
  The one is use is rudimentary, but the FSM library (Automat) in use supports any valid state machine."
  (:require [automat.viz :as viz]
            [automat.core :as a]))

(def ^:private automat-representation [:connection :user :authenticated :ended])
(def ^:private schema (a/compile automat-representation))

(def state-keyword-indices
  "Maps the linear indices of each state to its corresponding keyword.
  Automat only provides the state index, so this is a workaround to lookup the current state."
  {0 :pre-connection
   1 :connection
   2 :user
   3 :authenticated
   4 :ended})

(def begin-state
  "The prepared FSM representing a connection's lifetime in the starting connection state"
  (a/advance schema nil :connection))

(defn advance
  "Attempts to advance the FSM's state with the specified input.
  If successful, returns the new fsm state.
  If the input is not allowed in this state, throws an IllegalArgumentException"
  [state input]
  (a/advance schema state input))

(defn state
  "Returns the current keyword state of the FSM"
  [fsm]
  (let [keyword-state (get state-keyword-indices (:state-index fsm) nil)]
     (if-not (keyword? keyword-state) (throw (IllegalStateException. "Connection FSM reached unrecognized state")))
      keyword-state))