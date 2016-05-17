# QFTP

A prototype FTP server. Minimally featured, but supports several basic FTP commands. See [router.clj](src/qftp/router.clj) for a list.

## Installation & Usage

**IMPORTANT:** The server will not run unless it can create a "files" folder in the JVM's working directory to act as the FTP server's file root.  
**DO NOT run it in a directory if there's a 'files' folder with important files.**

1. Download the [uberjar from here](http://quest.works/STORAGE/qftp-0.1.0-standalone.jar) and run

        java -jar qftp-0.1.0-standalone.jar

   You may need to set permissions on the jar or run as sudo. The server will exit if it can't create the files folder.   

2. Download the code from [the Github repo.](http://github.com/Quezion/qftp)
    
        lein run

To connect to the server, you can use the FTP commands on most systems. This client mimics a Telnet terminal for commands. See **Simple Session** down below for an example.
        
## Documentation

Marginalia documentation [is available here.](http://quest.works/STORAGE/qftp-0.1.0-doc.html)

Connect with your favorite FTP client. PASV mode not supported, and don't forget to set binary mode for non-ASCII transfers!

# FSM

The commands that the server accepts are defined in the router. They're validated by an [Automat](https://github.com/ztellman/automat) compiled finite state machine. The one in place is simple, but can be easily extended to enforce more complex server logic.

![Alt text](http://quest.works/STORAGE/qftp-0.1.0-fsm.png "Connection FSM Graph")

# Bugs

* The max filesize that can be transferred is 20-50 kb.
* The server is programmed to be async, but a suspected bug in the [async.sockets library](https://github.com/bguthrie/async-sockets) is causing it to not process new connections.
* You must force-quit the server in order to stop it.
* Using "../" to indicate "going up a folder" in a path works but results in strange working-directory paths on the server.

# Wishlist

1. Daemonizing the FTP service
2. Running the app from within a REPL (allowing for live app editing)
3. CIDER support and other nice Clojure "features"
4. Zach Tellman's "Manifold" library looked like a more elegant way to handle communication across sockets. With more time, I would've investigated this option more.
5. Pull the response codes out of each response and replace them with keyword :response-code. The response code would be inserted into the message.
6. Make the "session" map into a record
8. Refactor FTPL system to be macro based
9. Refactor qftp.file-system code to use Java Path class for more robust file-system handling
10. Refactor the "how side-effects are invoked" to be entirely separate from the FTP logic that decides WHICH side-effects to be invoked.
    The FTP logic should return a "list of side effects" that is executed by a mechanism that guarantees they will be executed only one time.
    This allows for a nearly bullet-proof handling of connection state while still executing asynchronously.

# Example Session

    quest@QLAPTOP-UBU> ftp      
      ftp> open localhost
      Connected to localhost.
      220-Pure FTP Server (Development)
      220 A Clojure Implementation on top of Java Sockets
      Name (localhost:quest): quest
      331 Password required for quest
      Password:
      230 Logged On
      Remote system type is JVM.
      ftp> ls
      200 Port command successful
      150 Opening data channel for directory listing
      transferred
      226 Successfully transferred /
      ftp> cd transferred
      250 CWD successful. "/transferred" is current directory.
      ftp> ls
      200 Port command successful
      150 Opening data channel for directory listing
      favicon.png fawhogoo.txt example.txt Warframe.jpg launchy.db
      226 Successfully transferred /transferred
      ftp> get favicon.png
      local: favicon.png remote: favicon.png
      200 Port command successful
      150 Opening data channel for file download from server of /transferred/favicon.png
      WARNING! 6 bare linefeeds received in ASCII mode
      File may not have transferred correctly.
      226 Successfully transferred favicon.png
      880 bytes received in 0.00 secs (356.2915 kB/s)
      ftp> binary
      200 Type set to I
      ftp> get favicon.png
      local: favicon.png remote: favicon.png
      200 Port command successful
      150 Opening data channel for file download from server of /transferred/favicon.png
      226 Successfully transferred favicon.png
      880 bytes received in 0.00 secs (768.6718 kB/s)
      ftp> ascii
      200 Type set to A
      ftp> get example.txt
      local: example.txt remote: example.txt
      200 Port command successful
      150 Opening data channel for file download from server of /transferred/example.txt
      226 Successfully transferred example.txt
      8 bytes received in 0.00 secs (4.8018 kB/s)
      ftp> put example.txt
      150 Opening data channel for file upload to server of example.txt
      226 Successfully transferred simple.txt
      8 bytes sent in 0.00 secs (4.8018 kB/s)
      ftp> bye
      221 Goodbye quest!         
    quest@QLAPTOP-UBU > ls
    favicon.png example.txt         


## License

Copyright © 2016 Quest Yarbrough

Distributed under the MIT license.
