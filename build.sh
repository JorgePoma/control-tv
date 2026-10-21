#!/usr/bin/env bash
# Builds out/control-web.apk with only javac, d8, aapt2 and apksigner (no Gradle, no Android Studio).
# Needs a JDK 17+ on PATH. The Android build tools (~150 MB) are downloaded into .tools/ on first run.
#
# Usage: KEYSTORE=/path/key.jks KS_PASS=secret ./build.sh
#        KS_PASS is required. Without KEYSTORE a new key is made in out/ with that password; an APK
#        signed with a different key will not install over the one already on the TV (uninstall it first).
set -euo pipefail
cd "$(dirname "$0")"

TOOLS=.tools
BT=$TOOLS/android-14
JAR=$TOOLS/android-34/android.jar

if [ ! -f "$JAR" ] || [ ! -d "$BT" ]; then
    mkdir -p "$TOOLS"
    echo "Descargando herramientas de Android..."
    curl -sSL -o "$TOOLS/bt.zip" https://dl.google.com/android/repository/build-tools_r34-windows.zip
    curl -sSL -o "$TOOLS/pf.zip" https://dl.google.com/android/repository/platform-34-ext7_r03.zip
    (cd "$TOOLS" && unzip -q bt.zip && unzip -q pf.zip && rm bt.zip pf.zip)
fi

win() { cygpath -w "$1" 2>/dev/null || echo "$1"; }

rm -rf out/classes out/dex out/*.apk out/res.zip
mkdir -p out/classes out/dex

javac --release 17 -cp "$JAR" -d out/classes $(find src/java -name '*.java')
cmd //c "$(win $BT/d8.bat)" --release --min-api 26 --lib "$(win $JAR)" --output "$(win out/dex)" \
    $(find out/classes -name '*.class' -exec cygpath -w {} \;)
"$BT/aapt2.exe" compile --dir src/res -o out/res.zip
"$BT/aapt2.exe" link -o out/base.apk -I "$JAR" --manifest src/AndroidManifest.xml \
    -R out/res.zip --auto-add-overlay -A src/assets
(cd out/dex && "../../$BT/aapt.exe" add ../base.apk classes.dex >/dev/null)
"$BT/zipalign.exe" -f 4 out/base.apk out/aligned.apk

KEYSTORE=${KEYSTORE:-out/key.jks}
: "${KS_PASS:?define KS_PASS con la contraseña de la llave}"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias control \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Control Web" >/dev/null 2>&1
fi
cmd //c "$(win $BT/apksigner.bat)" sign --ks "$(win $KEYSTORE)" --ks-pass "pass:$KS_PASS" \
    --out "$(win out/control-web.apk)" "$(win out/aligned.apk)" 2>/dev/null
rm -f out/base.apk out/aligned.apk
ls -l out/control-web.apk
