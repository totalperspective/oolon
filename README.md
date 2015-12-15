# Oolon
[![Circle CI](https://circleci.com/gh/totalperspective/oolon.svg?style=svg&circle-token=5ba6245724fbcea9de4c13b7d58e035215929964)](https://circleci.com/gh/totalperspective/oolon)

> Now, it is such a bizarrely improbable coincidence that anything so
  mind-bogglingly useful could have evolved purely by chance that some
  have chosen to see it as the final proof of the NON-existence of
  God. The argument goes something like this:
>
> "I refuse to prove that I exist," says God, "for proof denies faith,
  and without faith I am nothing."
>
> "But," says Man, "the Babel fish is a dead giveaway, isn't it? It
  could not have evolved by chance. It proves that You exist, and so
  therefore, by Your own arguments, You don't. QED"
>
> "Oh dear," says God, "I hadn't thought of that," and promptly vanishes
  in a puff of logic.
>
> "Oh, that was easy," says Man, and for an encore goes on to prove that
  black is white and gets himself killed on the next zebra crossing.

Well, That About Wraps It Up For God - Oolon Colluphid
/
Hitchikers Guide to the Galaxy - Douglas Adams

## TL;DR

Oolon is a purely declarative language
for building systems
that manage state over time.
Based on the [Bloom](http://bloom-lang.net/) language
(developed by the [BOOM](http://boom.cs.berkeley.edu/) team at Berkeley UC)
it embraces the disorder in systems 
in both its semantics and execution.

Implemented as a data oriented DSL
for both Clojure and ClojureScript;
Oolon heavily leans on [DataScript](https://github.com/tonsky/datascript)
for its internal state
and operation.
The data orientated DSL means that
using Oolon in Java and JavaScript
is a simple matter of exchanging data
(except that, right now, it's a pain in the butt).

Oolon can be used to build
distributed systems,
user interfaces and
anything that involves dealing with ansynchrony.

## Clojure/ClojureScript

### Installation

#### Git

```sh
git clone https://github.com/totalperspective/oolon.git
cd oolon
lein install
```

#### Clojars

**Coming Soon**

#### Leiningen

```clojure
:require [[totalperspective/oolon "0.1.1"]
          [datascript "0.13.3"]]
```

### Usage

```clojure
(ns oolon-intro
  (:require [oolon.core :as o]))
```

To use Oolon you must
first define some modules,
then compose them together
into an agent.

#### Modules

A module is a collection of
related state (represented as tables)
some rules that define how they change
and other modules that they depend on.


```clojure
(def my-module
  (o/module
   :my-module
   [:state
    table1
    table2
    .
    .
    tableN
    :import
    module1
    module2
    .
    .
    moduleN
    :rules
    rule1
    rule2
    .
    .
    ruleM]))
```

##### Tables

Tables in Oolon are
semantically similar to
tables in traditional SQL databases.
They have a name and a schema:

```clojure
(def todos (o/table                 ;; Table type
            :todo-item              ;; Table name
            {:id :uuid}             ;; Key attributes
            {:content :string       ;; Value attributes
             :complete :boolean}))
```

The main difference is that
Oolon refers to the records in a table
as facts rather than rows and
the flieds as
attributes rather than columns.

Currently there are 3 distinct types of table:

*Persistent Tables*
`(table ...)`  
These tables persist facts until
they are explicitly deleted.

*Scratch Tables*
`(scratch ...)`  
These tables persist facts until
the start of the next execution step.

*Channels*
`(channel ...)`  
These represent communication outside of the agent.
Incoming facts have the same semantics as
facts in a scratch table.
Outgoing facts are placed in an output buffer for dispatch.

*Loopback*
`(loopback ...)` 
This is a special channel that
loop back a message to a module.

*Interfaces*
`(input ...)`
`(output ...)` 
These are scratch tables that allow
modules to communicate with each other.


##### Rules

Rules are use to define new facts
based on currently known facts.
Facts are added,
rules are triggered
and (potentially) new facts are added.
This process is repeated until
no more new rules are generated
and a fixed point is reached (quiescence).

Rules in Oolon 
have the same sematics as traditional
[Datalog](https://en.wikipedia.org/wiki/Datalog),
however, the syntax is based on
the [Datomic](http://datomic.com/) dialect of Datalog
(a tutorial can be found [here](http://www.learndatalogtoday.org/)).
In fact, rules in Oolon are converted
directly into Datomic Datalog queries
prior to execution.

As with traditional Datalog,
Oolon rules have a head and a body.
The head must match an existing table fact,
however, the body can be a mixture of
facts and
Datomic Datalog:

```clojure
(def make-path
  (o/rule
   [:path {:src :?src, :via :?via, :dst :?dst, :cost :?cost}]
   [[:link {:src :?src, :dst :?via, :cost :?link-cost}]
    [:path {:src :?via, :dst :?dst, :cost :?path-cost}]
    '[(+ ?link-cost ?path-cost) ?cost]]))
```

The above rule defines
the transative clojure of paths over links.
Notice that we don't need to use
all attributes of a fact in a rule
if we don't care about it.
Additionally we can
call any clojure function
and assign it's output to a variable.

**NOTE: To avoid unnecesarry quoting,
 both `:_` and `:?...` are
 converted to symbols.
 This will only work for facts (at present),
 hence the quoted form for the call to `+`.**

Currently there are 3 distinct types of rule:

**Instantaneous Insert**
`(rule ...)`  
When these rules fire
the resulting facts are added
to the target table
immediately.

**Deferred Insert**
`(rule+ ...)`  
When these rules fire
the resulting facts are added
to the target table
at the start of the next timestep.

**Asynchonous**
`(rule> ...)`  
When these rules fire
the resulting facts are added
to the target table
at the end of the current timestep
and cleared before the next.
These are the only way
to send facts to a channel.

#### Agents

Agents provide a way to
connect one or modules to
a database backend
and then manages
the execution of the modules.

```clojure
(ns oolon-intro
  (:require [oolon.core :as o]
            [oolon.db.datascript :as ds]))

(def module1 (o/module ...))
(def module2 (o/module ...))
(def my-schema ...)
(def conn (ds/create-conn my-schema))
(def agnt (o/agent :test conn
                   [module1
                    module2]))
(o/start! agnt)
```

##### Execution

The execution of an agent follows a simple loop:

 1. Add any new facts to the the agent.
 2. Tick!
 3. Dispatch any outgoing facts.
 4. Repeat.

Facts in and out take the same form as in rules:

```clojure
[:table-name {:attr1 val1 :attr2 val2}]
```

```clojure
(defn tick-agent! [agnt new-facts]
  (let [agnt (reduce o/+fact agnt new-facts)]
    (o/tick! agnt)
    [agnt (o/out agnt)]))

(defn run-agent [agnt]
  (loop [agnt agnt]
    (let [new-facts (get-new-facts ...)
          [agnt out] (tick-agent! agnt new-facts)]
      (dispatch out)
      (recur agnt))))
```

This can be run in a core.async go loop
or anything you like.

## Java/JavaScript

**Coming Soon**

## Datomic Backend

**Coming Soon**

## License

Copyright © 2015 Bahul Neel Upadhyaya

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
