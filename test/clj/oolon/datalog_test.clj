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
       (rel->eavt :link {}) => '[[?link :link/$id ?link$id]]
       (rel->eavt :link {:src 1}) => '[[?link :link/src 1] [?link :link/$id ?link$id]]
       (rel->eavt :link#1 {:src 1}) => '[[?link1 :link/src 1] [?link1 :link/$id ?link1$id]]
       (rel->eavt :link {:src :?src}) => '[[?link :link/src ?src] [?link :link/$id ?link$id]]
       (rel->eavt :link {:src :src :_ :dst}) => '[[?link :link/src :src]
                                                  [?link _ :dst]
                                                  [?link :link/$id ?link$id]]
       (rel->eavt :link {:src :?src :dst :?dst} 1) =>  '[[?link :link/src ?src 1]
                                                         [?link :link/dst ?dst 1]
                                                         [?link :link/$id ?link$id]]
       (rel->eavt :link {:src :?src :dst :dst} :?tx) =>  '[[?link :link/src ?src ?tx]
                                                           [?link :link/dst :dst ?tx]
                                                           [?link :link/$id ?link$id]])

(facts "About generating queries"
       (query) => nil

       (query [:link {:src 1}])
       =>
       '[[?link :link/src 1]
         [?link :link/$id ?link$id]]

       (query [:link#1 {:src 1}]
              [:link#2 {:src 2}])
       =>
       '[[?link1 :link/src 1]
         [?link1 :link/$id ?link1$id]
         [?link2 :link/src 2]
         [?link2 :link/$id ?link2$id]]

       (query [:link {:src :?src :dst :?via}]
              [:path {:src :?via :dst :?dst}])
       =>
       '[[?link :link/src ?src]
         [?link :link/dst ?via]
         [?link :link/$id ?link$id]
         [?path :path/src ?via]
         [?path :path/dst ?dst]
         [?path :path/$id ?path$id]]

       (query [:link {:src :?src :dst :?via :cost :?c1}]
              [:path {:src :?via :dst :?dst :cost :?c2}]
              '[(+ ?c1 ?c2) ?cost])
       =>
       '[[?link :link/src ?src]
         [?link :link/dst ?via]
         [?link :link/cost ?c1]
         [?link :link/$id ?link$id]
         [?path :path/src ?via]
         [?path :path/dst ?dst]
         [?path :path/cost ?c2]
         [?path :path/$id ?path$id]
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
         [?link :link/$id ?link$id]
         [?path :path/src ?via]
         [?path :path/dst ?dst]
         [?path :path/cost ?c2]
         [?path :path/$id ?path$id]
         [(+ ?c1 ?c2) ?cost]
         (not
          [?link :link/src ?dst]
          [?link :link/$id ?link$id])])

(facts "About vars"
       (lvars '[[?link :link/src 1]])
       =>
       '#{?link}

       (lvars '[[?link :link/src ?src]
                [?link :link/dst ?via]
                [?link :link/cost ?c1]
                [?link :link/$id ?link$id]
                [?path :path/src ?via]
                [?path :path/dst ?dst]
                [?path :path/cost ?c2]
                [?path :path/$id ?path$id]
                [(+ ?c1 ?c2) ?cost]
                (not
                 [?link :link/src ?dst]
                 [?link :link/$id ?link$id])])
       =>
       '#{?link ?link$id ?src ?c1 ?path ?path$id ?via ?c2 ?cost ?dst})

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

(facts "About rules"
       (let [link-path (rule [:path {:src :?src :dst :?dst}]
                             [[:link {:src :?src :dst :?dst}]])
             path-path (rule [:path {:src :?src :dst :?dst}]
                             [[:link {:src :?src :dst :?via}]
                              [:path {:src :?via :dst :?dst}]])
             everyone-likes (rule [:everyone-likes {:name :?x}]
                                  [[:person {:name :?x}]
                                   '(not-join [?x]
                                              [:is-not-liked {:name :?x}])])
             is-not-liked (rule [:is-not-liked {:name :?y}]
                                [[:person#x {:name :?x}]
                                 [:person#y {:name :?y}]
                                 '(not-join [?x ?y]
                                            [:likes {:person :?x :likes :?y}])])]
         (fact "We can ennumerate the positive attrs in the head of each rule"
               (:head-attrs link-path) => #{:path/src :path/dst}
               (:head-attrs path-path) => #{:path/src :path/dst}
               (:head-attrs everyone-likes) => #{:everyone-likes/name}
               (:head-attrs is-not-liked) => #{:is-not-liked/name})
         (fact "We can ennumerate the positive attrs a dule depends on"
               (:pos-attrs link-path) => #{:link/src :link/dst}
               (:pos-attrs path-path) => #{:link/src :link/dst :path/src :path/dst}
               (:pos-attrs everyone-likes) => #{:person/name}
               (:pos-attrs is-not-liked) => #{:person/name})
         (facts "About safety"
                (:safe? link-path) => true
                (:safe? path-path) => true
                (:safe? everyone-likes) => true
                (:safe? is-not-liked) => true)
         (facts "About negation"
                (fact "Rules with a not are negative"
                      (:neg? link-path) => false
                      (:neg? path-path) => false
                      (:neg? everyone-likes) => true
                      (:neg? is-not-liked) => true)
                (fact "Negative rules have dependant attributes"
                      (:neg-attrs link-path) => #{}
                      (:neg-attrs path-path) => #{}
                      (:neg-attrs everyone-likes) => #{:is-not-liked/name}
                      (:neg-attrs is-not-liked) => #{:likes/person :likes/likes}))
         (facts "About dependence"
                (depends-on? link-path path-path) => false
                (depends-on? path-path link-path) => false
                (depends-on? is-not-liked everyone-likes) => false
                (depends-on? everyone-likes is-not-liked) => true)))
