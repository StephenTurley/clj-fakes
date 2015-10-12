# Namespaces

The public API is split into two namespaces:

* `clj-fakes.core`
* `clj-fakes.context`

This is how you could require them in Clojure and ClojureScript:

```clj
; Clojure
(ns unit.example
  (:require
    [clj-fakes.core :as f]
    [clj-fakes.context :as fc]))

; ClojureScript
(ns unit.example
  (:require
    [clj-fakes.core :as f :include-macros true]
    [clj-fakes.context :as fc :include-macros true]))
```

These namespaces contain almost the same set of members. The difference 
is that `core` uses an implicit context and the `context` namespace
functions require an explicit context argument.

For your convenience functions which don't rely on a context can also be sometimes found in both namespaces (e.g. `f/any?` is the same as `fc/any?`).

The private/internal API uses a `-` prefix and should not be used (e.g. `-this-is-some-private-thing`).

# Context

Context is an object which stores all the information about
created fakes (recorded calls, positions in code, etc.).
All fakes have to be created inside some context.

To create a new context use `clj-fakes.context/context`:

```clj
; explicit context
(let [ctx (fc/context)
      foo (fc/recorded-fake ctx)]
  ; ...
)
```

Alternatively a new context can be created with 
`clj-fakes.core/with-fakes` macro:

```clj
; implicit context
(f/with-fakes
  ; note that now fake is created using a macro from core ns
  (let [foo (f/recorded-fake)]
    ; ...
))
```

