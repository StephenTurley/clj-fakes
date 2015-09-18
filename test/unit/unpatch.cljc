(ns unit.unpatch
  #?@(:clj  [
             (:require
               [clojure.test :refer :all]
               [clj-fakes.core :as f]
               [clj-fakes.context :as fc]
               )]
      :cljs [(:require
               [cljs.test :refer-macros [is testing]]
               [clj-fakes.core :as f :include-macros true]
               [clj-fakes.context :as fc :include-macros true]
               )
             ]))

(def my-var1 111)
(def my-var2 222)

(f/-deftest
  "var can be unpatched explicitly"
  (let [original-val my-var1]
    (f/with-fakes
      (f/patch! #'my-var1 200)
      (is (not= original-val my-var1) "self test")

      (f/unpatch! #'my-var1)
      (is (= original-val my-var1)))

    (is (= original-val my-var1) "just in case")))

(testing "unpatch works in explicit context"
  (let [ctx (fc/context)
        original-val my-var1]
    (fc/patch! ctx #'my-var1 200)
    (is (not= original-val my-var1) "self test")

    (fc/unpatch! ctx #'my-var1)
    (is (= original-val my-var1))))

(f/-deftest
  "var can be unpatched twice in a row"
  (let [original-val my-var1]
    (f/with-fakes
      (f/patch! #'my-var1 200)
      (is (not= original-val my-var1) "self test")

      (f/unpatch! #'my-var1)
      (f/unpatch! #'my-var1)
      (is (= original-val my-var1)))

    (is (= original-val my-var1) "just in case")))

(f/-deftest
  "var can be patched/unpatched several times"
  (let [original-val my-var1]
    (f/with-fakes
      (f/patch! #'my-var1 200)
      (is (not= original-val my-var1) "self test")
      (f/unpatch! #'my-var1)
      (is (= original-val my-var1))

      (f/patch! #'my-var1 300)
      (is (not= original-val my-var1) "self test")
      (f/unpatch! #'my-var1)
      (is (= original-val my-var1))

      (f/patch! #'my-var1 400)
      (is (not= original-val my-var1) "self test")
      (f/unpatch! #'my-var1)
      (is (= original-val my-var1)))

    (is (= original-val my-var1) "just in case")))

(f/-deftest
  "raises if key is not found"
  (f/with-fakes
    (f/-is-assertion-error-thrown
      #"^Assert failed: Specified var is not patched\n"
      (f/unpatch! #'my-var1))))

(f/-deftest
  "all vars can be unpatched at once"
  (let [original-val1 my-var1
        original-val2 my-var2]
    (f/with-fakes
      (f/patch! #'my-var1 200)
      (f/patch! #'my-var2 300)
      (is (not= original-val1 my-var1) "self test")
      (is (not= original-val2 my-var2) "self test")

      (f/unpatch-all!)
      (is (= original-val1 my-var1))
      (is (= original-val2 my-var2)))

    (is (= original-val1 my-var1) "just in case")
    (is (= original-val2 my-var2) "just in case")))

(testing "unpatch all works in explicit context"
  (let [ctx (fc/context)
        original-val my-var1]
    (fc/patch! ctx #'my-var1 200)
    (is (not= original-val my-var1) "self test")

    (fc/unpatch-all! ctx)
    (is (= original-val my-var1))))