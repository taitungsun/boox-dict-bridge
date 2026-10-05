#!/bin/bash
# Build + sign Dict Bridge without Gradle. Output: build/dict-bridge.apk
set -e
cd "$(dirname "$0")"
SDK=~/dev-tools/android-sdk; BT=$SDK/build-tools/36.0.0; JAR=$SDK/platforms/android-36/android.jar
rm -rf build && mkdir -p build/classes build/dex
javac --release 11 -classpath "$JAR" -d build/classes $(find src -name '*.java')
$BT/d8 --min-api 29 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
$BT/aapt2 link -I "$JAR" --manifest AndroidManifest.xml -o build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
$BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --out build/dict-bridge.apk build/aligned.apk
echo built build/dict-bridge.apk
