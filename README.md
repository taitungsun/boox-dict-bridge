# boox-dict-bridge

A small Android app for The Economist app on Boox e-readers. Define opens the Boox dictionary popup, and Translate shows a Gemini translation of the whole paragraph into Taiwan Traditional Chinese.

Since version 4.108.0, the Economist app shows its own text-selection menu. Its Translate and Define buttons send `ACTION_TRANSLATE` and `ACTION_DEFINE`. Nothing on a Boox handles those, so tapping them crashed the app. This app handles both.

Translate is tested on a Boox Page (Android 11). v1.3 (Define and the dictionary) is also tested on a Boox Go 7 (Android 13) and a Boox Go 10.3 (Android 12).

## Install

Build the APK (see Build) and install it. The APK in Releases is v1.3, which has Define and a dictionary-only Translate.

Boox freezes new apps, and a frozen app does nothing. It also freezes them again at every reboot. To stop that, open the Apps tab, tap the snowflake icon at the top, and switch off freezing for "Dictionary". Note that this list uses the name "Dictionary", not the "Dict Bridge" name shown under the app's icon.

You can also unfreeze it over adb, but Boox will freeze it again at the next reboot:

    adb shell pm enable io.github.taitungsun.dictbridge

### Translate setup

1. Open "Dict Bridge", paste your Gemini API key and tap Save.
2. Turn on "Dict Bridge paragraphs" in Settings > Accessibility. It only reads text in the Economist app, so Translate can show the whole paragraph instead of just the selected words.
3. Optional, over adb. The Boox NaviBall switch turns other accessibility services off. With this permission, the app switches its service back on by itself:

       adb shell pm grant io.github.taitungsun.dictbridge android.permission.WRITE_SECURE_SETTINGS

4. Optional, for a book-style Chinese font. Download `NotoSerifTC-Regular.otf` from the Noto CJK project and copy it to the app's folder:

       adb push NotoSerifTC-Regular.otf /sdcard/Android/data/io.github.taitungsun.dictbridge/files/

## Use

Long-press a word in an Economist article and tap Translate. The screen shows the paragraph on top and its translation below. Tap the back button at the top left to return to the article. A− and A+ change the text size. A paragraph that was translated before shows up at once.

Define under the ⋮ menu shows the small dictionary popup.

The app also appears as "Dictionary" in the share menu. This is needed because the Economist app can only reach apps that accept shared text.

Translate sends the paragraph, together with the translation instructions and the date, to the Gemini API with your key. The app sends nothing anywhere else.

## Build

Needs JDK 17 and the Android SDK (platform 36, build-tools 36.0.0). There's no Gradle; `build.sh` calls javac, d8, aapt2 and apksigner directly and writes `build/dict-bridge.apk`.

## License

MIT
