(ns ghosthacker-vamos.core
  "GHOST HACKER: VAMOS A BAILAR -- full-cast party minigame core
  (ADR-2607023200, portfolio title #9: party genre, Ren & Nei + agency
  cast, \"大人数ミニゲーム集\").

  Pure, host-free judgment/state engine. Unlike the single-protagonist
  titles (FLOW/HARMONY/ECHOES/TUNING), VAMOS A BAILAR's whole cast plays
  every round: each round is ONE trivially-simple judged minigame, and
  EVERY player in the roster takes a turn at the SAME minigame, each
  turn producing a per-player point delta that accumulates into a
  shared `:tally` (the party score). After all rounds, `ranking` /
  `winners` / `summary` turn the tally into a leaderboard. The \"party
  game\" feel comes from stacking a few distinct short activities back
  to back, not from any one activity having depth -- deliberately
  mirroring the portfolio's established modesty (ADR-2607023200
  addendum 2).

  Three minigames, each a single judged action (no sub-game depth):

  - `reaction-tap` -- a target number is shown; the player enters a
    number and is judged purely on numeric closeness
    (:bullseye/:close/:miss). No timing/clock is modeled here -- a host
    adapter may flash the target briefly for tension, but the pure core
    only ever compares two integers.
  - `quick-pick` -- given a small set of labeled options where exactly
    one is correct (Ghost Hacker's recurring real-world security-
    education framing, :gh/educationalContent -- e.g. \"which of these
    URLs is the real one\"), the player picks one; judged binary
    (:correct/:incorrect).
  - `sequence-recall` -- given a fixed short sequence of labeled steps
    (again security-education framed, e.g. incident-response order),
    the player answers with their believed order; judged by how many
    positions match (:perfect/:partial/:miss).

  No rendering, input, or scheduling I/O lives here -- those are host
  adapters layered on top (terminal.clj / web.cljs), same core/host
  split as every other title in this portfolio."
  )

;; ---------------------------------------------------------------------
;; Reaction tap
;; ---------------------------------------------------------------------

(defn- magnitude
  "xの絶対値(cljc互換、Math/absを避ける)。"
  [x]
  (if (neg? x) (- x) x))

(def reaction-close-window
  "reaction-tapの:close判定窓(target/guessの整数差の絶対値)。この値以下なら
   :close、これを超えたら:miss。ちょうど0(差なし)は:bullseye。"
  5)

(defn judge-reaction-tap
  "targetとguess(共に整数)の差から判定を返す(:bullseye/:close/:miss)。"
  [target guess]
  (let [diff (magnitude (- target guess))]
    (cond
      (zero? diff) :bullseye
      (<= diff reaction-close-window) :close
      :else :miss)))

(def reaction-tap-points
  {:bullseye 100 :close 40 :miss 0})

;; ---------------------------------------------------------------------
;; Quick pick
;; ---------------------------------------------------------------------

(defn- correct-option-index
  "options(各要素{:label :correct?})のうち、:correct?がtrueな要素のindexを
   返す。options自体は『ちょうど1つがcorrect』であることが呼び出し側
   (サンプルデータ)の責任 -- 見つからなければnil。"
  [options]
  (some (fn [[i opt]] (when (:correct? opt) i))
        (map-indexed vector options)))

(defn judge-quick-pick
  "optionsに対するpick-idx(0始まり)が正解かどうかを判定する
   (:correct/:incorrect)。"
  [options pick-idx]
  (if (= pick-idx (correct-option-index options))
    :correct
    :incorrect))

(def quick-pick-points
  {:correct 80 :incorrect 0})

;; ---------------------------------------------------------------------
;; Sequence recall
;; ---------------------------------------------------------------------

(defn- match-count
  "sequenceとanswerを同じ位置同士で比較した一致数。"
  [sequence answer]
  (count (filter true? (map = sequence answer))))

(defn judge-sequence-recall
  "sequence(正しい順番)に対するanswer(プレイヤーの回答順)を、一致した
   位置の数から判定する。全一致なら:perfect、1つ以上一致なら:partial、
   1つも一致しなければ:miss。"
  [sequence answer]
  (let [total (count sequence)
        matches (match-count sequence answer)]
    (cond
      (= matches total) :perfect
      (pos? matches) :partial
      :else :miss)))

(def sequence-recall-points
  {:perfect 90 :partial 30 :miss 0})

;; ---------------------------------------------------------------------
;; Shared party state: tally across the roster, round by round
;; ---------------------------------------------------------------------

(defn initial-party-state
  "roster(プレイヤーキーワードのシーケンス)から初期state({:roster :tally
   :rounds})を作る。:tallyは全員0点、:roundsは空(消化したラウンドの
   ログ)。"
  [roster]
  {:roster (vec roster)
   :tally (zipmap roster (repeat 0))
   :rounds []})

