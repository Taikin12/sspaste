# PicPaste

[English](README_EN.md) | 日本語

Minecraft 26.3 用のクライアントサイド Fabric MOD です。チャット欄で `Ctrl+V`（Mac は `Cmd+V`）すると、クリップボードの画像をアップロードして URL を入力欄に挿入します。F2 スクリーンショット（F1 で HUD を消したものも含む）は自動でシステムクリップボードにもコピーされます。

![PicPaste demo](docs/picpaste-demo.gif)

- クリップボードにテキストがある場合は、通常のテキスト貼り付けを優先します。
- ビットマップ画像（`DataFlavor.imageFlavor` / `image/png`）と画像ファイルに対応しています。
- アップロードはバックグラウンドで実行し、完了時にカーソル位置へ URL を挿入します。チャットを閉じていた場合は URL をクリップボードにコピーしてチャットに表示します。
- F2 スクリーンショットをクリップボードへコピーするので、F1 のきれいなスクショをそのまま貼り付けられます。
- チャット内の画像 URL にホバーするとツールチップでプレビューします（`showChatImagePreviews`、デフォルト ON）。

## アップロード先

| `uploader` | 動作 |
| --- | --- |
| `catbox`（デフォルト） | catbox.moe（トークン不要） |
| `auto` | Imgur 設定時は Imgur → Gyazo → ImgBB → catbox.moe |
| `gyazo` | Gyazo（アクセストークン） |
| `imgbb` | ImgBB（API キー） |
| `gzo` | gzo.ai 匿名（キー不要、24 時間で消える） |
| `imgur` | Imgur（既存の Client-ID がある場合のみ） |

Imgur は新規 Client-ID 登録が停止しているため、既存の Client-ID が必要です。

- **Gyazo**: <https://gyazo.com/oauth/applications> でアプリを作成し、アクセストークンを発行します。画像は Gyazo アカウントに保存されます。
- **ImgBB**: <https://api.imgbb.com/> で無料 API キーを取得します。アップロードは恒久的です。
- **gzo.ai**: キー不要。明示的に選択すると使えます。24 時間で消えます（ログイン時の 7 日保存は未実装）。
- **imgbox**: 公開アップロード API がないため非対応です。
- **Catbox**: キー不要。デフォルトのアップロード先です。

設定ファイル: `config/picpaste.json`（初回起動時に生成）。Mod Menu と Cloth Config が入っている場合は **Mod Menu > PicPaste > Config** からも設定できます。画面はゲーム言語（日本語/英語）に追従し、Gyazo トークン取得へのリンクも表示します。

```json
{
  "uploader": "catbox",
  "imgurClientId": "",
  "gyazoAccessToken": "",
  "imgbbApiKey": "",
  "maxUploadMegabytes": 10,
  "showChatFeedback": true,
  "copyScreenshotToClipboard": true,
  "showChatImagePreviews": true,
  "chatImagePreviewWidth": 160,
  "chatImagePreviewHeight": 100,
  "chatImageTextureWidth": 1280,
  "chatImageTextureHeight": 720
}
```

環境変数でも設定できます（旧 `CHATIMAGEUPLOADER_*` / `CHATIMGUR_*` も読みます）。

- Imgur Client-ID: `PICPASTE_CLIENT_ID`
- Gyazo: `PICPASTE_GYAZO_TOKEN`
- ImgBB: `PICPASTE_IMGBB_API_KEY`

## チャット画像プレビュー

Imgur / Gyazo / ImgBB / gzo.ai / Catbox の直リンクと、拡張子なしのページ URL に対応しています。ページ URL は OGP（`og:image`）や既知の URL パターンから実画像を解決します。URL をクリックするとブラウザで開きます。

Imgur のアルバム／ギャラリーは、Client-ID が設定されている場合に最大 4 枚を 2x2 グリッドで表示します（5 枚以上は「+N」）。認証情報がない場合はカバー画像 1 枚のみです。

HTTPS のみ、最大 8 MB。プレビュー画像は高解像度テクスチャ（デフォルト 1280x720）として取得し、小さい表示サイズ（デフォルト 160x100）にリニアフィルタで縮小します。表示サイズとテクスチャ解像度は設定画面で個別に変更できます（`chatImagePreviewWidth` / `chatImagePreviewHeight`、`chatImageTextureWidth` / `chatImageTextureHeight`）。テクスチャ解像度を上げるほどシャープになりますが、メモリ使用量が増えます。プレビュー自体は `showChatImagePreviews` で無効化できます。

## 免責

この MOD は Mojang Studios、Microsoft、Imgur、Gyazo / Helpfeel、ImgBB、gzo.ai、Catbox とは関係ありません。各製品名・ロゴ・商標はそれぞれの所有者に帰属します。

## プライバシー

アップロードした画像は選択した第三者のホストに送信され、公開 URL になります。トークンや API キーは `config/picpaste.json` にローカル保存され、選択したホストにのみ送信されます。チャットプレビューは対応ホストから画像を取得します。作者にデータは送信されません。

## 移行

MOD ID は `picpaste` です。既存の `config/chatimageuploader.json` または `config/chatimgur.json` は `config/picpaste.json` に自動コピーされます。古い `chatimageuploader-*.jar` / `chatimgur-*.jar` は mods フォルダから削除してください。

## 注意

- アップロードした画像は公開 URL になります。
- `maxUploadMegabytes` を超える画像はアップロード前に縮小されます（PNG）。
- Java 25 と Fabric API 0.165.0+26.3 が必要です。

## ビルド

```sh
./gradlew build
# -> build/libs/picpaste-2.7.0.jar
```
