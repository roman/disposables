> > Learn how to stop before you run.
> Old Proverb.

# disposables

A library inspired on Reactive Extension's Subscription/Disposable model.

It facilitates the composition and management of cleanup actions on
different components via a unique Interface/Protocol, allowing to
implement reload strategies in a sane way. This library works
perfectly in combination with
[tools.namespace](https://github.com/clojure/tools.namespace)

## Rationale

Most of times, whenever we create a component in our application we
need to create and keep track of resources that are being loaded. For
example a web application needs to deal with the cleanup of a Socket
and probably a DB connection.

In an ideal scenario, every one of this resources is managed by a
smaller component, say for example you have an application that
manages a XMPP connection and a WebServer. Each of this should be
managed by different components and your main application should just
compose them together. Disposables allow you to make this job easier
by allowing both the XMPP component and the WebServer component return
a cleanup record that talk in the same terms.

### Naive Approach

A lot of libraries ([http-kit](http://www.http-kit.org/) being one of
them) provide a cleanup function that is returned whenever a new
server is started. This work in a lot of cases, but if you want to
replicate this behavior with all your different components, you have
to:

1) Ensure that the side-effect of the cleanup function is idempotent
   (won't do crazy stuff if you run it more than once)

2) Ensure that exceptions are being logged whenever a cleanup function
   call fails

3) Keep a trace (either via logging, println, etc). That shows
   which dispose functions are being called.

### Getting Started with disposable

The best way to explain the usage of this library is with an example:

```clojure

(ns disposables.example
  (:require
   [disposables.core :refer [new-disposable dispose]]
   [org.httpkit.server :refer [run-server]])
  (:import
   [xmpp.library XMPPConnection]
   [java.net Socket]))

(defn init-xmpp [{:keys [server user password]}]
  (let [conn (XMPPConnection. server user password)]
    ;; do stuff with the XMPP connection here
    (new-disposable "xmpp-connection"
                    (.disconnect conn))))

(defn init-http [{:keys [port]}]
  (let [cleanup-action (run-server handler {:port port})]
    (new-disposable "http-connection"
                    (cleanup-action))))

(defn init-tcp-socket [{:keys address port}]
  (let [socket (Socket. addres port)]
    ;; do stuff with socket
    (new-disposable "tcp-connection"
                    (.close socket))))

(defn init-app [{:keys [xmpp tcp http]}]
  (let [xmpp-disposable (init-xmpp xmpp)
        http-disposable (init-http http)
        tcp-disposable  (init-tcp-socket tcp)]
    (-> xmpp-disposable
        (mappend http-disposable)
        (mappend tcp-disposable))))

```

From the previous example, you can tell every component of our app has
an `init-` function. Each of this functions returns a disposable
(created via `new-disposable`[1]). The `init-app` function is going to
compose all this sub-components disposables via the `mappend`[2]
function returning just a single `disposable`

---

[1] Note that new-disposable receives a description as its first
argument, this later will be used for debugging/tracing purposes

[2] `mappend` stands for
[Monoid](http://en.wikibooks.org/wiki/Haskell/Monoids#Introduction)
append

---

Following is what you would do with the returned disposable on a repl
session:

```clojure

(ns dev
  (:require
   [clojure.tools.namespace.repl :refer [refresh]]
   [disposables.example :refer [init-app]]))

(defonce app (atom nil))

(defn load-dev-config []
  ;; read your config from somewhere
  )

(defn init-app-dev []
  (if @app
    (println "App already loaded, call reload-app")
    (do
      (let [config (load-dev-config)]
        (reset! app (init-app config))))))

(defn reload-app []
  ;; if not nil, dispose all running code
  (when @app
    (println (verbose-dispose @app)))
  (refresh :after 'dev/init-app-dev))

```

In the `dev.clj` namespace we keep a single state atom that holds the
`disposable` of the app, on reload time, we call `verbose-dispose`
which in turn is going to call all the dispose actions of our
sub-components, shutting down and cleaning everything just before we
start a new server again after a call to `refresh`.

`verbose-dispose` prints the outcome of each dispose action, if the
disposable was successful, it is going to return the disposable
description and true, otherwise it is going to return the description
and the exception that happened. You can log this outcome for
tracing/debugging purposes.

## License

Copyright © 2015 Roman Gonzalez and collaborators.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
