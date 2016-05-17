(ns qftp.ftp.logic
  "This namespace contains FTPL definitions of FTP logic.")

; * If we had historical implementations of the FTP protocol, this namespace would be given
;   a name matching the protocol version, such as RFC "logic-rfc-959"
; * With that in mind, this namespace defines some logic that is technically
;   part of the Telnet protocol. Long term, it should be refactored out.
; FTP Logic Model
; The user's session is modeled in a single map denoting the sesssion
; Every FTP logic function interacts with the session and the command's args and returns it
; EX: (compiled-ftpl-command session args FTPL-ARGS)
;  (Note: Every function must return the session map.
;         Under the hood, this is a reduce operation.)

(def UNRECOGNIZED
  ['(SEND :command-unrecognized-500)])

(def PWD
  ['(SEND :working-directory-257)])

(def USER
  ['(UPDATE-SESSION :user (ARG 0))
   '(FSM-ADVANCE :user)
   '(SEND :password-required-331)])

(def PASS
  ['(UPDATE-SESSION :pass (ARG 0))
   '(FSM-ADVANCE :authenticated)
   '(SEND :login-success-230)])

(def QUIT
  ['(SEND :goodbye-221)
   '(FSM-ADVANCE :ended)])

(def SYST
  ['(SEND :syst-info-215)])

(def FEAT
  ['(SEND :features-211)
   '(SEND :end-211)])

; NOTE: FTPL commands not yet implemented
;       Due to time constraints, I am directly writing the rest of the handlers outside of FTPL
(def MKDIR
  ['(MKDIR (ARG 0))])

(def CWD
  ['(if (VALID-CWD? (ARG 0))
        (do
          (CWD (ARG 0))
          (SEND :cwd-success-250))
        (SEND :invalid-path-550))])

; Not yet used: technically this should be written as a conditional in the PASS FTPL command
; Right now we assume the user's credentials are valid and log them in
(def LOGIN-SUCCESS ['(FSM-ADVANCE :authenticated)
                    '(SEND :login-success-230)])

(def LOGIN-FAILURE ['(FSM-ADVANCE :connect)
                    '(SEND :invalid-credentials-530)])

; I noted that FileZilla would make AUTH TLS and AUTH SSL requests
; While not part of the FTP RFC, this is a simple and appropriate response.
(def AUTH ['(SEND :tls-not-allowed-502)])