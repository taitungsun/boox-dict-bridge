# boox-dict-bridge

A small Android app for The Economist app on Boox e-readers. Define opens the Boox dictionary popup, and Translate shows a Gemini translation of the whole paragraph into Taiwan Traditional Chinese.

Since version 4.108.0, the Economist app shows its own text-selection menu. Its Translate and Define buttons send `ACTION_TRANSLATE` and `ACTION_DEFINE`. Nothing on a Boox handles those, so tapping them crashed the app. This app handles both.

Tested on a Boox Page (Android 11), Go 7 (Android 13), Go 10.3 (Android 12) and Nova3 (Android 10). On the Nova3, whose dictionary app is older, Define opens that dictionary's own popup.

## Install

Download `dict-bridge-v1.4.3.apk` from Releases and install it.

Boox freezes new apps, and a frozen app does nothing. It also freezes them again at every reboot. To stop that, open the Apps tab, tap the snowflake icon at the top, and switch off freezing for "Dictionary". Note that this list uses the name "Dictionary", not the "Dict Bridge" name shown under the app's icon.

You can also unfreeze it over adb, but Boox will freeze it again at the next reboot:

    adb shell pm enable io.github.taitungsun.dictbridge

### Translate setup

1. Open "Dict Bridge" (its settings screen is in Chinese), paste your Gemini API key into the key field and tap the button below it to save.
2. Turn on "Dict Bridge paragraphs" in Settings > Accessibility. It reads text only in the Economist app and only at the moment you tap Translate, so Translate can show the whole paragraph instead of just the selected words.
3. Optional, over adb. The Boox NaviBall switch turns other accessibility services off. With this permission, the app switches its service back on by itself:

       adb shell pm grant io.github.taitungsun.dictbridge android.permission.WRITE_SECURE_SETTINGS

The first time Translate runs, the app downloads a book-style Chinese font (Noto Serif TC, about 8 MB) from the Noto CJK project on GitHub. Until it arrives, the translation shows in the normal system font.

## Use

Long-press a word in an Economist article and tap Translate. The screen shows the paragraph on top and its translation below. Tap the back button at the top left to return to the article. A− and A+ change the text size. A paragraph that was translated before shows up at once.

If the paragraph service has just been switched back on, the first Translate shows a short loading message and the screen flashes once before the paragraph appears.

Define under the ⋮ menu shows the small dictionary popup.

The app also appears as "Dictionary" in the share menu. This is needed because the Economist app can only reach apps that accept shared text.

Translate sends the paragraph, together with the translation instructions and the date, to the Gemini API with your key. Apart from that and the one-time font download, the app sends nothing and downloads nothing.

## Build

Needs JDK 17 and the Android SDK (platform 36, build-tools 36.0.0). There's no Gradle; `build.sh` calls javac, d8, aapt2 and apksigner directly and writes `build/dict-bridge.apk`.

## License

MIT
