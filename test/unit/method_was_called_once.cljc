(ns unit.method-was-called-once
  #?@(:clj  [
             (:require
               [clojure.test :refer :all]
               [clj-fakes.core :as f]
               [unit.method-was-called-fn-contract :refer :all]
               [unit.fixtures.protocols :as p])]
      :cljs [(:require
               [cljs.test :refer-macros [is testing]]
               [clj-fakes.core :as f :include-macros true]
               [unit.method-was-called-fn-contract :refer [testing-method-was-called-fn-contract]]
               [unit.fixtures.protocols :as p]
               )
             ]))

(f/-deftest
  "contract"
  (testing-method-was-called-fn-contract f/method-was-called-once
                                         #"^Function was not called the expected number of times\. Expected: 1\. Actual: 0\."))

(f/-deftest
  "throws if function was called more than once"
  (f/with-fakes
    (let [cow (f/reify-fake p/AnimalProtocol
                            (speak :recorded-fake))]
      (p/speak cow)
      (p/speak cow 2)
      (f/-is-error-thrown
        #"^Function was not called the expected number of times\. Expected: 1\. Actual: 2\."
        (f/method-was-called-once cow p/speak [2])))))