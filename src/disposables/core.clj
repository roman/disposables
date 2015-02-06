(ns disposables.core)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General Protocols

(defprotocol Semigroup
  (mappend [self other]))

(defprotocol IDisposable
  (verbose-dispose [self]))

(defprotocol ISetDisposable
  (set-disposable [self other-disposable]))

(defprotocol IToDisposable
  (to-disposable [self]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Disposable API

(deftype Disposable [dispose-actions]
  IDisposable
  (verbose-dispose [self]
    (->> dispose-actions
         (mapv (fn -dispose [dispose-action] (dispose-action)))
         flatten))

  IToDisposable
  (to-disposable [self] self)

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

(defn new-disposable* [desc action]
  (let [dispose-result (atom nil)]
    (Disposable.
     [(fn -new-disposable []
        (when-not @dispose-result
          (try
            (when (compare-and-set! dispose-result nil {:description desc
                                                        :status :succeed})
              (action))
            (catch Exception e
              (reset! dispose-result
                      {:description desc
                       :status :failed
                       :exception e}))))
        @dispose-result)])))

(defmacro new-disposable [desc & body]
  `(new-disposable* ~desc (fn -new-disposable-macro [] ~@body)))

(def empty-disposable (Disposable. []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Single Assignment Disposable API

(def sad-not-initialized-error-value
  {:description "SingleAssignmentDisposable"
   :status :failed
   :exception (Exception. "SingleAssignmentDisposable was disposed without inner disposable")
   })

(def sad-double-assignment-error
  (Exception. "Double assignment on SingleAssignmentDisposable"))

(deftype SingleAssignmentDisposable [disposable-atom]
  ISetDisposable
  (set-disposable [_ inner-disposable]
    (when-not (compare-and-set! disposable-atom nil inner-disposable)
      (throw sad-double-assignment-error)))

  IToDisposable
  (to-disposable [self]
    (if @disposable-atom
      ;; when is set, this disposable is not
      ;; going to change, so we just return it
      @disposable-atom
      ;; when is not set, we need to return whatever
      ;; this dispose eventually will respond
      (Disposable. [#(verbose-dispose self)] )))

  IDisposable
  (verbose-dispose [_]
    (if @disposable-atom
      (verbose-dispose @disposable-atom)
      [sad-not-initialized-error-value])))

(defn new-single-assignment-disposable []
  (let [disposable-atom (atom nil)]
    (SingleAssignmentDisposable. disposable-atom)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Serial Disposable API

(def sd-empty-disposable-response
  {:description "Empty SerialDisposable"
   :status :succeed})

(deftype SerialDisposable [disposable-atom]
  ISetDisposable
  (set-disposable [_ inner-disposable]
    (when @disposable-atom (dispose @disposable-atom))
    (reset! disposable-atom inner-disposable))

  IToDisposable
  (to-disposable [self]
    (Disposable. [#(verbose-dispose self)]))

  IDisposable
  (verbose-dispose [_]
    (if @disposable-atom
      (verbose-dispose @disposable-atom)
      [sd-empty-disposable-response])))

(defn new-serial-disposable []
  (let [disposable-atom (atom nil)]
    (SerialDisposable. disposable-atom)))
