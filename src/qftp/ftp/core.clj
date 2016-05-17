;; This namespace contains the functions that handle FTP logic functionality within the server.
;; Each function takes two arguments, a map representing the user's ongoing session
;; and the string arguments sent to the FTP command. This is an artifact of the FTP DSL
;; where the session was threaded through the scripted commands.

(ns qftp.ftp.core
  (require [qftp.ftp.logic :as logic]
           [qftp.ftpl.core :as ftpl]
           [qftp.ftpl.commands :as commands]
           [qftp.data-connection :as data-connection]
           [clojure.tools.logging :as log]
           [swiss.arrows :refer [-<> -!<>]]
           [qftp.file-system :as fs]
           [clojure.string :as str])
  (:import [java.io FileInputStream ByteArrayInputStream]
           [java.nio.charset.StandardCharsets]))

(def EOF
  "A coll of bytes representing an FTP EOF the first and second bytes for an FTP EOF.
  RFC 3.4.1 - STREAM MODE"
  [0xFF 0x1])

(def EOR
  "A coll of bytes representing an FTP EOR. Used when transferring record data.
  Reference: RFC 3.4.1 - STREAM MODE"
  [0xFF 0x2])

(def EOR-EOF
  "A coll of bytes representing an FTP EOR followed by an EOF.
  This sequence establishes both with fewer bytes.
  Reference: RFC 3.4.1 - STREAM MODE"
  [0xFF 0x2])

(def CRLF
  "The sequence that represents the carriage return line feed for FTP.
  Note: This varies from terminals from just LF to CR-NULL (telnet),
  so it has been explicitly noted here."
  [0x0D 0x0A])

(def SYST
  "Responds to inquiries about the system
   Reference: RFC 959 4.1.3 - FTP SERVICE COMMANDS"
  (ftpl/compile commands/command-map logic/SYST))

(def USER
  "Reponds to transmission of USER login by storing it in the session and shifting the FSM.
   Reference: RFC 959 4.1.1 - ACCESS CONTROL COMMANDS"
  (ftpl/compile commands/command-map logic/USER))

(def PASS
  "Reponds to transmission of user PASS by storing it in the session and shifting the FSM.
   Reference: RFC 959 4.1.1 - ACCESS CONTROL COMMANDS"
  (ftpl/compile commands/command-map logic/PASS))

(def QUIT
  "Responds to transmission of QUIT by logging the user out.
   Reference: RFC 959 4.1.1 - ACCESS CONTROL COMMANDS"
  (ftpl/compile commands/command-map logic/QUIT))

(def PWD
  "Prints the working directory.
   Reference: RFC 959 4.1.3 - FTP SERVICE COMMANDS"
  (ftpl/compile commands/command-map logic/PWD))

(defn- ensure-absolute
  "Given the working directory and a path, ensures the path is absolute
  by prepending the working directory if it's not"
  [working-directory path]
  (if (fs/relative? path)
    (fs/append working-directory (str path))
    path))

(defn MKD
  "Makes the directory specified the path.
  Reference: RFC 959 4.1.3 - FTP SERVICE COMMANDS"
  [{:keys [working-directory] :as session} [path-arg :as args]]
  (let [path (ensure-absolute working-directory path-arg)
        file (fs/file path)]
  (if (.exists file)
    (commands/send session args :directory-exists-550)
    (if (.mkdir file) (commands/send session [path] :mkdir-success-257)
                      (commands/send session [path] :mkd-failed-550)))))

(defn RMD
  "Removes the directory at the specified path.
  Reference: RFC 959 4.1.3 FTP SERVICE COMMANDS"
  [{:keys [working-directory] :as session} [path-arg :as args]]
  (let [path (ensure-absolute working-directory path-arg)
        file (fs/file path)]
    (if (.exists file)
      (if (.isDirectory file)
        (if (.delete file)
          (commands/send session [path] :removed-250)
          (commands/send session [path] :rmd-failed-550))
        (commands/send session [path] :no-directory-550))
      (commands/send session [path] :no-directory-550))
    session))

(defn CWD
  "Changes the working directory to the specified path.
  Reference: RFC 959 4.1.1 ACCESS CONTROL COMMANDS"
  [{:keys [working-directory] :as session} [path-arg :as args]]
  (let [path (ensure-absolute working-directory path-arg)
        file (fs/file path)]
    (if (.exists file)
      (if (.isDirectory file)
        (commands/send (assoc session :working-directory path) args :cwd-success-250)
        (commands/send session [path] :no-directory-550))
      (commands/send session args :directory-not-found-550))))


; All of the below code is part of PORT. It's to deal with the port number
; specificed in the PORT command. It has to be hexified, concatenated, and turned into a decimal.
; See: www.securitypronews.com/understanding-the-ftp-port-command-2003-09

