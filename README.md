# QFTP

A prototype FTP server. Minimally featured, but supports several basic FTP commands. See [router.clj](src/qftp/router.clj) for a list.

## Installation

Download from http://example.com/FIXME.

## Usage

    $ lein run

Connect with your favorite FTP client. PASV mode not supported, and don't forget to set binary mode for non-ASCII transfers!

# FSM

The commands that the server accepts are defined in the router. They're validated by an [Automat](https://github.com/ztellman/automat) compiled finite state machine. The one in place is simple, but can be easily extended to enforce more complex server logic.

![Alt text](http://i.imgur.com/TcJJtlH.png "FSM Graph")

## License

Copyright © 2016 Quest Yarbrough

Distributed under the MIT license.
