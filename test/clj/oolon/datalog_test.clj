(ns oolon.datalog-test
  (:require [oolon.datalog :refer :all]
            [midje.sweet :refer :all]))

(facts "About val->sym"
       (tabular
        (fact "Basic types are un touched"
              (val->sym ?val) => ?val)
        ?val
        1
        "two"
        :three
        'four
        #inst "2010")
       (tabular
        (fact "Special keywords are turned into symbols"
              (val->sym ?val) => ?sym)
        ?val  ?sym
        :_    '_
        :?s   '?s))

(facts "About rel->eavt"
       (rel->eavt :link nil) => nil
       (rel->eavt :link {}) => nil
       (rel->eavt :link {:src 1}) => '[[?link :link/src 1]]
       (rel->eavt :link#1 {:src 1}) => '[[?link1 :link/src 1]]
       (rel->eavt :link {:src :?src}) => '[[?link :link/src ?src]]
       (rel->eavt :link {:src :src :_ :dst}) => '[[?link :link/src :src]
                                                  [?link _ :dst]]
       (rel->eavt :link {:src :?src :dst :?dst} 1) =>  '[[?link :link/src ?src 1]
                                                         [?link :link/dst ?dst 1]]
       (rel->eavt :link {:src :?src :dst :dst} :?tx) =>  '[[?link :link/src ?src ?tx]
                                                           [?link :link/dst :dst ?tx]])

(facts "About generating queries"
       (query) => nil

       (query [:link {:src 1}])
       =>
       '[[?link :link/src 1]]

       (query [:link#1 {:src 1}]
              [:link#2 {:src 2}])
       =>
       '[[?link1 :link/src 1]
         [?link2 :link/src 2]]

       (query [:link {:src :?src :dst :?via}]
              [:path {:src :?via :dst :?dst}])
       =>
       '[[?link :link/src ?src]
         [?link :link/dst ?via]
         [?path :path/src ?via]
         [?path :path/dst ?dst]]

       (query [:link {:src :?src :dst :?via :cost :?c1}]
              [:path {:src :?via :dst :?dst :cost :?c2}]
              '[(+ ?c1 ?c2) ?cost])
       =>
       '[[?link :link/src ?src]
         [?link :link/dst ?via]
         [?link :link/cost ?c1]
         [?path :path/src ?via]
         [?path :path/dst ?dst]
         [?path :path/cost ?c2]
         [(+ ?c1 ?c2) ?cost]]

       (query [:link {:src :?src :dst :?via :cost :?c1}]
              [:path {:src :?via :dst :?dst :cost :?c2}]
              '[(+ ?c1 ?c2) ?cost]
              '(not
                [:link {:src :?dst}]))
       =>
       '[[?link :link/src ?src]
         [?link :link/dst ?via]
         [?link :link/cost ?c1]
         [?path :path/src ?via]
         [?path :path/dst ?dst]
         [?path :path/cost ?c2]
         [(+ ?c1 ?c2) ?cost]
         (not
          [?link :link/src ?dst])])

(facts "About vars"
       (lvars '[[?link :link/src 1]])
       =>
       '#{?link}

       (lvars '[[?link :link/src ?src]
                [?link :link/dst ?via]
                [?link :link/cost ?c1]
                [?path :path/src ?via]
                [?path :path/dst ?dst]
                [?path :path/cost ?c2]
                [(+ ?c1 ?c2) ?cost]
                (not
                 [?link :link/src ?dst])])
       =>
       '#{?link ?src ?c1 ?path ?via ?c2 ?cost ?dst})

(facts "About safety"
       (tabular
        (fact "every variable that appears in the head of a clause also
appears in a nonarithmetic positive (i.e. not negated) literal in the
body of the clause"
              (safe? ?head ?body) => ?safe)
        ?head         ?body          ?safe
        '[?a]         '[?a]          true
        '[]           '[?a]          true
        '[?a]         '[]            false
        '[]           '[[?a]]        true
        '[[?a]]       '[]            false
        '{:a ?a}      '[[?a] [?b]]   true
        '{:a ?c}      '[[?a] [?b]]   false
        '{?b {:b ?a}} '[[?a] [?b]]   true)
       (tabular
        (fact "requires that every variable appearing in a negative
literal in the body of a clause also appears in some positive literal in
the body of the clause"
              (safe? [] ?body) => ?safe)
        ?body                 ?safe
        '[(not [?q])]         false
        '[[?q] (not [?q])]    true))
