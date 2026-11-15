#!/system/bin/sh
# Rolls back the Google Play system update that installed a Bluetooth module that breaks Pokemon GO postcards to the
# Nintendo Switch (module 373041380 and later stop reporting MTU changes to GATT servers). No root needed.
#
# Usage, from a computer with adb (Command Prompt, PowerShell, or a macOS/Linux terminal; on Windows, type curl.exe):
#   curl -fsSL https://raw.githubusercontent.com/Mygod/pogoplusle/master/tools/rollback-bluetooth-update.sh | adb shell "tr -d '\r' | sh -s"
# This only shows what would be rolled back. Run it again with "sh -s commit" to roll back and reboot.
# A downloaded copy also works: adb push it to /data/local/tmp/ and run adb shell sh /data/local/tmp/<file> [commit].
#
# Rolling back restores every module from that Google Play system update, including their security fixes, and
# Google Play will install an update again later. Source: https://github.com/Mygod/pogoplusle/blob/master/tools/RollbackBluetoothUpdate.java
if [ "$(id -u)" != 2000 ]; then
    echo "Run this through adb shell, not as root or from an app." >&2
    exit 2
fi
commit=
for arg; do [ "$arg" = commit ] && commit=1; done
dex=/data/local/tmp/rollback-bluetooth-update.$$.dex
trap 'rm -f "$dex"' EXIT
base64 -d > "$dex" <<'EOF' || exit 1
@DEX_BASE64@
EOF
CLASSPATH="$dex" app_process /system/bin be.mygod.pogoplusplus.rollback.RollbackBluetoothUpdate "$@" || exit
if [ -n "$commit" ]; then
    rm -f "$dex"
    echo "Rebooting in 5 seconds to finish rolling back..."
    sleep 5
    reboot
fi
