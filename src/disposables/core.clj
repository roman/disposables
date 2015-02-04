(ns disposables.core)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General Protocols

(defprotocol Semigroup
  (mappend [self other]))

(defprotocol IDisposable
  (verbose-dispose [self]))

(defprotocol ISetDisposable
  (set-disposable [self other-disposable]))

(deftype Disposable [dispose-actions]
  IDisposable
  (verbose-dispose [self]
    (mapv (fn -dispose [dispose-action] (dispose-action))
          dispose-actions))

  Semigroup
  (mappend [_ other]
    (Disposable.
     ;; compose disposables in reverse order
     (reduce conj
             (.dispose-actions other)
             dispose-actions))))

(defn dispose [disposable]
  (verbose-dispose disposable)
  nil)

(defn merge-disposable [disposable & other]
  (reduce mappend disposable other))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Disposable API

(defn new-disposable* [desc action]
  (let [dispose-result (atom nil)]
    (Disposable.
     [(fn -new-disposable []
        (if @dispose-result
          @dispose-result
          (try
            (action)
            (reset! dispose-result [desc true])
            (catch Exception e
              (reset! dispose-result [desc e])))))])))

(defmacro new-disposable [desc & body]
  `(new-disposable* ~desc (fn -new-disposable-macro [] ~@body)))

(def empty-disposable (Disposable. []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Single Assignment Disposable API

(def sad-not-initialized-error
  ["SingleAssignmentDisposable"
   (Exception. "SingleAssignmentDisposable was disposed without inner disposable")])

(def sad-double-assignment-error
  (Exception. "Double assignment on SingleAssignmentDisposable"))

(deftype SingleAssignmentDisposable [disposable-atom]
  ISetDisposable
  (set-disposable [_ inner-disposable]
    (if @disposable-atom
      (throw sad-double-assignment-error)
      (reset! disposable-atom inner-disposable)))

  IDisposable
  (verbose-dispose [_]
    (if @disposable-atom
      (verbose-dispose @disposable-atom)
      [sad-not-initialized-error])))

(defn new-single-assignment-disposable []
  (let [disposable-atom (atom nil)]
    (SingleAssignmentDisposable. disposable-atom)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Serial Disposable API

(def sd-empty-disposable-response
  ["Empty SerialDisposable" true])

(deftype SerialDisposable [disposable-atom]
  ISetDisposable
  (set-disposable [_ inner-disposable]
    (if @disposable-atom
      (do (dispose @disposable-atom)
          (reset! disposable-atom inner-disposable))
      (reset! disposable-atom inner-disposable)))

  IDisposable
  (verbose-dispose [_]
    (if @disposable-atom
      (verbose-dispose @disposable-atom)
      [sd-empty-disposable-response])))

(defn new-serial-disposable []
  (let [disposable-atom (atom nil)]
    (SerialDisposable. disposable-atom)))
