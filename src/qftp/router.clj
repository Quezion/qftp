(ns qftp.router
  "This namespace defines which commands are allowed in each of the state machine's states."
  (require [qftp.ftp.core :as ftp]
           [clojure.core.async :refer [>! <! go go-loop] :as async]
           [clojure.tools.logging :as log]
           [clojure.string :as str]
           [swiss.arrows :refer [-<> -!<>]])) ; wand threading macros

(def command-router
  "Contains a map of all states in the state machine.
  Each state contains a map representing its allowed commands and their handlers.
  A default exists for every state. This is used in case the user inputs an invalid command."
  {; Commands that may be executed prior to login
   :connection    {:AUTH    ftp/AUTH
                   :USER    ftp/USER
                   :SYST    ftp/SYST
                   :DEFAULT ftp/UNRECOGNIZED}
   :user          {:PASS    ftp/PASS
                   :DEFAULT ftp/UNRECOGNIZED}
   ; Commands that may be executed by a logged in user
   :authenticated {:MKD     ftp/MKD
                   :PWD     ftp/PWD
                   :CWD     ftp/CWD
                   :RMD     ftp/RMD
                   :DELE    ftp/DELE
                   ; TODO: NOOP

                   :PORT    ftp/PORT
                   :TYPE    ftp/TYPE
                   :LIST    ftp/LIST
                   :NLST    ftp/LIST
                   :RETR    ftp/RETR
                   :STOR    ftp/STOR

                   :BYE     ftp/QUIT
                   :QUIT    ftp/QUIT
                   :SYST    ftp/SYST

                   ; older clients still use these deprecated X aliases
                   :XPWD    ftp/PWD
                   :XMKD    ftp/MKD
                   :XCWD    ftp/CWD
                   :XRMD    ftp/RMD

                   :DEFAULT ftp/UNRECOGNIZED}

   ; Does nothing if the connection has been ended
   :ended         {:DEFAULT (fn [session & rest] session)}})

(defn get-command-handler
  "Given the keyword state of the FSM matching state-commands and a keyword representing a command,
  returns a function that performs the behavior associated with the command.
  In this case of an invalid command for that state, a default response is issued."
  [connection-state command]
  (let [allowed-commands (get command-router connection-state)
        default-command  (get allowed-commands :DEFAULT)]
    (-<> (str/upper-case command)
         (keyword <>)
         (get allowed-commands <> default-command))))
