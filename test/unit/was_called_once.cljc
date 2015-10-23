(ns unit.was-called-once
  #?@(:clj  [
             (:require
               [clojure.test :refer :all]
               [clj-fakes.core :as f]
               [unit.was-called-fn-contract :refer :all]
               )]
      :cljs [(:require
               [cljs.test :refer-macros [is testing]]
               [clj-fakes.core :as f :include-macros true]
               [unit.was-called-fn-contract :refer [testing-was-called-fn-contract]]
               )
             ]))

(f/-deftest
  "contract"
  (testing-was-called-fn-contract f/was-called-once
                                  #"^Function was not called the expected number of times\. Expected: 1\. Actual: 0\."))

(f/-deftest
  "throws if function was called more than once"
  (f/with-fakes
    (let [foo (f/recorded-fake)]
      (foo)
      (foo 2)
      (f/-is-error-thrown
        #"^Function was not called the expected number of times\. Expected: 1\. Actual: 2\."
        (f/was-called-once foo [2])))))