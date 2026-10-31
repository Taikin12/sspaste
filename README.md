# PicPaste

PicPaste is a client-side Fabric mod (Minecraft 26.3). Paste an image into the chat box with
`Ctrl+V` (or `Cmd+V`): the image is uploaded and its URL is inserted into the
chat input. F2 screenshots (including clean screenshots taken with F1) are also
copied to the system clipboard automatically.

![PicPaste demo](docs/picpaste-demo.gif)

- Text paste is unchanged: if the clipboard contains plain text, the normal
  text paste happens.
- Images copied as a bitmap (`DataFlavor.imageFlavor` / `image/png`) or as an
  image file are supported.
- The upload runs in the background; the URL is inserted at the cursor when it
  finishes. If the chat screen was closed meanwhile, the URL is copied to the
  clipboard and shown in chat.
- F2 screenshots are copied to the clipboard, so you can take a clean F1
  screenshot and immediately paste it into chat.
- Hover an image URL in chat to show a thumbnail preview in a tooltip
  (`showChatImagePreviews`, default on).

## Hosts

| config `uploader` | behaviour |
| ----------------- | --------- |
| `catbox` (default) | catbox.moe (no token required) |
| `auto`            | Gyazo if a token is set, otherwise ImgBB if an API key is set, otherwise catbox.moe |
| `gyazo`           | Gyazo (access token) |
| `imgbb`           | ImgBB (API key) |
| `gzo`             | gzo.ai anonymous (no key, expires after 24 hours) |
| `imgur`           | Imgur (existing Client-ID or access token only) |

Imgur still works if you already have a Client-ID or a temporary access token
(the Siki method); new Client-ID registration is currently closed.

Gyazo requires an **access token**: create an application at
<https://gyazo.com/oauth/applications> and generate one. Uploads are saved to
your Gyazo account.

ImgBB requires a **free API key** from <https://api.imgbb.com/>. Uploads are
permanent.

gzo.ai works anonymously without a key and is selectable explicitly. Uploads
expire after 24 hours (the logged-in 7-day mode is not implemented).

imgbox has no public upload API (Siki uses its web upload page), so it is not
supported for automatic upload.

Config file: `config/picpaste.json` (created on first launch). If Mod Menu
and Cloth Config are installed, the same settings are available from
**Mod Menu > Chat Image Uploader > Config**. The screen follows the game
language (English/Japanese) and includes a clickable Gyazo token link.

```json
{
  "uploader": "catbox",
  "imgurClientId": "",
  "imgurAccessToken": "",
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

The Imgur Client-ID can also come from `PICPASTE_CLIENT_ID` or
`IMGUR_CLIENT_ID`, and the Imgur access token from
`PICPASTE_IMGUR_ACCESS_TOKEN` or `IMGUR_ACCESS_TOKEN`. The Gyazo token can
come from `PICPASTE_GYAZO_TOKEN` or `GYAZO_ACCESS_TOKEN`. The ImgBB key can
come from `PICPASTE_IMGBB_API_KEY` or `IMGBB_API_KEY`.

## Chat image previews

Direct image URLs and extensionless viewer pages from Imgur, Gyazo, ImgBB,
gzo.ai, and Catbox are previewed when you hover over them in chat (the same
tooltip approach as ChatImage). Viewer pages are resolved to the actual image
via Open Graph tags / known URL patterns. Clicking the URL still opens it in
the browser. Imgur albums/galleries with a configured Client-ID or access token
show up to 4 images in a 2x2 grid (with "+N" for the rest); without Imgur
credentials only the cover image is previewed. Only HTTPS URLs are loaded, up
to 8 MB; previews are
downloaded/scaled to a high-resolution texture (1280x720 by default) and drawn
at a smaller display size (160x100 by default) with linear filtering. Both are
separately adjustable in the config screen (`chatImagePreviewWidth`/`Height`
for the display, `chatImageTextureWidth`/`Height` for the texture resolution).
Higher texture resolution looks sharper but uses more memory. Disable previews
entirely with `showChatImagePreviews`.

## Credits

- Imgur temporary-token method documented by the [Siki wiki](https://wikiwiki.jp/siki-app/).

## Disclaimer

This mod is not affiliated with Mojang Studios, Microsoft, Imgur, Gyazo /
Helpfeel, ImgBB, gzo.ai, or Catbox. All product names, logos, and trademarks are
the property of their respective owners.

## Privacy

Images are sent to the selected third-party host when you upload them and become
publicly accessible URLs. Tokens and API keys are stored locally in
`config/picpaste.json` and are only sent to the selected host. Chat
previews fetch image URLs from the supported hosts; no data is sent to the mod
author.

## Migration

The mod id changed to `picpaste`. An existing `config/chatimageuploader.json`
or `config/chatimgur.json` is copied to `config/picpaste.json` automatically.
Remove old `chatimageuploader-*.jar` / `chatimgur-*.jar` files from the mods
folder to avoid duplicates.

## Notes

- Uploads are public. Anyone with the URL can view the image.
- Images larger than `maxUploadMegabytes` are downscaled (PNG) before upload.
- Requires Java 25 and Fabric API 0.165.0+26.3.

## Build

```sh
./gradlew build
# -> build/libs/picpaste-2.6.0.jar
```
