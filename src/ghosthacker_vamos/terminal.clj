(ns ghosthacker-vamos.terminal
  "GHOST HACKER: VAMOS A BAILAR -- minimal terminal host adapter (playable
  prototype).

  Like ghosthacker-echoes/ghosthacker-tuning's terminal prototypes, VAMOS
  A BAILAR has no real-time beat to track -- no `future`/agent thread
  pool. A plain pass-the-device REPL loop: for each round in
  `roster/sample-match`, every player in `roster/roster` (in order) takes
  one turn at that round's minigame; once all players have gone, the
  round's inputs are handed to `core/play-round` and the match advances.
  EOF (or `q`/`quit` where applicable) at any point aborts the whole
  match and falls through to whatever result the party has accumulated
  so far.

  Run: clojure -M -m ghosthacker-vamos.terminal"
  (:require [kotoba.lang.text :as str]
            [ghosthacker-vamos.core :as core]
            [ghosthacker-vamos.roster :as roster]))

;; ---------------------------------------------------------------------
;; Low-level, validated stdin readers. Each returns nil only on true EOF
;; -- an invalid line re-prompts (recur) rather than returning nil, so
;; nil unambiguously means "stop the match here".
;; ---------------------------------------------------------------------

(defn- read-int!
  "整数が入力されるまで読み直す。EOFならnil。"
  [prompt]
  (print prompt) (flush)
  (when-let [line (read-line)]
    (let [n (try (Integer/parseInt (str/trim line)) (catch NumberFormatException _ nil))]
      (if n
        n
        (do (println "数字を入力してください。") (recur prompt))))))

(defn- read-choice!
  "1..n-optionsの番号が入力されるまで読み直し、0始まりのindexを返す。
   EOFならnil。"
  [prompt n-options]
  (print prompt) (flush)
  (when-let [line (read-line)]
    (let [n (try (Integer/parseInt (str/trim line)) (catch NumberFormatException _ nil))]
      (if (and n (<= 1 n n-options))
        (dec n)
        (do (println (format "1〜%dの番号で選んでください。" n-options))
            (recur prompt n-options))))))

(defn- read-token-sequence!
  "valid-tokens(集合)に属するトークンをちょうどexpected-length個、
   カンマ/空白区切りで入力するまで読み直す。EOFならnil。"
  [prompt expected-length valid-tokens]
  (print prompt) (flush)
  (when-let [line (read-line)]
    (let [tokens (->> (str/split (str/trim line) #"[,\s]+")
                       (remove str/blank?)
                       (mapv keyword))]
      (if (and (= (count tokens) expected-length) (every? valid-tokens tokens))
        tokens
        (do (println (format "%d個を、%sの中からカンマ区切りで入力してください。"
                              expected-length (str/join "/" (map name valid-tokens))))
            (recur prompt expected-length valid-tokens))))))

;; ---------------------------------------------------------------------
;; Per-player collection across the roster. Returns nil (abort) if any
;; player's turn hits EOF; otherwise a vector of that many inputs.
;; ---------------------------------------------------------------------

(defn- collect-per-player
  "roster-listの各playerについてread-fn(player)を呼び、結果を集める。
   途中でnil(EOF)が返ったら、そこで打ち切ってnilを返す。"
  [roster-list read-fn]
  (reduce (fn [acc player]
            (let [v (read-fn player)]
              (if (nil? v) (reduced nil) (conj acc v))))
          []
          roster-list))

;; ---------------------------------------------------------------------
;; Per-minigame interactive rounds. Each returns the round-spec assoc'd
;; with its input key (ready for `core/play-round`), or nil if the round
;; was aborted (EOF) partway through.
;; ---------------------------------------------------------------------

(defn- play-reaction-tap-interactive!
  [roster-list round-spec]
  (println (format "-- %s --" (:label round-spec)))
  (println (format "目標の数字: %d" (:target round-spec)))
  (when-let [guesses (collect-per-player
                       roster-list
                       (fn [player]
                         (read-int! (format "[%s] 数字を入力 > " (name player)))))]
    (assoc round-spec :guesses guesses)))

(defn- print-quick-pick-options! [options]
  (doseq [[i opt] (map-indexed vector options)]
    (println (format "  %d) %s" (inc i) (:label opt)))))

(defn- play-quick-pick-interactive!
  [roster-list round-spec]
  (println (format "-- %s --" (:label round-spec)))
  (print-quick-pick-options! (:options round-spec))
  (when-let [picks (collect-per-player
                    roster-list
                    (fn [player]
                      (read-choice! (format "[%s] > " (name player))
                                    (count (:options round-spec)))))]
    (assoc round-spec :picks picks)))

(defn- play-sequence-recall-interactive!
  [roster-list round-spec]
  (println (format "-- %s --" (:label round-spec)))
  (let [steps (:sequence round-spec)
        labels (:step-labels round-spec)
        valid (set steps)]
    (doseq [s steps]
      (println (format "  - %s (%s)" (name s) (get labels s (name s)))))
    (when-let [answers (collect-per-player
                        roster-list
                        (fn [player]
                          (read-token-sequence!
                           (format "[%s] 正しい順で入力(例: %s) > "
                                   (name player) (str/join "," (map name steps)))
                           (count steps) valid)))]
      (assoc round-spec :answers answers))))

(defn- play-round-interactive!
  [roster-list round-spec]
  (case (:minigame round-spec)
    :reaction-tap (play-reaction-tap-interactive! roster-list round-spec)
    :quick-pick (play-quick-pick-interactive! roster-list round-spec)
    :sequence-recall (play-sequence-recall-interactive! roster-list round-spec)))

(defn- play-match!
  "roster-listでrounds(round-specのシーケンス)を最初から順にこなす。
   いずれかのラウンドがEOFで打ち切られたら、そこまでのstateで終える。"
  [roster-list rounds]
  (loop [state (core/initial-party-state roster-list)
         rs (seq rounds)]
    (if (nil? rs)
      state
      (let [filled (play-round-interactive! roster-list (first rs))]
        (if (nil? filled)
          state
          (recur (core/play-round state filled) (next rs)))))))

(defn -main
  "Entry point for `clojure -M -m ghosthacker-vamos.terminal`."
  [& _args]
  (println "GHOST HACKER: VAMOS A BAILAR — 放課後パーティナイト")
  (println (format "プレイヤー: %s"
                    (str/join " / " (map roster/player-names roster/roster))))
  (println)
  (let [state (play-match! roster/roster roster/sample-match)
        result (core/summary state)]
    (println)
    (println "=== RESULT ===")
    (doseq [{:keys [player score]} (:ranking result)]
      (println (format "%s: %d" (name (roster/player-names player player)) score)))
    (println (format "winner(s): %s"
                      (str/join ", " (map #(name (roster/player-names % %)) (:winners result)))))))
