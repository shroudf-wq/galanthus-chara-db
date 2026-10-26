# Galanthus キャラDB

キャラのステータス・評価（全キャラ中のパーセントランク）・タイプ・スキル・アビリティを検索・並べ替えでき、自分の所持状況・作成済み・アイコンを管理できるWebアプリです。

- サーバー不要の静的サイトです。GitHub Pages などにそのまま置けば動きます。
- 所持状況・アイコン・自分で追加や編集した内容は、各利用者のブラウザ（IndexedDB）にだけ保存されます。
- 「登録・管理」タブのバックアップで、別の端末へ移せます。
- スマホではホーム画面に追加するとアプリとして起動でき、オフラインでも使えます。

## ファイル
- `index.html` … アプリ本体
- `data/chars.json` … キャラの基本データ（`updated` に更新日）
- `data/details.json` … スキル・アビリティなど（キーはNo.）
- `sw.js` / `manifest.webmanifest` / `icons/` … オフライン対応とホーム画面用

## キャラデータの更新
1. `data/chars.json` と `data/details.json` を更新する
2. `data/chars.json` の `rev` と `data/version.json` の `rev` を、同じ数字で1つ上げる（`updated` も更新）
3. main ブランチに push する

アプリ（Web版・Windows版とも）は起動時に `version.json` を GitHub から確認し、`rev` が新しければ最新データを取得して切り替えます。
取得したデータは端末に保存されるので、次回からはオフラインでも最新データで起動します。
利用者の所持状況・アイコン・自分で編集した値はそのまま残ります（編集した値は、そのキャラについては利用者の値が優先されます）。

## Windows版
`desktop/` にElectron用の `main.js` と `package.json` があります。`desktop/web/` にこのリポジトリの `index.html`（Service Worker登録を除いたもの）・`data/`・`icons/` を置いて、`@electron/packager` で `--platform=win32 --arch=x64` を指定してビルドします。

## データの出典
ステータス等は「FLOWER KNIGHT GIRL Wiki*」（wikiwiki.jp/flower2ch）と「フラワーナイトガール攻略まとめwiki」（攻略wiki.com）の公開情報をもとにしています。
キャラの画像は含みません（アイコンは各利用者が自分のスクショから端末内に登録します）。
