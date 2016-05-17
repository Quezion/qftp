(ns qftp.data-connection
  "Provides operations for sending data over the server DTP of a session"
  (require [clojure.tools.logging :as log]
           [swiss.arrows :refer [-<> -!<>]]
           [com.gearswithingears.async-sockets :refer [socket-client] :as asockets]
           [clojure.core.async :refer [>! >!! <! go go-loop] :as async])
  (:import  [java.net Socket InetSocketAddress]
            [java.io InputStream File FileOutputStream]))

; BUG: Due to it simply trying to send the data and not catching the "port in use exception"
;      a client requesting two things over the data connection (out of order) would crash the server
;      A "real" solution to this is modeling an FSM for the data-connection
;      but that has not been done due to time constraints

(defn send-file
[^String address ^Integer port ^InputStream in length finished-callback]
  (let [socket (Socket.)
        inet-address (InetSocketAddress. address port)]
    (.connect socket inet-address)
    (go (if (.isConnected socket)
          (let [out           (.getOutputStream socket)
                buffer        (byte-array length)]
            ; TODO: refactor to use a buffered input stream and send incrementally in record mode
            (.read in buffer 0 length)
            (.write out buffer 0 length)
            (.close out)      ; also closes the socket
            (finished-callback))
          (throw (Exception. "Could not connect to data address for file transfer"))))))

(def ^:private socket-polling-rate
  "The delay between every read on the server DTP's socket when it's active"
  7)

(defn- delayer [in out delay-ms]
  (async/go-loop []
             (when-let [v (<! in)]
               (loop [batch [v] timeout-ch (async/timeout delay-ms)]
                 (let [[v ch] (async/alts! [in timeout-ch])]
                   (if (= in ch)
                     (recur (conj batch v) timeout-ch)
                     (>! out batch))))
               (recur))))

(def process-chan
  "When sockets visit this channel, they're. The go loop below pipes them into
  a channel that processes them"
  (async/chan))

(def recur-chan
  (async/chan))

; Whenever a value is put onto the delay-chan it will
; be returned to the process-chan after the socket-read-delay
(delayer recur-chan process-chan socket-polling-rate)

(async/go-loop []
  (when-let [v (<! recur-chan)]
    (>! process-chan v)
    (recur)))

(defn- copy-stream
  [{:keys [in out buffer callback]}]
  ; Read in and write out. It is assumed that .read will eventually fail
  ; TODO: There's not an actual catch. The handling here should be refactored.
  (go (try (let [length (.read in buffer)]
             (.write out buffer 0 length)
             (>! recur-chan out))
           (do (if (some? out) (.close out))
               (callback)))))

; An outputstream from a Java socket should be passed onto this channel
; It will perform nonblocking reads every socket-read-delay milliseconds
(async/go-loop []
           (when-let [v (<! process-chan)]
             (copy-stream v)
             (recur)))

(defn connect-retrieve
  "Given the string destination address and port, connects to the destination.
  Reads in all input sent from the destination and saves it to the passed File.
  The passed callback will be invoked after the file has finished processing and been saved."
  [^String address ^Integer port ^File file callback]
  (let [socket (Socket.)
        inet-address (InetSocketAddress. address port)]
    (.connect socket inet-address)
    (go (if (.isConnected socket)
          (let [in  (.getInputStream socket)
                out (FileOutputStream. file)
                buffer-size (.getReceiveBufferSize socket)
                wrapped-callback (fn [] (.flush out) (.close out) (callback))
                ]
            (go (>! process-chan {:in in :out out :buffer (byte-array buffer-size) :callback wrapped-callback})))
          (throw (Exception. "Could not connect to data address to receive requested file transfer"))))))