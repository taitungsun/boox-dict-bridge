# boox-dict-bridge

A small Android app that brings back the Boox dictionary popup when you long-press a word in The Economist app.

Since version 4.108.0, the Economist app shows its own text-selection menu. Its Translate and Define buttons send `ACTION_TRANSLATE` and `ACTION_DEFINE`. Nothing on a Boox handles those, so tapping them crashed the app. This app receives both and opens the selected word in the Onyx dictionary (`com.onyx.dict`).

Tested on a Boox Go 7 (Android 11) with Economist 4.108.0.

## Install

Download the APK from Releases and install it.

Boox freezes new apps, and a frozen app does nothing. It also freezes them again at every reboot. To stop that, open the Apps tab, tap the snowflake icon at the top, and switch off freezing for "Dictionary". Note that this list uses the name "Dictionary", not the "Dict Bridge" name shown under the app's icon.

You can also unfreeze it over adb, but Boox will freeze it again at the next reboot:

    adb shell pm enable io.github.taitungsun.dictbridge

## Use

Long-press a word in an Economist article, then tap Translate. Define under the ⋮ menu also works.

The app also appears as "Dictionary" in the share menu. This is needed because the Economist app can only reach apps that accept shared text.

The "Dict Bridge" icon opens a short status screen. The icon is there so the app shows up in the Boox freeze settings.

## Build

Needs JDK 17 and the Android SDK (platform 36, build-tools 36.0.0). There's no Gradle; `build.sh` calls javac, d8, aapt2 and apksigner directly and writes `build/dict-bridge.apk`.

## License

MIT
