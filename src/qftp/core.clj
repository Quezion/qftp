(ns qftp.core
  "The core namespace provides basic functionality to handle incoming connections.
  It works asynchronously and hands the connections to the Channels namespace once they're established."
  (:gen-class)
  (:require [com.gearswithingears.async-sockets :refer [socket-server] :as asockets]
            [clojure.core.async :refer [>! <! go go-loop] :as async]
            [clojure.tools.logging :as log]
            [qftp.fsm :as fsm]
            [swiss.arrows :refer [-<> -!<>]] ; wand threading macros
            [qftp.channels :as channels]
            [qftp.file-system :as fs])
  (:import  [java.net Socket ServerSocket InetAddress InetSocketAddress]))

(def ftp-port 21)
(def start-message (str "qftp server starting on port " ftp-port))

(def default-server-data-port
  "The default FTP data port for the server is always the existing local port number
  to the client minus 1.
  Reference: RFC 959 3.2"
  (- ftp-port 1))

(defn- socket-client-ip
  [^Socket socket]
  "Given a Java Socket, returns the IP"
    (-<> (.getRemoteSocketAddress socket)
         (.getAddress <>)
         (.getHostAddress <>)))

(defn- socket-client-port
  [^Socket socket]
  "Given a Java Socket, returns the port"
  (-<> (.getRemoteSocketAddress socket)
       (.getPort <>)))

(defn construct-session
  [{:keys [socket] :as control-connection}]
  (atom {:user               nil
         :pass               nil
         :control-connection control-connection
         :data-connection    nil
         :data-server-port   default-server-data-port
         :data-dest-addr     (socket-client-ip   socket)
         :data-dest-port     (socket-client-port socket)
         :transfer-mode      "A"
         :fsm-state          fsm/begin-state
         :working-directory  fs/session-directory
         :processing?        false}))

(defn new-connection
  [connection]
  (log/info "New connection: " connection)
  (go (let [session (construct-session connection)]
        (log/info "Session initialized: " session)
        (>! channels/intro session))))

(defn setup-socket-server!
  [port]
  (let [server (socket-server port)]
    (go-loop []
      (when-let [connection (<! (:connections server))]
        (new-connection connection))
      (recur))))

(defn -main
  "Starts the FTP server and waits for connections."
  [& args]
  (log/info start-message)

  ; Ensure we have a "files" directory in wherever the JVM is running
  ; The reason is uses an empty string is because it's appended on the server-file-root
  (fs/mkdir "")
  (if-not (fs/exists? "") (throw (Exception. "Could not create files directory for server startup")))

  (setup-socket-server! ftp-port)
  (while true (Thread/sleep 10)))
