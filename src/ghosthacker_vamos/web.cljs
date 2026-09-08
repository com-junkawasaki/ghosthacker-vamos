(ns ghosthacker-vamos.web
  "GHOST HACKER: VAMOS A BAILAR -- browser host adapter (ADR-2607100900
  follow-up (b)). Plain reagent, no Web Audio -- same low-complexity end
  of the portfolio's host spectrum as ECHOES/TUNING (no real-time beat).

  Pass-the-device party UX: one shared browser tab/device, players take
  turns in `roster/roster` order within each round. Every input is a
  button click (no text/keyboard entry needed) so a full multi-round,
  multi-player playthrough can be driven by dispatching real click
  events alone -- reaction-tap offers a small fixed set of delta buttons
  around the shown target instead of a free-form number field,
  quick-pick is buttons-per-option, and sequence-recall builds up an
  answer by clicking steps in the order the player believes is correct
  (disabled once picked, 'やり直す' clears the buffer, '決定' submits
  once all steps are picked). Once a round's last player submits, a
  round-result screen shows everyone's judgment before advancing;
  after the last round, a final leaderboard (ranking + winner(s)) is
  shown with a restart control."
  (:require [kotoba.lang.text :as str]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [ghosthacker-vamos.core :as core]
            [ghosthacker-vamos.roster :as roster]))

(defn- reset-round-progress [s]
  (assoc s :player-idx 0 :collected [] :seq-buffer []))

(defn- initial-state []
  (reset-round-progress
   {:phase :playing
    :rounds roster/sample-match
    :round-idx 0
    :party-state (core/initial-party-state roster/roster)
    :last-round nil}))

(defonce state (r/atom (initial-state)))

(defn- current-round [s] (nth (:rounds s) (:round-idx s)))
(defn- current-player [s] (nth roster/roster (:player-idx s)))

(defn- finish-player-turn!
  "現在のプレイヤーの入力valueを記録する。roster全員分揃っていなければ
   次のプレイヤーへ、揃っていればcore/play-roundを適用してround-result
   画面へ遷移する。"
  [value]
  (let [{:keys [round-idx player-idx collected rounds party-state]} @state
        round-spec (nth rounds round-idx)
        collected' (conj collected value)]
    (if (< (inc player-idx) (count roster/roster))
      (swap! state assoc :collected collected' :player-idx (inc player-idx) :seq-buffer [])
      (let [filled (case (:minigame round-spec)
                     :reaction-tap (assoc round-spec :guesses collected')
                     :quick-pick (assoc round-spec :picks collected')
                     :sequence-recall (assoc round-spec :answers collected'))
            next-party (core/play-round party-state filled)]
        (swap! state assoc
               :party-state next-party
               :last-round (last (:rounds next-party))
               :phase :round-result)))))

(defn- next-round! []
  (let [{:keys [round-idx rounds]} @state]
    (if (< (inc round-idx) (count rounds))
      (swap! state #(-> % (assoc :round-idx (inc round-idx) :phase :playing) reset-round-progress))
      (swap! state assoc :phase :final))))

(defn- restart! [] (reset! state (initial-state)))

(def ^:private reaction-deltas [-10 -5 -2 -1 0 1 2 5 10])

(defn- reaction-tap-view [round-spec]
  [:div.vamos-round
   [:h2 (:label round-spec)]
   [:p.vamos-target (str "目標の数字: " (:target round-spec))]
   [:p.vamos-turn (str (name (current-player @state)) " の番")]
   (into [:div.vamos-choices]
         (map (fn [d]
                ^{:key d}
                [:button {:on-click #(finish-player-turn! (+ (:target round-spec) d))}
                 (cond (zero? d) "ぴったり" (pos? d) (str "+" d) :else (str d))])
              reaction-deltas))])

(defn- quick-pick-view [round-spec]
  [:div.vamos-round
   [:h2 (:label round-spec)]
   [:p.vamos-turn (str (name (current-player @state)) " の番")]
   (into [:div.vamos-choices.vamos-choices--stacked]
         (map-indexed (fn [i opt]
                        ^{:key i}
                        [:button {:on-click #(finish-player-turn! i)} (:label opt)])
                      (:options round-spec)))])

(defn- sequence-recall-view [round-spec]
  (let [buffer (:seq-buffer @state)
        steps (:sequence round-spec)
        labels (:step-labels round-spec)
        done? (= (count buffer) (count steps))]
    [:div.vamos-round
     [:h2 (:label round-spec)]
     [:p.vamos-turn (str (name (current-player @state)) " の番")]
     [:p.vamos-buffer (str "選んだ順: " (str/join " -> " (map name buffer)))]
     (into [:div.vamos-choices.vamos-choices--stacked]
           (map (fn [step]
                  ^{:key step}
                  [:button {:disabled (boolean (some #(= % step) buffer))
                            :on-click #(swap! state update :seq-buffer conj step)}
                   (get labels step (name step))])
                steps))
     [:div.vamos-controls
      [:button.vamos-secondary {:on-click #(swap! state assoc :seq-buffer [])} "やり直す"]
      [:button.vamos-lock {:disabled (not done?) :on-click #(finish-player-turn! buffer)} "決定"]]]))

(defn- playing-screen []
  (let [round-spec (current-round @state)]
    (case (:minigame round-spec)
      :reaction-tap (reaction-tap-view round-spec)
      :quick-pick (quick-pick-view round-spec)
      :sequence-recall (sequence-recall-view round-spec))))

(defn- round-result-screen []
  (let [{:keys [minigame results]} (:last-round @state)]
    [:div.vamos-round
     [:h2 (str "ラウンド結果: " (name minigame))]
     (into [:div.vamos-results]
           (map (fn [{:keys [player judgment points]}]
                  ^{:key player}
                  [:p.vamos-result-line (str (name player) ": " (name judgment) " (+" points ")")])
                results))
     [:button {:on-click next-round!} "次へ"]]))

(defn- final-screen []
  (let [summary (core/summary (:party-state @state))]
    [:div.vamos-round
     [:h2 "最終結果"]
     (into [:div.vamos-ranking]
           (map-indexed (fn [i {:keys [player score]}]
                          ^{:key player}
                          [:p.vamos-rank-line (str (inc i) "位  " (name player) "  " score "点")])
                        (:ranking summary)))
     [:p.vamos-winner (str "winner: " (str/join ", " (map name (:winners summary))))]
     [:button {:on-click restart!} "もう一度"]]))

(defn app []
  [:div.vamos-app
   [:h1 "GHOST HACKER: VAMOS A BAILAR"]
   [:p.vamos-sub "houkago-vamos"]
   (case (:phase @state)
     :round-result [round-result-screen]
     :final [final-screen]
     [playing-screen])])

(defn ^:export mount []
  (when-let [el (.getElementById js/document "app")]
    (rdom/render [app] el)))

(defn ^:export init [] (mount))
