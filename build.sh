#!/bin/bash
# Build Galeriku v1.2 tanpa Gradle, memakai toolchain yang sudah ada di VM.
# Toolchain: ~/workspace/android-toolchain (JDK 17, build-tools 34, platform android-34)
set -e
cd "$(dirname "$0")"
TOOL=~/workspace/android-toolchain
JDK="$TOOL/jdk-17.0.20.1+1/bin"
SDK="$TOOL/sdk"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
export PATH="$JDK:$PATH"

rm -rf build
mkdir -p build/classes build/dex

# 1. Paket manifest + assets (font Font Awesome) -> APK belum bertanda
"$BT/aapt" package -f -M AndroidManifest.xml -I "$PLATFORM" -A assets -F build/unsigned.apk

# 2. Kompilasi Java
"$JDK/javac" -encoding UTF-8 -source 8 -target 8 -cp "$PLATFORM" \
  -d build/classes \
  $(find src -name "*.java")

# 3. DEX
"$BT/d8" --lib "$PLATFORM" --output build/dex build/classes/com/gobel/galeriku/*.class

# 4. Masukkan DEX ke APK
cp build/unsigned.apk build/app.apk
cp build/dex/classes.dex build/classes.dex
(cd build && "$BT/aapt" add app.apk classes.dex >/dev/null)

# 5. Align + tanda tangan debug (kunci lokal proyek, bukan kunci Play)
if [ ! -f debug.keystore ]; then
  "$JDK/keytool" -genkeypair -keystore debug.keystore -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/zipalign" -f 4 build/app.apk build/aligned.apk
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out Galeriku.apk build/aligned.apk
"$BT/apksigner" verify Galeriku.apk
"$BT/aapt" dump badging Galeriku.apk | head -20
ls -lh Galeriku.apk