(defn- apply-round-results
  "1ラウンド分の判定結果(judgments、(:roster state)と同じ順序で1人1つ)を
   stateに適用する: :tallyへ得点を加算し、:roundsへこのラウンドのログを
   積む。judgmentsが(:roster state)より短ければ、対応する後方のプレイヤー
   は今回ノーカウント(そのラウンドに参加しなかった/host側で打ち切った、
   として扱う -- エラーにはしない)。"
  [state minigame-kw judgments points-table]
  (let [results (mapv (fn [player judgment]
                         {:player player
                          :judgment judgment
                          :points (get points-table judgment)})
                       (:roster state) judgments)]
    (-> state
        (update :tally (fn [tally]
                          (reduce (fn [t {:keys [player points]}] (update t player + points))
                                  tally results)))
        (update :rounds conj {:minigame minigame-kw :results results}))))

(defn play-reaction-tap-round
  "roster全員ぶんのguesses(target狙いの整数、(:roster state)と同順)を
   reaction-tapで判定し、stateに適用する。"
  [state target guesses]
  (apply-round-results state :reaction-tap
                        (map #(judge-reaction-tap target %) guesses)
                        reaction-tap-points))

(defn play-quick-pick-round
  "roster全員ぶんのpicks(0始まりindex、(:roster state)と同順)を
   quick-pickで判定し、stateに適用する。"
  [state options picks]
  (apply-round-results state :quick-pick
                        (map #(judge-quick-pick options %) picks)
                        quick-pick-points))

(defn play-sequence-recall-round
  "roster全員ぶんのanswers(順番の回答、(:roster state)と同順)を
   sequence-recallで判定し、stateに適用する。"
  [state sequence answers]
  (apply-round-results state :sequence-recall
                        (map #(judge-sequence-recall sequence %) answers)
                        sequence-recall-points))

(defn play-round
  "round(round-spec: :minigameで種別を持ち、その種別に応じた入力キーを
   併せ持つマップ -- :reaction-tapなら:target/:guesses、:quick-pickなら
   :options/:picks、:sequence-recallなら:sequence/:answers)をstateに適用
   する共通ディスパッチャ。"
  [state round]
  (case (:minigame round)
    :reaction-tap (play-reaction-tap-round state (:target round) (:guesses round))
    :quick-pick (play-quick-pick-round state (:options round) (:picks round))
    :sequence-recall (play-sequence-recall-round state (:sequence round) (:answers round))))

;; ---------------------------------------------------------------------
;; Leaderboard / summary
;; ---------------------------------------------------------------------

(defn ranking
  "state(:tally/:roster)から得点降順のランキングを返す([{:player :score}...])。
   同点はrosterの登録順を安定ソートでそのままタイブレークに使う。"
  [state]
  (let [{:keys [roster tally]} state]
    (->> roster
         (mapv (fn [p] {:player p :score (get tally p)}))
         (sort-by (comp - :score))
         vec)))

(defn winners
  "stateの最高得点者を全員返す(同点なら複数、単独首位なら1人のベクタ)。"
  [state]
  (let [r (ranking state)
        top (:score (first r))]
    (mapv :player (filter #(= (:score %) top) r))))

(defn summary
  "1回のパーティ全体のリザルト画面向けサマリ。"
  [state]
  {:tally (:tally state)
   :ranking (ranking state)
   :winners (winners state)
   :round-count (count (:rounds state))})

(defn play-party
  "roster + rounds(round-specのシーケンス、入力込み)から最終stateを作る
   最短経路。ホストアダプタは通常インタラクティブに1ラウンドずつ
   `play-round`を呼ぶが、テスト/一括再生にはこちらが便利。"
  [roster rounds]
  (reduce play-round (initial-party-state roster) rounds))

(defn play-party-summary
  "play-party + summaryの合成。"
  [roster rounds]
  (summary (play-party roster rounds)))
