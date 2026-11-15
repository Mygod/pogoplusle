#!/bin/sh
# Builds rollback-bluetooth-update.sh: compiles RollbackBluetoothUpdate.java to a dex and embeds it into
# rollback-bluetooth-update.template.sh.
set -eu
cd "$(dirname "$0")"
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
platform=$(ls -d "$sdk"/platforms/android-[0-9]* | sort -V | tail -n 1)
build_tools=$(ls -d "$sdk"/build-tools/[0-9]* | sort -V | tail -n 1)
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$platform/android.jar" -d "$out/classes" RollbackBluetoothUpdate.java
"$build_tools/d8" --release --min-api 29 --lib "$platform/android.jar" --output "$out" "$out"/classes/be/mygod/pogoplusplus/rollback/*.class
base64 -w 76 "$out/classes.dex" > "$out/dex.b64"
awk -v b64="$out/dex.b64" '$0 == "@DEX_BASE64@" { while ((getline line < b64) > 0) print line; next } { print }' \
    rollback-bluetooth-update.template.sh > rollback-bluetooth-update.sh
chmod +x rollback-bluetooth-update.sh
echo "Built rollback-bluetooth-update.sh ($(wc -c < "$out/classes.dex") byte dex)"
