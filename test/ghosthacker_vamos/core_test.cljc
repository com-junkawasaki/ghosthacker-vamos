(ns ghosthacker-vamos.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [ghosthacker-vamos.core :as core]
            [ghosthacker-vamos.roster :as roster]))

;; ---------------------------------------------------------------------
;; reaction-tap
;; ---------------------------------------------------------------------

(deftest judge-reaction-tap-tiers
  (testing "差0は:bullseye"
    (is (= :bullseye (core/judge-reaction-tap 42 42))))
  (testing "close-window境界"
    (is (= :close (core/judge-reaction-tap 42 (+ 42 core/reaction-close-window))))
    (is (= :close (core/judge-reaction-tap 42 (- 42 core/reaction-close-window))))
    (is (= :close (core/judge-reaction-tap 42 43))))
  (testing "close-windowを1超えたら:miss"
    (is (= :miss (core/judge-reaction-tap 42 (+ 42 core/reaction-close-window 1))))
    (is (= :miss (core/judge-reaction-tap 42 (- 42 core/reaction-close-window 1))))
    (is (= :miss (core/judge-reaction-tap 42 999)))))

;; ---------------------------------------------------------------------
;; quick-pick
;; ---------------------------------------------------------------------

(def ^:private options
  [{:label "a" :correct? false}
   {:label "b" :correct? true}
   {:label "c" :correct? false}])

(deftest judge-quick-pick-binary
  (testing "正解indexを選べば:correct、それ以外は:incorrect"
    (is (= :correct (core/judge-quick-pick options 1)))
    (is (= :incorrect (core/judge-quick-pick options 0)))
    (is (= :incorrect (core/judge-quick-pick options 2)))
    (is (= :incorrect (core/judge-quick-pick options 99)))))

;; ---------------------------------------------------------------------
;; sequence-recall
;; ---------------------------------------------------------------------

(def ^:private steps [:a :b :c :d])

(deftest judge-sequence-recall-tiers
  (testing "全一致で:perfect"
    (is (= :perfect (core/judge-sequence-recall steps [:a :b :c :d]))))
  (testing "1つでも一致すれば:partial(境界: ちょうど1つ一致)"
    (is (= :partial (core/judge-sequence-recall steps [:a :x :y :z])))
    (is (= :partial (core/judge-sequence-recall steps [:x :b :c :z]))))
  (testing "1つも一致しなければ:miss"
    (is (= :miss (core/judge-sequence-recall steps [:d :c :b :a]))))
  (testing "長さが違っても位置比較でずれた分は不一致扱い"
    (is (= :partial (core/judge-sequence-recall steps [:a :b])))))

;; ---------------------------------------------------------------------
;; party state: tally / rounds bookkeeping
;; ---------------------------------------------------------------------

(def ^:private test-roster [:p1 :p2 :p3])

(deftest initial-party-state-shape
  (testing "全員0点、rounds空で始まる"
    (let [s (core/initial-party-state test-roster)]
      (is (= test-roster (:roster s)))
      (is (= {:p1 0 :p2 0 :p3 0} (:tally s)))
      (is (= [] (:rounds s))))))

(deftest play-reaction-tap-round-updates-tally
  (testing "roster順にguessesを判定し、tallyへ加算する"
    (let [s0 (core/initial-party-state test-roster)
          s1 (core/play-reaction-tap-round s0 42 [42 43 999])] ; bullseye/close/miss
      (is (= {:p1 100 :p2 40 :p3 0} (:tally s1)))
      (is (= 1 (count (:rounds s1))))
      (let [{:keys [minigame results]} (first (:rounds s1))]
        (is (= :reaction-tap minigame))
        (is (= [:bullseye :close :miss] (map :judgment results)))
        (is (= [:p1 :p2 :p3] (map :player results)))))))

(deftest play-quick-pick-round-updates-tally
  (testing "正解を引いたプレイヤーだけ加点される"
    (let [s0 (core/initial-party-state test-roster)
          s1 (core/play-quick-pick-round s0 options [1 0 2])]
      (is (= {:p1 80 :p2 0 :p3 0} (:tally s1))))))

(deftest play-sequence-recall-round-updates-tally
  (testing "一致数に応じたtierで加点される"
    (let [s0 (core/initial-party-state test-roster)
          s1 (core/play-sequence-recall-round s0 steps
                                               [[:a :b :c :d] [:a :x :y :z] [:d :c :b :a]])]
      (is (= {:p1 90 :p2 30 :p3 0} (:tally s1))))))

