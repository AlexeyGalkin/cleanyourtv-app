#!/usr/bin/env bash
# Clean Your TV RU (fork): build every TV add-on in tv-addons/, sign it with the fork's key, and put it with its
# index.json into android/platform/src/main/assets/fork-addons/ (scripts/fork/ADDONS-CONTRACT.md), so the phone app
# carries it. CI runs this before the app build; the assets are generated, not committed.
#
# Needs: a JDK (17 or later), the Android SDK (platform 34), apksigner and aapt2 on PATH (or APKSIGNER / AAPT2),
# and the key: CYT_ADDON_KEYSTORE (a PKCS12 file), CYT_ADDON_KEYSTORE_PASSWORD (or _FILE), CYT_ADDON_KEY_ALIAS (default
# cleanyourtv). Fails when an APK's signer is not the pinned one.
set -euo pipefail

PIN=c02a76c8034b6ecd835dd88161e751fe5689c40b5f42d4715768769580a7051c
root=$(cd "$(dirname "$0")/../.." && pwd)
out="$root/android/platform/src/main/assets/fork-addons"
apksigner=${APKSIGNER:-apksigner}
aapt2=${AAPT2:-aapt2}
alias=${CYT_ADDON_KEY_ALIAS:-cleanyourtv}
: "${CYT_ADDON_KEYSTORE:?set CYT_ADDON_KEYSTORE}"
# The password from the environment (CI), or from a file (CYT_ADDON_KEYSTORE_PASSWORD_FILE, handy on Windows).
if [ -n "${CYT_ADDON_KEYSTORE_PASSWORD_FILE:-}" ]; then pass="file:$CYT_ADDON_KEYSTORE_PASSWORD_FILE"
else : "${CYT_ADDON_KEYSTORE_PASSWORD:?set CYT_ADDON_KEYSTORE_PASSWORD}"; pass="env:CYT_ADDON_KEYSTORE_PASSWORD"; fi

mkdir -p "$out"
rm -f "$out"/*.apk "$out"/*.idsig "$out/index.json"
entries=""
for dir in "$root"/tv-addons/*/; do
  [ -f "$dir/settings.gradle" ] || continue
  name=$(basename "$dir")
  echo "== $name"
  (cd "$dir" && ./gradlew --no-daemon --console=plain assembleRelease testReleaseUnitTest)
  unsigned="$dir/app/build/outputs/apk/release/app-release-unsigned.apk"
  badging=$("$aapt2" dump badging "$unsigned")
  pkg=$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)
  code=$(printf '%s\n' "$badging" | sed -n "s/^package: .* versionCode='\([0-9]*\)'.*/\1/p" | head -1)
  vname=$(printf '%s\n' "$badging" | sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p" | head -1)
  printf '%s' "$pkg" | grep -Eq '^[a-z][a-z0-9_.]{2,63}$' || { echo "bad package name '$pkg'" >&2; exit 1; }
  apk="$out/$pkg.apk"
  "$apksigner" sign --ks "$CYT_ADDON_KEYSTORE" --ks-type PKCS12 --ks-key-alias "$alias" \
    --ks-pass "$pass" --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
    --out "$apk" "$unsigned"
  signer=$("$apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1 | tr -d '\r')
  [ "$signer" = "$PIN" ] || { echo "$pkg: signer $signer is not the pinned $PIN" >&2; exit 1; }
  sha=$(sha256sum "$apk" | cut -d' ' -f1)
  size=$(wc -c < "$apk" | tr -d ' ')
  entry=$(printf '{"pkg":"%s","versionCode":%s,"versionName":"%s","file":"%s.apk","sha256":"%s","size":%s,"signer_sha256":"%s"}' \
    "$pkg" "$code" "$vname" "$pkg" "$sha" "$size" "$signer")
  entries="${entries:+$entries,}$entry"
  echo "$pkg $vname ($code): $size bytes, sha256 $sha"
done
printf '{"spec":1,"addons":[%s]}\n' "$entries" > "$out/index.json"
cat "$out/index.json"
