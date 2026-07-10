# GHOST HACKER: VAMOS A BAILAR

![test](https://github.com/com-junkawasaki/ghosthacker-vamos/actions/workflows/test.yml/badge.svg)

Ghost Hacker ゲームポートフォリオ第9弾。設計は
[ADR-2607023200](../../../90-docs/adr/2607023200-ghosthacker-game-portfolio-flow.md)
（superproject `com-junkawasaki/root`、addendum 2）を参照。

[Ghost Hacker](https://github.com/com-junkawasaki/ghosthacker)（既存カノン: Ren/Nei、
「情報は物理だ」、情報場、Ghost Battle / Daemon Battle）を土台に、FreeTEMPOの
『The World Is Echoed』（2003）収録曲 "Vamos a Bailar" に由来する、10ジャンル展開の
第9弾。

## コンセプト

- **ジャンル**: パーティゲーム
- **主人公**: Ren & Nei + 事務所つながりの面々総出演（大人数ミニゲーム集）
- **コアループ**: 放課後に居残ったroster（Ren・Nei・Kota・Meiの4人、既存
  カノンキャラクターのみ使用 -- 新規オリジナルキャラクターは追加しない）
  全員が、同じ短いミニゲームを1ラウンドずつ順番にこなす。各ラウンドの
  判定は`:tally`（パーティ合計点）へ加算され、全ラウンド終了後にランキング
  と勝者(同点なら複数)が決まる。「パーティゲーム」らしさは、1つ1つの
  ミニゲームを深くすることではなく、trivialに単純な短い遊びを何本か
  並べることで出す、というこのポートフォリオの一貫した控えめさ
  （ADR-2607023200 addendum 2）にならっている。

  3種のミニゲーム、いずれも「1回の単純な判定」だけで深さを持たせない:

  - **reaction-tap**（通知バッジ当て）: 一瞬示された(host演出。pureな
    core自体はタイミングに関与しない)目標の数字に対し、できるだけ近い
    数字を入力する。ズレの絶対値で`:bullseye`/`:close`/`:miss`を判定。
  - **quick-pick**（本物のURLはどれ?）: 数個の選択肢のうち正解は1つだけ
    -- Ghost Hacker既存カノンの`:gh/educationalContent`（実在のセキュ
    リティ教育、フィッシングURL見分け）をそのまま題材にした二択判定
    （`:correct`/`:incorrect`）。
  - **sequence-recall**（アカウント乗っ取り対応の正しい順番）: 短い固定
    手順（切断→パスワード変更→二段階認証→報告、これも実在のセキュリティ
    教育が題材）を正しい順に答える。一致した位置の数で
    `:perfect`/`:partial`/`:miss`を判定。

## 実装範囲

`src/ghosthacker_vamos/core.cljc` -- pure、host-free。判定/state核:

- `judge-reaction-tap`/`judge-quick-pick`/`judge-sequence-recall` -- 3種の
  ミニゲームそれぞれの判定関数
- `play-reaction-tap-round`/`play-quick-pick-round`/`play-sequence-recall-round`
  -- roster全員分の入力を1ラウンドとして`:tally`へ適用
- `play-round` -- round-specの`:minigame`で上記3つへ委譲する共通
  ディスパッチャ
- `ranking`/`winners`/`summary` -- リザルト画面向けのランキング・勝者
  （同点は複数人）・サマリ
- `play-party`/`play-party-summary` -- roster + 全ラウンド分のround-spec
  から最終stateを作る一括再生

`src/ghosthacker_vamos/roster.cljc` -- サンプルのroster（4人）と、3ラウンド
分のサンプルデータ（`sample-match`）。

**プレイ可能な最小プロトタイプ**として `src/ghosthacker_vamos/terminal.clj`
がある。FLOW/HARMONYと違い実時間のビート判定が無いため、`future`/agent
スレッドプールを一切使わない素朴な「順番に手渡し」REPLループ。各ラウンドで
roster全員が順に入力し、EOF(または途中打ち切り)になればそこまでのstateで
リザルトを出す。

**ブラウザで遊べるホストアダプタ**が `src/ghosthacker_vamos/web.cljs`
（reagent、ADR-2607100900 follow-up (b)）: TUNING/ECHOESと同じ低複雑度側の
構成（Web Audio不要）。全ての入力をボタンクリックだけで完結させている
（reaction-tapは目標値からのズレ量を選ぶプリセットボタン、quick-pickは
選択肢ボタン、sequence-recallは手順ボタンをクリックした順に積み上げる方式）
-- 1台の端末を手渡ししながら遊ぶ「pass-the-device」パーティゲームUXとして
自然であると同時に、キーボード入力のシミュレーションを要さずheadless DOM
でのクリックのみの検証がしやすい。

## 開発

```bash
clojure -M:test
```

Lint（clj-kondo、Clojars経由でHomebrew等の別インストール不要）:

```bash
clojure -M:lint
```

`main`へのpush/PRで `.github/workflows/test.yml` が自動でテスト+lintを実行する。

ターミナルで遊んでみる:

```bash
clojure -M -m ghosthacker-vamos.terminal
```

ブラウザで遊んでみる（`npm install`は初回のみ）:

```bash
npm install
npx shadow-cljs watch app   # http://localhost:8302 で自動リロード開発
npx shadow-cljs release app # public/ に静的バンドルをビルド(デプロイ可能)
```

変更履歴は [CHANGELOG.md](CHANGELOG.md)。

## ライセンス

MIT License -- [LICENSE](LICENSE) 参照。
