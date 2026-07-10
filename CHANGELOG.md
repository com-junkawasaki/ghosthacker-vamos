# Changelog

pure `.cljc` 全員参加パーティミニゲーム核（`ghosthacker-vamos.core`）と、
それを使うプロトタイプ実装の変更履歴（ADR-2607023200 portfolio title #9）。

## Unreleased

- 初期実装: `core.cljc`（reaction-tap/quick-pick/sequence-recallの3ミニ
  ゲーム判定、roster全員分の入力を1ラウンドとして適用する共有tally、
  `play-round`ディスパッチャ、ranking/winners/summary）、`roster.cljc`
  （サンプルroster4人 + サンプル3ラウンド`sample-match`、quick-pick/
  sequence-recallはGhost Hacker既存カノンの`:gh/educationalContent`を
  題材化）、`terminal.clj`（プレイ可能な「順番に手渡し」REPLプロトタイプ、
  future/スレッドプール不使用）、`web.cljs`（ブラウザhostアダプタ、
  reagent、ADR-2607100900 follow-up (b)、全入力がボタンクリックのみで
  完結するpass-the-device UX）。headless DOM上で実クリック操作による
  4人×3ラウンドの通し（各ラウンドの判定→次へ→最終リザルト画面→もう一度で
  初期状態に復帰）を検証済み。
