(ns unit.reify-nice-fake
  #?@(:clj  [
             (:require
               [clojure.test :refer :all]
               [unit.utils :as u]
               [clj-fakes.core :as f]
               [clj-fakes.context :as fc]
               [unit.fixtures.protocols :as p :refer [AnimalProtocol]]
               )]
      :cljs [(:require
               [cljs.test :refer-macros [is testing]]
               [clj-fakes.core :as f :include-macros true]
               [clj-fakes.context :as fc :include-macros true]
               [unit.fixtures.protocols :as p :refer [AnimalProtocol]])
             (:require-macros [unit.utils :as u])]))

(defprotocol LocalProtocol
  (bar [this] [this x y])
  (baz [this x])
  (qux [this x y z]))

(defn is-faked
  [method & args]
  ; strings are compared instead of values, because, presumably, 'lein test-refresh' plugin incorrectly reloads deftype
  ; and tests start failing on every change to contex.cljc
  (is (= #?(:clj  "clj_fakes.context.FakeReturnValue"
            :cljs "clj-fakes.context/FakeReturnValue")
         (pr-str (type (apply method args)))))

  (is (not= (apply method args)
            (apply method args))))

(u/-deftest
  "methods from same-namespace-protocol can be automatically faked"
  (f/with-fakes
    (let [foo (f/reify-nice-fake LocalProtocol)]
      (is-faked bar foo)
      (is-faked bar foo 1 2)
      (is-faked baz foo 100)
      (is-faked qux foo 1 2 3))))

(u/-deftest
  "method from fully-qualified protocol can be automatically faked"
  (f/with-fakes
    (let [cow (f/reify-nice-fake p/AnimalProtocol)]
      (is-faked p/speak cow))))

(u/-deftest
  "method from refered protocol can be automatically faked"
  (f/with-fakes
    (let [cow (f/reify-nice-fake AnimalProtocol)]
      (is-faked p/speak cow))))

(u/-deftest
  "works in explicit context"
  (let [ctx (fc/context)
        cow (fc/reify-nice-fake ctx AnimalProtocol)]
    (is-faked p/speak cow)))
;
;(u/-deftest
;  "method with arglists can be automatically reified even if one of arglists is faked explicitly"
;  (f/with-fakes
;    (let [foo (f/reify-nice-fake LocalProtocol
;                                 [bar :optional-fake [f/any? "bar"]])]
;      (is (= "bar" (bar foo)))
;      (is-faked bar foo 1 2))))

(u/-deftest
  "several protocols can be automatically reified"
  (f/with-fakes
    (let [cow (f/reify-nice-fake p/AnimalProtocol
                                 p/FileProtocol)]
      (is-faked p/speak cow)
      (is-faked p/speak cow 1)
      (is-faked p/speak cow 1 2)
      (is-faked p/eat cow 100 200)
      (is-faked p/sleep cow)

      (is-faked p/save cow)
      (is-faked p/scan cow))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Java interface
#?(:clj
   (u/-deftest
     "IFn cannot be automatically reified"
     (u/-is-exception-thrown
       java.lang.AbstractMethodError
       "n/a"
       #""
       (f/with-fakes
         (let [foo (f/reify-nice-fake clojure.lang.IFn)]
           (foo 1 2 3 4))))))

#?(:clj
   (u/-deftest
     "java.lang.CharSequence can be explicitly reified alongside automatically reified protocol"
     (f/with-fakes
       (let [foo (f/reify-nice-fake
                   java.lang.CharSequence
                   (charAt :fake [[100] \a])

                   p/FileProtocol)]
         (is (= \a (.charAt foo 100)))

         (is-faked p/save foo)
         (is-faked p/scan foo)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Object
(u/-deftest
  "Object/toString cannot be automatically reified"
  (f/with-fakes
    (let [foo (f/reify-nice-fake Object)]
      (is (not (instance? clj_fakes.context.FakeReturnValue (.toString foo)))))))

#?(:clj
   (u/-deftest
     "Object/toString can be explicitly reified alongside automatically reified protocol"
     (f/with-fakes
       (let [foo (f/reify-nice-fake Object
                                    (toString :recorded-fake [[] "bla"])

                                    p/FileProtocol)]
         (is (= "bla" (str foo)))
         (is (f/method-was-called "toString" foo []))

         (is-faked p/save foo)
         (is-faked p/scan foo)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;; integration
(u/-deftest
  "several protocols can be automatically reified and be partially explicitly faked"
  (f/with-fakes
    (let [cow (f/reify-nice-fake p/AnimalProtocol
                                 (sleep :recorded-fake [[] "zzz"])

                                 p/FileProtocol
                                 (scan :recorded-fake))]

      ; AnimalProtocol
      (is-faked p/speak cow)
      (is-faked p/speak cow 1)
      (is-faked p/speak cow 2 3)
      (is-faked p/eat cow 100 200)
      (is (= "zzz" (p/sleep cow)))

      ; FileProtocol
      (is-faked p/save cow)
      (p/scan cow)

      ; AnimalProtocol
      (f/method-was-called p/sleep cow [])

      ; FileProtocol
      (f/method-was-called p/scan cow []))))