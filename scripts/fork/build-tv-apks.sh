#!/usr/bin/env bash
# Clean Your TV RU (fork): build the TV curtain (:tv:curtain, with its Russian strings) signed with the fork's key
# into android/platform/src/main/assets/bundled/cleanyourtv-curtain.apk and write its size, sha256 and signer into
# core/trust Pins.kt (PinnedCurtain), which verifyCurtainPin checks. Commit the APK and Pins.kt together.
# The launcher is NOT rebuilt: the fork carries the original app's own signed launcher (as 0.5.0-beta1 does), so a
# TV the original app set up can be updated; after an upstream merge its APK and PinnedLauncher come from upstream.
# (tv-launcher/panel.patch, the two-card widget panel, is kept for a possible own launcher; it is not built.)
#
# Needs: JDK 21, the Android SDK (platforms 34 and 37), apksigner on PATH (or APKSIGNER), and the key:
# CYT_ADDON_KEYSTORE (PKCS12) with CYT_ADDON_KEYSTORE_PASSWORD or CYT_ADDON_KEYSTORE_PASSWORD_FILE.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
apksigner=${APKSIGNER:-apksigner}
alias=${CYT_ADDON_KEY_ALIAS:-cleanyourtv}
: "${CYT_ADDON_KEYSTORE:?set CYT_ADDON_KEYSTORE}"
if [ -n "${CYT_ADDON_KEYSTORE_PASSWORD_FILE:-}" ]; then pass="file:$CYT_ADDON_KEYSTORE_PASSWORD_FILE"
else : "${CYT_ADDON_KEYSTORE_PASSWORD:?set CYT_ADDON_KEYSTORE_PASSWORD}"; pass="env:CYT_ADDON_KEYSTORE_PASSWORD"; fi
bundled=android/platform/src/main/assets/bundled
pins=core/trust/src/main/kotlin/tv/cleanyour/core/trust/Pins.kt

sign() {  # unsigned-in signed-out
  "$apksigner" sign --ks "$CYT_ADDON_KEYSTORE" --ks-type PKCS12 --ks-key-alias "$alias" --ks-pass "$pass" \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out "$2" "$1"
  "$apksigner" verify --print-certs "$2" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1 | tr -d '\r'
}

# Rewrites one `const val NAME = value` inside `object OBJ { ... }` of Pins.kt.
pin() {  # object name value
  "${PYTHON:-python3}" - "$pins" "$1" "$2" "$3" <<'PY'
import re, sys
path, obj, name, value = sys.argv[1:]
s = open(path, encoding="utf-8").read()
start = s.index("object %s {" % obj)
end = s.index("\n}", start)
block = s[start:end]
new, n = re.subn(r"(const val %s = )[^\n]*" % name, lambda m: m.group(1) + value, block, count=1)
assert n == 1, (obj, name)
open(path, "w", encoding="utf-8", newline="").write(s[:start] + new + s[end:])
PY
}

echo "== curtain"
./gradlew --no-daemon --console=plain :tv:curtain:assembleRelease
curtain_signer=$(sign tv/curtain/build/outputs/apk/release/curtain-release-unsigned.apk "$bundled/cleanyourtv-curtain.apk")
pin PinnedCurtain SIGNER_SHA256 "\"$curtain_signer\""
pin PinnedCurtain SIZE "$(wc -c < "$bundled/cleanyourtv-curtain.apk" | tr -d ' ')L"
pin PinnedCurtain SHA256 "\"$(sha256sum "$bundled/cleanyourtv-curtain.apk" | cut -d' ' -f1)\""

echo "curtain signed by $curtain_signer; Pins.kt updated"
