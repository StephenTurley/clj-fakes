(ns unit.args-matcher
  #?@(:clj  [
             (:require
               [clojure.test :refer :all]
               [clj-fakes.core :as f]
               [clj-fakes.context :as fc]
               )]
      :cljs [(:require
               [cljs.test :refer-macros [is are testing]]
               [clj-fakes.core :as f :include-macros true]
               [clj-fakes.context :as fc :include-macros true]
               )
             ]))

(defn match?
  [matcher args]
  (is (satisfies? fc/ArgsMatcher matcher) "self test")
  (fc/args-match? matcher args))

(f/-deftest
  "function is an args matcher"
  (is (match? (constantly true) [11 22]))
  (is (match? #(= [11 22] %) [11 22]))

  (is (not (match? (constantly false) [1 2 3])))
  (is (not (match? #(= [11 22] %) [11 22 33])))
  (is (not (match? #(= [11 22] %) [11 25]))))

(f/-deftest
  "vector is an args matcher"
  (are [v args] (match? v args)
                [11 22] [11 22]
                [] nil
                [1 nil] [1 nil]
                [nil nil] [nil nil])

  (are [v args] (not (match? v args))
                [2 3] [2 3 4]
                [2 3 4] [2 3]
                [1 2] nil)

  (testing "vector elements can be functional arg matchers"
    (are [v args] (match? v args)
                  [100 even?] [100 2]
                  [100 even?] [100 10])

    (are [v args] (not (match? v args))
                  [100 odd?] [100 2]
                  [100 odd?] [100 10]))

  (testing "empty vector can be matched"
    (are [v args] (match? v args)
                  [] []
                  [[]] [[]]
                  [integer? []] [123 []]))
  )

(f/-deftest
  "f/any? matcher matches everything"
  (are [args] (match? f/any? args)
              nil
              [4]
              [3 2 4]))