(defn- parse-int [s]
  "Given a string representing a decimal number, parses it into a string"
  (Integer/parseInt (re-find #"\A-?\d+" s)))

(defn- decimal-to-hex
  "Given a decimal number, returns a hex representation
  Note: this might not prepend the appropriate character depending on the hex representation"
  [n]
  (format "%02x" n))

(defn- hex-to-decimal
  "Given a string representing a hex number, parses it into a decimal number"
  [s]
  (-<> (Integer/parseInt s 16)
       (format "%2d" <>)
       (parse-int <>)))

(defn tcp-port-from-uints
  "Given two numerical Clojure strings of 3 characters or less,
  converts them into a matching TCP port.
  WARNING: This could produce output bigger than the largest allowed TCP port if
  the Clojure numbers are too large."
  [str-uint1 str-uint2]
  (-<> (str (decimal-to-hex (parse-int str-uint1)) (decimal-to-hex (parse-int str-uint2)))
       (hex-to-decimal <>)))

(defn PORT
  "Establishes a specific port that the server should connect to in order to send data.
  This changes it from the default one calculated automatically by the server and client.
  Note that this is only used when the data connection is in ACTIVE mode.
  Reference: RFC 959 4.1.1"
  [session args]
  ; NOTE: (first args) is a string sequence of numbers, comma delimited
  ;       "192,168,1,2,246,92"
  ;       the first four numbers are the dest data address, the second two denote the dest port

  (let [arg-split (str/split (first args) #"[,]")]
    (if (not= 6 (count arg-split))
      (commands/send session args :command-unrecognized-500)
      (let [dest-addr (apply str (interpose "." (take 4 arg-split)))
            ; The specified data TCP port. The endpoint will listen here for a connection from the server.
            dest-port (apply tcp-port-from-uints (take-last 2 arg-split))
            updated-session (assoc session :data-dest-addr (apply str dest-addr) :data-dest-port dest-port)]
        (commands/send updated-session args :port-success-200)))))

(defn- append-crlf-to-array
  "Given a byte array, appends the two EOR bytes to it and returns
  a new array of input-length + 2 elements"
  [bytes length]
  (let [new-len (+ length 2)
        new-arr (byte-array new-len)]
  (System/arraycopy bytes 0 new-arr 0 length)
  (aset-byte new-arr (- new-len 2) (unchecked-byte (first CRLF)))
  (aset-byte new-arr (- new-len 1) (unchecked-byte (second CRLF)))
  new-arr))

(defn LIST
  "Retrieves a listing of the specified directory.
  Reference: RFC 959 4.1.3 - FTP SERVICE COMMANDS"
  [{:keys [working-directory data-dest-addr data-dest-port] :as session} [path-arg :as args]]
  (let [path (ensure-absolute working-directory path-arg)
        listing (fs/list path)
        str-listing (apply str (interpose " " listing))
        str-bytes (.getBytes str-listing java.nio.charset.StandardCharsets/US_ASCII)
        str-bytes-length (count str-bytes)

        ; Note: hidden dependency here. append-crnull-to-array should be refactored.
        in-buffer (append-crlf-to-array str-bytes str-bytes-length)
        in-length (+ str-bytes-length (count CRLF))

        in (ByteArrayInputStream. in-buffer 0 in-length)
        callback #(commands/send session [path] :transfer-success-226)]
    (commands/send session args :opening-data-directory-150)
    (data-connection/send-file data-dest-addr data-dest-port in in-length callback)
    session))

(defn RETR
  "Request for file transfer. Sends the file over the data connection.
  Reference: RFC 959 4.1.3 - FTP SERVICE COMMANDS"
  [{:keys [working-directory data-dest-addr data-dest-port] :as session} [path-arg :as args]]
  (let [path (ensure-absolute working-directory path-arg)
        file (fs/file path)]
    (if-not (.exists file)
      (commands/send session args :invalid-path-550)
      (let [length  (.length file)
            in      (FileInputStream. file)]
        (commands/send session [path] :opening-data-download-150)
        (data-connection/send-file data-dest-addr data-dest-port in length
                                   #(commands/send session args :transfer-success-226))))
    session))

(defn TYPE
  "Sets the transfer mode.
   REFERENCE: RFC 4.1.2 - TRANSFER PARAMETER COMMANDS"
  [session args]
  (-<> (assoc session :transfer-mode (first args))
       (-!<> (commands/send <> args :type-set-200))))

(defn STOR
  "Stores a file from the remote connection.
  Reference: RFC 959 4.1.3 - FTP SERVICE COMMANDS"
  [{:keys [working-directory data-dest-addr data-dest-port] :as session} [path-arg :as args]]
  (let [path (ensure-absolute working-directory path-arg)
        file (fs/file path)]
    (if (.isDirectory file)
      (commands/send session args :is-directory-550)
      (do (commands/send session args :opening-data-upload-150)
          (data-connection/connect-retrieve data-dest-addr data-dest-port file
            #(commands/send session args :transfer-success-226))))
    session))

(defn DELE
  "Removes a file
  Reference: RFC 959 4.1.3 - FTP SERVICE COMMANDS"
  [{:keys [working-directory] :as session} [path-arg :as args]]
  (let [path (ensure-absolute working-directory path-arg)
        file (fs/file path)]
    (if (.exists file)
      (if (.isDirectory file)
        (commands/send session [path] :is-directory-550)
        (do (if (.delete file)
              (commands/send session [path] :removed-250)
              (commands/send session [path] :rmd-failed-550))))
      (commands/send session [path] :no-file-550))
    session))

(def AUTH
  "A function that takes the session and a collection of arguments and responds to
  a request for authentication.
  EX: (auth-handler session args)"
  (ftpl/compile commands/command-map logic/AUTH))

(def UNRECOGNIZED
  "Responds to unrecognized commands"
  (ftpl/compile commands/command-map logic/UNRECOGNIZED))