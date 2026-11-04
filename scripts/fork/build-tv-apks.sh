#!/usr/bin/env bash
# Clean Your TV RU (fork): build the two TV apps the phone app carries and pins, signed with the fork's key:
#   - the launcher (com.gathin.tvhome) from the upstream submodule with tv-launcher/*.patch applied (the widget
#     panel), into android/platform/src/main/assets/bundled/cleanyourtv-launcher.apk;
#   - the TV curtain (:tv:curtain, with its Russian strings), into .../bundled/cleanyourtv-curtain.apk;
# then write their size, sha256 and signer into core/trust Pins.kt (PinnedLauncher, PinnedCurtain), which the
# verifyLauncherPin and verifyCurtainPin tasks check. Commit the two APKs and Pins.kt together.
#
# Needs: JDK 21, the Android SDK (platforms 34 and 37), apksigner on PATH (or APKSIGNER), and the key:
# CYT_ADDON_KEYSTORE (PKCS12) with CYT_ADDON_KEYSTORE_PASSWORD or CYT_ADDON_KEYSTORE_PASSWORD_FILE.
# The submodule is patched for the build and put back afterwards.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
apksigner=${APKSIGNER:-apksigner}
alias=${CYT_ADDON_KEY_ALIAS:-cleanyourtv}
: "${CYT_ADDON_KEYSTORE:?set CYT_ADDON_KEYSTORE}"
if [ -n "${CYT_ADDON_KEYSTORE_PASSWORD_FILE:-}" ]; then pass="file:$CYT_ADDON_KEYSTORE_PASSWORD_FILE"
else : "${CYT_ADDON_KEYSTORE_PASSWORD:?set CYT_ADDON_KEYSTORE_PASSWORD}"; pass="env:CYT_ADDON_KEYSTORE_PASSWORD"; fi
sub=third_party/cleanyourtv
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

restore() { git -C "$sub" checkout -q -- . && git -C "$sub" clean -qfd launcher/app/src; }
trap restore EXIT
git -C "$sub" diff --quiet || { echo "$sub has changes of its own; commit or drop them first" >&2; exit 1; }
for p in tv-launcher/*.patch; do git -C "$sub" apply "$root/$p"; done

echo "== launcher"
[ -f "$sub/launcher/local.properties" ] || [ -n "${ANDROID_HOME:-}" ] || { echo "set ANDROID_HOME" >&2; exit 1; }
(cd "$sub/launcher" && ./gradlew --no-daemon --console=plain testReleaseUnitTest assembleRelease)
launcher_signer=$(sign "$sub/launcher/app/build/outputs/apk/release/app-release.apk" "$bundled/cleanyourtv-launcher.apk")
code=$(sed -n 's/^ *versionCode \([0-9]*\)$/\1/p' "$sub/launcher/app/build.gradle" | head -1)
name=$(sed -n "s/^ *versionName '\([^']*\)'$/\1/p" "$sub/launcher/app/build.gradle" | head -1)
pin PinnedLauncher UPSTREAM_COMMIT "\"$(git -C "$sub" rev-parse HEAD)\""
pin PinnedLauncher VERSION_CODE "${code}L"
pin PinnedLauncher VERSION_NAME "\"$name\""
pin PinnedLauncher SIZE "$(wc -c < "$bundled/cleanyourtv-launcher.apk" | tr -d ' ')L"
pin PinnedLauncher SHA256 "\"$(sha256sum "$bundled/cleanyourtv-launcher.apk" | cut -d' ' -f1)\""
pin PinnedLauncher SIGNER_SHA256 "\"$launcher_signer\""

echo "== curtain"
./gradlew --no-daemon --console=plain :tv:curtain:assembleRelease
curtain_signer=$(sign tv/curtain/build/outputs/apk/release/curtain-release-unsigned.apk "$bundled/cleanyourtv-curtain.apk")
[ "$curtain_signer" = "$launcher_signer" ] || { echo "curtain and launcher signers differ" >&2; exit 1; }
pin PinnedCurtain SIZE "$(wc -c < "$bundled/cleanyourtv-curtain.apk" | tr -d ' ')L"
pin PinnedCurtain SHA256 "\"$(sha256sum "$bundled/cleanyourtv-curtain.apk" | cut -d' ' -f1)\""

echo "launcher $name ($code) and curtain signed by $launcher_signer; Pins.kt updated"