(deftest partial-round-only-scores-players-who-answered
  (testing "入力がrosterより短い場合、対応する分だけが判定・加点される
            (host adapterが途中で打ち切った場合に相当、エラーにはしない)"
    (let [s0 (core/initial-party-state test-roster)
          s1 (core/play-reaction-tap-round s0 42 [42])]
      (is (= {:p1 100 :p2 0 :p3 0} (:tally s1)))
      (is (= 1 (count (:results (first (:rounds s1)))))))))

(deftest play-round-dispatches-by-minigame
  (testing "core/play-roundはround-specの:minigameで各ラウンド関数へ委譲する"
    (let [s0 (core/initial-party-state test-roster)
          s1 (core/play-round s0 {:minigame :reaction-tap :target 42 :guesses [42 42 42]})
          s2 (core/play-round s1 {:minigame :quick-pick :options options :picks [1 1 1]})
          s3 (core/play-round s2 {:minigame :sequence-recall :sequence steps
                                   :answers [[:a :b :c :d] [:a :b :c :d] [:a :b :c :d]]})]
      (is (= {:p1 270 :p2 270 :p3 270} (:tally s3)))
      (is (= 3 (count (:rounds s3)))))))

;; ---------------------------------------------------------------------
;; ranking / winners / summary
;; ---------------------------------------------------------------------

(deftest ranking-orders-by-score-desc-stable-ties
  (testing "得点降順。同点はroster登録順を保つ(安定ソート)"
    (let [s (-> (core/initial-party-state test-roster)
                (assoc :tally {:p1 50 :p2 90 :p3 50}))]
      (is (= [{:player :p2 :score 90} {:player :p1 :score 50} {:player :p3 :score 50}]
             (core/ranking s))))))

(deftest winners-handles-outright-and-tied-top
  (testing "単独首位なら1人"
    (let [s (-> (core/initial-party-state test-roster)
                (assoc :tally {:p1 50 :p2 90 :p3 10}))]
      (is (= [:p2] (core/winners s)))))
  (testing "同点首位なら複数人、roster順"
    (let [s (-> (core/initial-party-state test-roster)
                (assoc :tally {:p1 90 :p2 10 :p3 90}))]
      (is (= [:p1 :p3] (core/winners s))))))

(deftest summary-shape
  (testing "summaryはtally/ranking/winners/round-countを返す"
    (let [s (-> (core/initial-party-state test-roster)
                (assoc :tally {:p1 90 :p2 10 :p3 90})
                (assoc :rounds [{:minigame :reaction-tap :results []}]))
          result (core/summary s)]
      (is (= {:p1 90 :p2 10 :p3 90} (:tally result)))
      (is (= [{:player :p1 :score 90} {:player :p3 :score 90} {:player :p2 :score 10}]
             (:ranking result)))
      (is (= [:p1 :p3] (:winners result)))
      (is (= 1 (:round-count result))))))

;; ---------------------------------------------------------------------
;; play-party / play-party-summary: full multi-round playthrough
;; ---------------------------------------------------------------------

(deftest play-party-full-playthrough
  (testing "3ラウンド分のround-specにplayer-per-turnの入力を与えた通しの
            プレイで、tally/ranking/winnersが期待通りになる"
    (let [rounds [(assoc roster/notification-badge-round
                          :guesses [42 47 999 40]) ; ren:bullseye nei:close kota:miss mei:close
                  (assoc roster/phishing-url-round
                          :picks [0 0 0 0]) ; only index 0 is correct -> all :correct
                  (assoc roster/incident-response-round
                          :answers [[:disconnect :change-password :enable-2fa :report]
                                    [:disconnect :change-password :enable-2fa :report]
                                    [:report :enable-2fa :change-password :disconnect]
                                    [:disconnect :x :y :z]])]
          result (core/play-party-summary roster/roster rounds)]
      ;; ren: 100 + 80 + 90 = 270
      ;; nei: 40 + 80 + 90 = 210
      ;; kota: 0 + 80 + 0 = 80
      ;; mei: 40 + 80 + 30 = 150
      (is (= {:ren 270 :nei 210 :kota 80 :mei 150} (:tally result)))
      (is (= [{:player :ren :score 270}
              {:player :nei :score 210}
              {:player :mei :score 150}
              {:player :kota :score 80}]
             (:ranking result)))
      (is (= [:ren] (:winners result)))
      (is (= 3 (:round-count result))))))

(deftest play-party-summary-matches-manual-composition
  (testing "play-party-summaryはplay-party+summaryの合成そのもの"
    (let [rounds [(assoc roster/notification-badge-round :guesses [42 42 42 42])]]
      (is (= (core/summary (core/play-party roster/roster rounds))
             (core/play-party-summary roster/roster rounds))))))