This approach is preferable since it requires less typing, automatically 
unpatches all [patched vars](#monkey-patching) and executes [self-tests](#self-tests).

Internally `with-fakes` relies on a public dynamic variable `*context*` which can be 
used in your own helper functions.

# Function Fakes

Fake is a function which returns canned values on matched arguments and can optionally record its calls. It 
can be used to define and assert a behavior of an explicit functional dependency of an SUT (system under test).

## Fake

A regular fake function can be created using a macro:

`(f/fake config)`

`(fc/fake ctx config)`

[Config](#fake-configuration) is a vector which defines which values to return for different arguments:

```clj
(let [foo (f/fake [[1 2] "foo"
                   [3 4 5] "bar"])]
  (foo 1 2) ; => "foo"
  (foo 3 4 5)) ; => "bar"
```

If passed arguments cannot be [matched](#argument-matching) using specified 
config then the exception will be raised:

```clj
(foo 100 200) ; => raises "Unexpected args are passed into fake: (100 200)"
```

A fake is assumed to be called at least once inside the context; otherwise, [self-test](#self-tests) exception 
will be raised. In such case user should either modify a test, an SUT 
or consider using an [optional fake](#optional-fake):

```clj
(f/with-fakes
  (f/fake [[] nil])) ; => raises "Self-test: no call detected for: non-optional fake ..."
```

If your test scenario focuses on testing a behavior (e.g. "assert that foo was called by an SUT") then do not rely on self-tests, 
instead use [recorded fakes](#recorded-fake) with explicit [assertions](#assertions). 
Self-tests are more about checking usefulness of provided preconditions than 
about testing expected behavior.

## Optional Fake

`(f/optional-fake [config])`

`(fc/optional-fake ctx [config])`

It works the same as a regular fake but is not expected to be always called in the context:

```clj
(f/with-fakes
  (f/optional-fake [[1 2] 3])) ; => ok, self-test will pass
```

Such fakes should be used to express the intent of the test writer, 
for example, when you have to provide a dependency to an SUT,
but this dependency is not really related to the test case:
 
```clj
(defn process-payments
  "Processor requires a logger."
  [data logger]
  {:pre [(fn? logger)]}
  ; ...
  )

(deftest good-payments-are-processed-without-error
  (f/with-fakes
    (let [; ...
          ; we are not interested in how logger is going to be used, just stub it and forget
          fake-logger (f/optional-fake)]
      (is (= :success (process-payments good-payments fake-logger))))))
```

As you may have noticed, `config` argument can be omitted. 
In such case fake will be created with [`(default-fake-config)`](#fake-configuration) 
which allows any arguments to be passed on invocation.

## Recorded Fake

Invocations of this fake are recorded so that they can later be asserted:

`(f/recorded-fake [config])`

`(fc/recorded-fake ctx [config])`
 
Use `calls` function in order to get all recorded invocations for the specified 
recorded fake. 
It can also return all the recorded calls in the context if fake is not specified:

```clj
(let [foo (f/recorded-fake [[integer? integer?] #(+ %1 %2)])
      bar (f/recorded-fake [[integer? integer?] #(* %1 %2)])]
  (foo 1 2)
  (bar 5 6)
  (foo 7 8)
       
  (f/calls foo)
  ; => [{:args [1 2] :return-value 3}
  ;     {:args [7 8] :return-value 15}]

  (f/calls)
  ; => [[foo {:args [1 2] :return-value 3}]
  ;     [bar {:args [5 6] :return-value 30}]
  ;     [foo {:args [7 8] :return-value 15}]]
)
```

Recorded fake must be checked using one of the [assertions](#assertions) provided by the framework or
be marked as checked explicitly using `mark-checked` function; otherwise, self-test will raise an exception:

```clj
(f/with-fakes
  (f/recorded-fake)) ; => raises "Self-test: no check performed on: recorded fake ..."
```

```clj
(f/with-fakes
  (let [foo (f/recorded-fake)]
    (foo)
    (f/was-called foo []))) ; => ok, self-test will pass
```

```clj
(f/with-fakes
  (f/mark-checked (f/recorded-fake))) ; => ok, self-test will pass
```

## Custom Macros

In your own reusable macros you should use `fake*/recorded-fake*` 
instead of `fake/recorded-fake`:

`(f/fake* form config)`

`(fc/fake* ctx form config)`

`(f/recorded-fake* form [config])`

`(fc/recorded-fake* ctx form [config])`

In other words, your macro must explicitly provide `&form` to framework macros; 
otherwise, due to implementation details, framework will not be 
able to correctly determine fake function line numbers which is crucial for debugging. 

The framework will warn you if you accidentally use the version without asterisk 
in your macro.


# Fake Configuration

Fake config should contain pairs of argument matcher and return value:

```clj
[args-matcher1 fn-or-value1
args-matcher2 fn-or-value2 ...]
```

On fake invocation argument matchers will be tested from top to bottom and 
on the first match the specified value will be returned. If return value is a function than it will be called with passed arguments to generate the return value at runtime:

```clj
(let [foo (f/fake [[1 2] 100
                   [3 4] #(+ %1 %2)])]
  (foo 1 2) ; => 100
  (foo 3 4)) ; => 7
```

There's one built-in config in the framework:

`fc/default-fake-config`

It accepts any number of arguments and returns a new unique 
instance of protocol `fc/FakeReturnValue` on each call.
It is used by `optional-fake` and `recorded-fake` functions by default (i.e. when user 
doesn't specify the config explicitly).

# Argument Matching

Argument matcher must implement an `fc/ArgsMatcher` protocol:

```clj
(defprotocol ArgsMatcher
  (args-match? [this args] "Should return true or false."))
```

In most cases you won't need to create instances of this protocol manually 
because framework provides functional and vector matchers which are useful in most cases.

## Built-in matchers
### Functional matcher

Functional matcher is a function which takes a vector of call arguments and returns true/false. 
Example:

```clj
(let [foo (f/fake [#(odd? (count %)) "odd number of args"
                   #(even? (count %)) "even number of args"])]
  (foo 1 2) ; => "even number of args"
  (foo 1 2 3)) ; => "odd number of args"
```

It's actually implemented like this:

```clj
(extend-type #?(:clj  clojure.lang.Fn
                :cljs function)
  ArgsMatcher
  (args-match? [this args]
    (this args)))
```

### Vector matcher

Vector matchers were already used all other this guide, they looks like this:

```clj
[value-or-function1 value-or-function2 ...]
```

Let's look at the demo:

```clj
(let [foo (f/fake [[] "no args"
                   [[]] "empty vector"
                   [1 2] "1 2"
                   [integer?] "integer"
                   [str?] "string"])]
  (foo) ; => "no args"
  (foo []) ; => "empty vector"
  (foo 1 2) ; => "1 2"
  (foo 1 2 3) ; => exception: "Unexpected args are passed into fake: (1 2 3)"
  (foo 123) ; => "integer"
  (foo "hey")) ; => "string"
```

### any?

`(f/any? args)`
`(fc/any? args)`

This matcher always returns `true` for any input arguments:

```clj
(let [foo (f/fake [[1 2] "1 2"
                   [f/any? f/any? f/any?] "three args"
                   f/any? "something else"])]
  (foo) ; => "something else"
  (foo 1) ; => "something else"
  (foo 1 2) ; => "1 2"
  (foo 1 2 3) ; => "three args"
  (foo 1 2 3 4)) ; => "something else"
```

# Protocol Fakes

Framework defines two new macros for reifying protocols 
using function fakes described earlier. So, for example, 
you can record and assert method calls on reified instances.

The "strict" `reify-fake` macro is very similar to `reify`; in particular, 
created instance will raise an exception 
on calling protocol method which is not defined. 

On the other hand, `reify-nice-fake` is able to automatically 
generate [optional-fake](#optional-fake) implementations for methods which are 
not explicitly defined by user. 

Which macro to use solely depends on your testing style. I'd 
recommend to use nice fakes whenever possible in order to make 
tests more compact and break less often on code changes.

There are some subtleties, so here's a table to give you an overview of 
which features are currently supported:

Feature                                    | `reify-fake` | `reify-nice-fake` 
-                                          | -            | -            
Fake protocol method (explicitly)          | Yes          | Yes 
Fake protocol method (auto)                | -            | Yes
Support overloaded protocol methods        | Yes          | Yes
Fake Java interface method (explicitly)    | Yes          | Yes
Fake Java interface method (auto)          | -            | No
Fake Object method (explicitly)            | Yes          | Yes
Fake Object method (auto)                  | -            | No
Object can be reified with any new methods | No           | No

## Syntax

The syntax is very similar to the built-in `reify` macro:
 
`(f/reify-fake specs*)`

`(fc/reify-fake ctx specs*)`

`(f/reify-nice-fake specs*)`

`(fc/reify-nice-fake ctx specs*)`

Each spec consists of the protocol or interface name followed by zero
or more method fakes:

```clj
protocol-or-interface-or-Object
(method-name fake-type config)*
```

Available fake types:

* `:fake`
* `:optional-fake`
* `:recorded-fake`

As with function fakes, config can be omitted for `:optional-fake` and `:recorded-fake`:

```clj
(defprotocol AnimalProtocol
  (speak [this] [this name] [this name1 name2])
  (eat [this food drink])
  (sleep [this]))

(defprotocol FileProtocol
  (save [this])
  (scan [this]))

; ...

(f/reify-fake
  p/AnimalProtocol
  (sleep :fake [f/any? "zzz"])
  (speak :recorded-fake)
    
  p/FileProtocol
  (save :optional-fake)
  
  java.lang.CharSequence
  (charAt :recorded-fake [f/any? \a]))
```

While protocol methods always have a first `this` argument, 
method config must not try to match this argument (there is no sense to do it).
However, the return value function will receive all the arguments on invocation, 
including `this`:

```clj
(let [monkey (f/reify-fake p/AnimalProtocol
                           ; config only matches |food| and |drink| arguments
                           ; but return value function will get all 3 arguments on call
                           (eat :fake [[f/any? f/any?] #(str "ate " %2 " and drank " %3)]))]
      (println (p/eat monkey "banana" "water"))) ; => ate banana and drank water
```

## Calls & Assertions

In order to get and assert recorded method calls there's a 
helper function:

`(f/method obj f)`

`(fc/method ctx obj f)`

It can be used in combination with existing `calls` and `was-called-*` functions like this:
 
```clj
(f/with-fakes
  (let [cow (f/reify-fake p/AnimalProtocol
                          (speak :recorded-fake [f/any? "moo"]))]
    (p/speak cow)
    (println (f/calls (f/method cow p/speak))) ; => [{:args ..., :return-value moo}]
    (is (f/was-called-once (f/method cow p/speak) [cow]))))
```

Notice how object name `cow` is duplicated at the last line. In order to get 
rid of such duplications there are additional `method-was-called-*` functions defined. 
So the last expression can be rewritten like this:

```clj
(is (f/method-was-called-once cow p/speak []))
```

For the list of all available assertion functions see [Assertions](#assertions).

There's a quirk when Java interface method is faked: you will need to use its
string representation in `method`/`method-was-called-*`:

```clj
(let [foo (f/reify-fake clojure.lang.IFn
                        (invoke :recorded-fake))]
  (foo 1 2 3)
  (is (f/method-was-called foo "invoke" [1 2 3])))
```

## Custom Macros

In your own reusable macros you should use `reify-fake*/reify-nice-fake*` 
instead of `reify-fake/reify-nice-fake`:

`(f/reify-fake* form env specs*)`

`(fc/reify-fake* ctx form env specs*)`

`(f/reify-nice-fake* form env specs*)`

`(fc/reify-nice-fake* ctx form env specs*)`

In other words, your macro must explicitly provide `&form` and `&env` to framework macros; 
otherwise, due to implementation details, framework will not be 
able to correctly determine fake method line numbers which is crucial for debugging. 

For instance:

```clj
(defmacro my-reify-fake
  [& specs]
  `(f/reify-fake* ~&form ~&env ~@specs))
```

The framework will warn you if you accidentally use the version without asterisk 
in your macro.

# Assertions

Framework provides several assertion functions for [recorded fakes](#recorded-fake). 
Each function either returns `true` or raises an exception with additional details:

`(f/was-called-once f args-matcher)`
- checks that function was called strictly once and that the call was with the specified args.

`(f/was-called f args-matcher)`
- checks that function was called at least once with the specified args.

`(f/was-not-called f)`
- checks that function was never called.

The set of similar functions is defined for [protocol methods](#calls-assertions):

`(f/method-was-called-once obj f args-matcher)`

`(f/method-was-called obj f args-matcher)`

`(f/method-was-not-called obj f)`

Of course, all these functions can be called with an explicit context:

`(fc/was-called-once ctx f args-matcher)`

`(fc/was-called ctx f args-matcher)`

`(fc/was-not-called ctx f)`

`(fc/method-was-called-once ctx obj f args-matcher)`

`(fc/method-was-called obj ctx f args-matcher)`

`(fc/method-was-not-called ctx obj f)`

# Self-tests

Framework can perform "self-tests" in order to inform a user 
early on that some fakes (including protocol method fakes) are potentially used inappropriately.

If you use [`with-fakes`](#context) macro then self-tests will be run automatically on exiting the block.
Otherwise, when [explicit context](#context) is used, you have to invoke self-tests manually.

Currently two types of self-tests are supported to identify:
 
* unused fakes 
* unchecked fakes

## Unused Fakes

`(fc/self-test-unused-fakes ctx)`

This function raises an exception when some [fake](#fake) was never called after its creation.

For example, this self-test comes in handy when SUT stops using a dependency which 
was faked in several test scenarios. In such case the framework will guide you in cleaning 
 your test suite from the unused stubs.

## Unchecked Fakes

`(fc/self-test-unchecked-fakes ctx)`

This self-test raises an exception if some `recorded-fake` 
was never [marked checked](#recorded-fake), i.e. you forgot to assert its calls.

# Monkey Patching

You can temporarily change a variable value by using `patch!` macro:

`(f/patch! var-expr val)`

`(fc/patch! ctx var-expr val)`

After patching original value can still be obtained using a function:

`(f/original-val a-var)`

`(fc/original-val ctx a-var)`

Also don't forget to unpatch the variable to recover its original value:

`(f/unpatch! var-expr)`

`(fc/unpatch! ctx var-expr)`

Or unpatch all the variables inside the context at once:

`(f/unpatch-all!)`

`(fc/unpatch-all! ctx)`

If you use `with-fakes` then all variables will be unpatched 
automatically on exiting the block, for instance:

```clj
(f/with-fakes
  (f/patch! #'funcs/sum (f/fake [[1 2] "foo"
                                 [3 4] "bar"]))
  (is (= "foo" (funcs/sum 1 2)))
  (is (= "bar" (funcs/sum 3 4))))

; patching is reverted on exiting with-fakes block
(is (= 3 (funcs/sum 1 2)))
```

Another example is combining `patch` and `recorded-fake` in order
to create a *function spy* which works exactly the same as the original function
and also records its calls:

```clj
(f/patch! #'funcs/sum (f/recorded-fake [f/any? funcs/sum]))
```

Monkey patching is not thread-safe because it changes variable 
in all threads 
(underlying implementation uses [`alter-var-root`](https://clojuredocs.org/clojure.core/alter-var-root)/[`set!`](https://github.com/cljsinfo/cljs-api-docs/blob/catalog/refs/special/setBANG.md)).

# References
The API was mainly inspired by [jMock](http://www.jmock.org/) and [unittest.mock](https://docs.python.org/3/library/unittest.mock.html) frameworks with
design decisions loosely based on the ["Fifteen things I look for in an Isolation framework" by Roy Osherove](http://osherove.com/blog/2013/11/23/fifteen-things-i-look-for-in-an-isolation-framework.html).

Some alternative frameworks with isolation capabilities:

* [shrubbery](https://github.com/bguthrie/shrubbery)
* [clj-mock](https://github.com/EchoTeam/clj-mock)
* [Midje](https://github.com/marick/Midje)
* [speclj](https://github.com/slagyr/speclj)

Also take at look at the article ["Isolating External Dependencies in Clojure" by Joseph Wilk](http://blog.josephwilk.net/clojure/isolating-external-dependencies-in-clojure.html)
which discusses different approaches to mocking in Clojure.
 
For more detailed information about unit testing, TDD and test double patterns I'd recommend the books below:

* "Test Driven Development: By Example" by Kent Beck
* "Growing Object-Oriented Software, Guided by Tests" by Steve Freeman and Nat Pryce [[site](http://www.growing-object-oriented-software.com/)]
* "xUnit Test Patterns: Refactoring Test Code" by Gerard Meszaros [[site](http://xunitpatterns.com/)]