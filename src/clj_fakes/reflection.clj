(ns clj-fakes.reflection
  (:require [clj-fakes.macro :refer :all]
            [clojure.reflect :as reflect]))

(defn -cljs-resolve
  "Alternative way to call cljs.analyzer/resolve-var.
  It's needed to be able to compile the library in the Clojure-only project."
  [env sym]
  ((ns-resolve 'cljs.analyzer 'resolve-var) env sym))

(defn -resolves?
  [env sym]
  (if (-cljs-env? env)
    ; ClojureScript
    (not (nil? (:meta (-cljs-resolve env sym))))

    ; Clojure
    (not (nil? (resolve sym)))))

(defn -resolve-protocol-with-specs
  "Returns a resolved protocol or nil if resolved object has no protocol specs."
  [env protocol-sym]
  (if (-cljs-env? env)
    ; ClojureScript
    (let [protocol (-cljs-resolve env protocol-sym)]
      (when-not (nil? (-> protocol :meta :protocol-info))
        protocol))

    ; Clojure
    (let [protocol (resolve protocol-sym)]
      (when (instance? clojure.lang.Var protocol)
        protocol))))

(defn -resolves-to-Object?
  [env sym]
  (if (-cljs-env? env)
    ; ClojureScript
    (= 'Object sym)

    ; Clojure
    (= Object (resolve sym))))

(defn -protocol-methods
  "Portable reflection helper. Returns different structures for different hosts.
  Protocol must be already resolved."
  [env protocol]
  (if (-cljs-env? env)
    ; ClojureScript
    (-> protocol :meta :protocol-info :methods)

    ; Clojure
    (-> protocol deref :sigs vals)))

(defn -protocol-method-name
  "Portable reflection helper."
  [env protocol-method]
  (if (-cljs-env? env)
    ; ClojureScript
    (first protocol-method)

    ; Clojure
    (:name protocol-method)))

(defn -protocol-method-arglist
  "Portable reflection helper."
  [env protocol-method]
  (if (-cljs-env? env)
    ; ClojureScript
    (second protocol-method)

    ; Clojure
    (:arglists protocol-method)))

(defn -reflect-interface-or-object
  "Raises an exception if cannot reflect on specified symbol."
  [env interface-sym]
  (assert (not (-cljs-env? env)) "Works only in Clojure for reflection on Java interfaces and Object class.")
  (if (-resolves-to-Object? env interface-sym)
    ; Object
    (reflect/reflect Object)

    ; Java interface?
    (try
      (reflect/type-reflect interface-sym)

      ; error
      (catch Exception e
        (assert nil (str "Unknown protocol or interface: " interface-sym
                         ". Underlying exception: " (pr-str e)))))))