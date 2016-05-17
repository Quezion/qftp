(ns qftp.file-system
  "Handles OS path and file-system interactions for the server."
  (require [me.raynes.fs :as fs]
           [clojure.string :as str]
           [swiss.arrows :refer [-<> -!<>]]
           [clojure.java.io :as io])
  (:import [java.io File])
  (:refer-clojure :exclude [list]))

;; TODO: refactor to use the Java Path class

(def session-directory
  "The directory that is the default working directory for every session"
  "/")

(defn- windows-absolute-path?
  [path]
  (and (> (count path) 1) (= (nth path 1) \:)))

(def path-char
  "The normalized path character"
   "/")

(defn normalize
  "Normalizes a file system dependent path"
  [path]
  (-<> (str/replace path "\\" "/")
       (drop-while #(not= % \/) <>)
       (apply str <>)))

(def server-jvm-directory
  "The JVM's working directory on the server's file system"
  (fs/expand-home fs/*cwd*))

(def jvm-directory
  "A normalized version of the server-jvm-directory"
  (normalize server-jvm-directory))

(def windows?
  "Whether the server should use windows path"
  (windows-absolute-path? server-jvm-directory))

(def windows-drive-root
  "The drive root of the windows based server, EX: C:
  Only capture when the JVM is launched, and is nil if not on windows."
  (if windows? (str (first server-jvm-directory) ":") nil))

(def server-path-char
  "The character the host OS uses for paths"
  (if windows? "\\" "/"))

(defn absolute-denormalized?
  "Returns whether a denormalized path is absolute"
  [path]
  (if windows?
    (cond
      (< 2 (count path))
      false  ; normalized windows path are at least 2 char, ie "C:"

      (= ":" (str (second path)))
      true

      :default false)))

(defn absolute?
  "Returns whether a normalized path is absolute"
  [path]
  (= (str (first path)) path-char))

(defn relative?
  "Returns whether a normalized path is relative"
  [path]
  (not (absolute? path)))

(defn denormalize-server-root-path
  "Given a normalized path,
  If the host OS uses something other than a path-char for the root of the path,
  this function will modify the root to contain the OS specific characters.
  It DOES NOT change the OS specific path chars, so it may return a string such as
  C:\\Data\\"
  [path]
  (cond
    windows? (str windows-drive-root path)
    ; Assumes UNIX paths, which would match FTP's spec
    :default path))

(defn- append-with-path-char
  [path arg path-char]
  (cond
    (str/blank? path)
    (throw (IllegalArgumentException. "Cannot append onto an empty path"))

    ; Handle case where we're appending an empty path onto a valid one
    (str/blank? arg) path

    ; throw exception if appending two absolute paths together
    (= path-char (str (first path)) (str (first arg)))
    (throw (IllegalArgumentException. "Cannot append two absolute paths"))

    ; appending a relative path on an absolute path that already has ending path char
    (= (str (last path)) path-char) (str path arg)

    ; appending a relative path on an absolute path with no path char
    :default (str path path-char arg)))

(defn append-denormalized
  "Performs OS dependent concatenation onto a denormalized path.
  Takes a string path and a string argument to append."
  [path arg]
  (append-with-path-char path arg server-path-char))

(def server-file-root
  "The directory that the FTP server's file listing is based out of"
  (append-denormalized server-jvm-directory "files"))

(defn make-relative
  "Given a normalized path, makes it relative by dropping the first path char.
  If the path is already relative, returns it."
  [path]
  (if (absolute? path)
    (apply str (rest path))
    path))

(defn denormalize-windows
  "Takes a normalized path and denormalizes it to windows"
  [path]
  ; because we're doing an append of the path to the
  ; absolute server path, we need to make it relative
  (-<> (make-relative path)
       (str/replace <> "/" "\\")
       (append-denormalized server-file-root <>)))

(defn denormalize-unix
  "Takes a normalized path and denormalizes it to unix. (Appends the server-file-root)"
  [path]
  (-<> (make-relative path)
       (append-denormalized server-file-root <>)))

; TODO: the denormalize call adds in the server root automatically
;       the normalize call does not strip the server root
;       refactor the server-root-add-in part of denormalization to be "localize"
(defn denormalize
  "Makes a normalized path specific to the host file system"
  [path]
  (if windows?
    (denormalize-windows path)
    (denormalize-unix path)))

(defn append
  "Performs OS independent concatenation onto a normalized path.
  Takes a string path and a string argument to append.
  The argument should be relative (e.g. not start with the path-char)"
  [path arg]
  (append-with-path-char path arg path-char))

(defn file
  "Given a normalized path, attempts to open a file on the server.
  Returns Java.IO.Socket"
  (^File [^String path]
   (io/file (denormalize path))))

(defn exists?
  "Given a normalized string path, checks if a file exists"
  [path]
  (.exists (file path)))

(defn mkdir
  "Given a normalized path creates that directory on the target filesystem.
  Returns true if the directory was created successfully, and false if the directory already exists."
  ([path]
   (let [file (file path)]
     (if (.exists file)
       false
       (do (.mkdir file) true)))))

(defn list
  "Takes a normalized directory path and returns a string coll of the directory's contents"
  [path]
  (let [file (file path)]
    (-<> (.list file)
         (seq <>)
         (into [] <>))))
