(ns ghosthacker-vamos.terminal-test
  "-mainそのものはテストせず、private var経由でread-int!/read-choice!/
   read-token-sequence!/collect-per-player/各play-*-interactive!/
   play-match!を直接叩く。実プロセスとしての-main自体は手動検証済み
   （完走/EOF途中打ち切り/不正入力の再入力要求、いずれも正しく完了し
   プロセスがハングしないことを確認）。"
  (:require [clojure.test :refer [deftest is testing]]
            [ghosthacker-vamos.core :as core]
            [ghosthacker-vamos.roster :as roster]
            [ghosthacker-vamos.terminal :as terminal]))

(def ^:private read-int! #'terminal/read-int!)
(def ^:private read-choice! #'terminal/read-choice!)
(def ^:private read-token-sequence! #'terminal/read-token-sequence!)
(def ^:private collect-per-player #'terminal/collect-per-player)
(def ^:private play-reaction-tap-interactive! #'terminal/play-reaction-tap-interactive!)
(def ^:private play-quick-pick-interactive! #'terminal/play-quick-pick-interactive!)
(def ^:private play-sequence-recall-interactive! #'terminal/play-sequence-recall-interactive!)
(def ^:private play-match! #'terminal/play-match!)

(defn- silently [thunk]
  (binding [*out* (java.io.StringWriter.)]
    (thunk)))

(deftest read-int-boundary-test
  (testing "整数が入力されるまで読み直す"
    (is (= 42 (silently #(with-in-str "42\n" (read-int! "> ")))))
    (is (= 42 (silently #(with-in-str "abc\n42\n" (read-int! "> "))))))
  (testing "EOFはnil"
    (is (nil? (silently #(with-in-str "" (read-int! "> ")))))))

(deftest read-choice-boundary-test
  (testing "1..n-optionsの範囲、0始まりindexで返す"
    (is (= 0 (silently #(with-in-str "1\n" (read-choice! "> " 3)))))
    (is (= 2 (silently #(with-in-str "3\n" (read-choice! "> " 3))))))
  (testing "範囲外/数字以外は読み飛ばし、次の有効な入力を処理する"
    (is (= 1 (silently #(with-in-str "0\n4\nxyz\n2\n" (read-choice! "> " 3))))))
  (testing "EOFはnil"
    (is (nil? (silently #(with-in-str "" (read-choice! "> " 3)))))))

(deftest read-token-sequence-boundary-test
  (let [valid #{:a :b :c :d}]
    (testing "期待の個数、valid-tokensに属するトークンが入力されるまで読み直す"
      (is (= [:a :b :c :d] (silently #(with-in-str "a,b,c,d\n" (read-token-sequence! "> " 4 valid)))))
      (is (= [:a :b :c :d] (silently #(with-in-str "a b c d\n" (read-token-sequence! "> " 4 valid))))))
    (testing "個数が違う/未知トークンは読み飛ばし、次の有効な入力を処理する"
      (is (= [:a :b :c :d]
             (silently #(with-in-str "a,b,c\nz,y,x,w\na,b,c,d\n" (read-token-sequence! "> " 4 valid))))))
    (testing "EOFはnil"
      (is (nil? (silently #(with-in-str "" (read-token-sequence! "> " 4 valid))))))))

(deftest collect-per-player-test
  (testing "roster全員ぶんの結果を順に集める(read-fnにmapを使い、playerごとの
            戻り値を固定して検証)"
    (is (= [1 2 3] (collect-per-player [:p1 :p2 :p3] {:p1 1 :p2 2 :p3 3}))))
  (testing "途中でEOF(read-fnがnilを返す)なら全体としてnilを返す"
    (is (nil? (collect-per-player [:p1 :p2 :p3] {:p1 1 :p3 3})))))

(deftest play-reaction-tap-interactive-test
  (testing "roster全員分の数字入力を集め、:guessesを付けたround-specを返す"
    (let [round-spec (silently
                      #(with-in-str "42\n47\n999\n40\n"
                         (play-reaction-tap-interactive! roster/roster roster/notification-badge-round)))]
      (is (= [42 47 999 40] (:guesses round-spec)))
      (is (= :reaction-tap (:minigame round-spec)))))
  (testing "途中でEOFになれば打ち切り(nil)"
    (is (nil? (silently
               #(with-in-str "42\n"
                  (play-reaction-tap-interactive! roster/roster roster/notification-badge-round)))))))

(deftest play-quick-pick-interactive-test
  (testing "roster全員分の選択を集め、:picksを付けたround-specを返す"
    (let [round-spec (silently
                      #(with-in-str "1\n1\n2\n1\n"
                         (play-quick-pick-interactive! roster/roster roster/phishing-url-round)))]
      (is (= [0 0 1 0] (:picks round-spec))))))

(deftest play-sequence-recall-interactive-test
  (testing "roster全員分の回答を集め、:answersを付けたround-specを返す"
    (let [round-spec (silently
                      #(with-in-str "disconnect,change-password,enable-2fa,report\ndisconnect,change-password,enable-2fa,report\nreport,enable-2fa,change-password,disconnect\ndisconnect,change-password,enable-2fa,report\n"
                         (play-sequence-recall-interactive! roster/roster roster/incident-response-round)))]
      (is (= [[:disconnect :change-password :enable-2fa :report]
              [:disconnect :change-password :enable-2fa :report]
              [:report :enable-2fa :change-password :disconnect]
              [:disconnect :change-password :enable-2fa :report]]
             (:answers round-spec))))))

(deftest play-match-full-and-eof-boundary-test
  (testing "全ラウンドを完走すればcore/summaryがwinnerを返せる状態になる"
    (let [input (str "42\n47\n999\n40\n"                                  ; reaction-tap
                      "1\n1\n1\n1\n"                                       ; quick-pick (all correct)
                      "disconnect,change-password,enable-2fa,report\n"     ; sequence-recall x4
                      "disconnect,change-password,enable-2fa,report\n"
                      "disconnect,change-password,enable-2fa,report\n"
                      "disconnect,change-password,enable-2fa,report\n")
          state (silently #(with-in-str input (play-match! roster/roster roster/sample-match)))
          result (core/summary state)]
      (is (= 3 (:round-count result)))
      (is (= [:ren] (:winners result)))))
  (testing "途中でEOFになれば、そこまでのstateで打ち切る(未完走ラウンドあり)"
    (let [state (silently #(with-in-str "42\n47\n" (play-match! roster/roster roster/sample-match)))]
      (is (= 0 (:round-count (core/summary state)))))))
