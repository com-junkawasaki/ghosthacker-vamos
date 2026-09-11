(ns ghosthacker-vamos.roster
  "GHOST HACKER: VAMOS A BAILAR -- sample roster + minigame round data
  (pure data, ADR-2607023200 portfolio title #9).

  \"houkago-vamos\": 放課後、Ren・Neiの事務所つながりの面々が居残って
  開く小さなパーティナイト。roster(4人、既存カノンキャラクターのみ --
  新規オリジナルキャラクターは追加しない、ADR-2607023200共通ルール)が
  同じ3つのミニゲームを順番にこなし、最後に合計点でランキングが決まる。
  quick-pick/sequence-recallの2ラウンドは、Ghost Hacker既存カノンの
  `:gh/educationalContent`(実在のセキュリティ教育)をそのままミニゲーム
  の題材にしている -- フィッシングURL見分け、アカウント乗っ取り後の
  対応手順、いずれも作中の啓発内容と地続き。

  データのみ、ロジックは`ghosthacker-vamos.core`側。"
  )

(def roster
  "この回のパーティメンバー(Ren & Nei + 事務所つながりの2人、既存カノン
   キャラクター)。"
  [:ren :nei :kota :mei])

(def player-names
  "host adapter表示用の表示名。"
  {:ren "Ren"
   :nei "Nei"
   :kota "Kota"
   :mei "Mei"})

;; -- Round 1: reaction-tap ----------------------------------------------
;; 一瞬示された(host側の演出、coreはタイミング非関与)目標の数字に、できる
;; だけ近い数字を入力する。

(def notification-badge-round
  {:minigame :reaction-tap
   :label "通知バッジ当て"
   :target 42})

;; -- Round 2: quick-pick --------------------------------------------------
;; 本物のURLを1つだけ選ぶ(残りはフィッシングの見た目そっくりな偽URL)。

(def phishing-url-round
  {:minigame :quick-pick
   :label "本物のURLはどれ?"
   :options [{:label "https://accounts.google.com/signin" :correct? true}
             {:label "https://accounts-google-verify.com/signin" :correct? false}
             {:label "https://google-account-security.net/login" :correct? false}
             {:label "http://accounts.google.com.secure-verify.jp" :correct? false}]})

;; -- Round 3: sequence-recall ----------------------------------------------
;; アカウント乗っ取り発生後、正しい対応の順番を答える。

(def incident-response-steps
  [:disconnect :change-password :enable-2fa :report])

(def incident-response-labels
  {:disconnect "ネットワークを切断する"
   :change-password "パスワードを変更する"
   :enable-2fa "二段階認証を有効にする"
   :report "運営や学校に報告する"})

(def incident-response-round
  {:minigame :sequence-recall
   :label "アカウント乗っ取り発生後、正しい対応の順番は?"
   :sequence incident-response-steps
   :step-labels incident-response-labels})

(def sample-match
  "1回分のVAMOS A BAILARパーティで遊ぶ、3種のミニゲームを順に並べたセット
   (round-spec自体には入力キー(:guesses/:picks/:answers)はまだ無い --
   host adapterがプレイヤーごとの入力を集めてassocしてから
   `core/play-round`に渡す)。"
  [notification-badge-round phishing-url-round incident-response-round])
