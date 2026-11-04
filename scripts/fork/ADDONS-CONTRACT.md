# Fork TV add-ons: the contract between the phone app and the TV add-ons

Clean Your TV RU ships two TV add-ons of its own, built from `tv-addons/` and signed with the fork's key.
They plug into the unchanged upstream launcher (com.gathin.tvhome 3.3, versionCode 12) through its public hooks
(module card, game_day). Nothing in the launcher is modified.

## Signing
- One key for the phone app and the add-ons. Certificate SHA-256 (apksigner "Signer #1 certificate SHA-256 digest"):
  `c02a76c8034b6ecd835dd88161e751fe5689c40b5f42d4715768769580a7051c`
- Add-on Gradle builds sign `release` with env vars when set, else stay unsigned (never the debug key in CI):
  `CYT_ADDON_KEYSTORE` (path to a PKCS12 file), `CYT_ADDON_KEYSTORE_PASSWORD`, `CYT_ADDON_KEY_ALIAS` (default `cleanyourtv`;
  key password = store password).
- Locally: keystore `D:\Claude\cyt-ru-keys\release.p12`, password in `D:\Claude\cyt-ru-keys\release-password.txt`.
  Never print or commit either.

## Common rules for both add-ons
- Standalone Gradle project in `tv-addons/<name>/` modelled on upstream `third_party/cleanyourtv/addons/game-day`
  (Gradle wrapper, AGP 8.5.2, Java 17 source/target, compileSdk/targetSdk 34, minSdk 26, no runtime dependencies,
  JUnit for JVM tests only). Build: `./gradlew --no-daemon assembleRelease testReleaseUnitTest` (JDK 21 runs it fine).
- Output APK: `app/build/outputs/apk/release/app-release.apk` (signed when the env vars are set).
- UI text in Russian in `res/values/strings.xml` (the fork is Russian-first; no English fallback needed).
- Android TV app: `LEANBACK_LAUNCHER` + `DOCK_CARD` intent filters on the main activity, `android.software.leanback`
  and `android.hardware.touchscreen` `required="false"`, a banner (320x180) and an icon of our own art (vector/shape, no logos).
- Module card `res/xml/module_card.xml`: `<card spec="1" label=".." caption=".." accent="#RRGGBB"/>`, ASCII only
  (`A-Z a-z 0-9 space . & + ' -`), label ≤ 24, caption ≤ 32. Literal values.
- D-pad first: everything reachable with arrows/OK/Back, visible focus, 10-foot sizes (text ≥ 20sp), safe 48dp margins.
- No telemetry, no ads, no background services, no boot receivers. Network only while on screen or when the launcher queries.
- Every network call: HTTPS, connect+read timeout 8 s, User-Agent `CleanYourTV-RU/<versionName>`, results cached in
  internal storage so the screen shows the last good data offline (with "Обновлено: <time>").
- Config door for the phone (adb shell only): an exported ContentProvider `<pkg>.config` with
  `android:readPermission` and `android:writePermission` = `android.permission.WRITE_SECURE_SETTINGS` (the adb shell holds
  it; ordinary apps do not). It accepts `content write --uri content://<pkg>.config/config.json` (openFile mode "w"/"wt",
  ≤ 16 KB UTF-8 JSON) and `content read --uri content://<pkg>.config/config.json`. Invalid JSON is rejected and the old
  config kept. After a valid write the app's UI reloads on next resume.

## F1 add-on
- Package / applicationId: `tv.cleanyour.ru.f1`. App label «Формула 1». Card: `label="F1" caption="Calendar and standings" accent="#E10600"`.
- game_day provider (launcher hook, see `third_party/cleanyourtv/docs/modules.md` "Each team's next game on Home"):
  `<meta-data android:name="com.gathin.tvhome.games" android:value="tv.cleanyour.ru.f1.games"/>` on `<application>`,
  exported read-only provider authority `tv.cleanyour.ru.f1.games`, `content://tv.cleanyour.ru.f1.games/turns`.
- Config door authority `tv.cleanyour.ru.f1.config`, JSON (all optional):
  `{"spec":1, "countdown": true, "no_spoilers": false}`; `countdown=false` makes the turns cursor empty.
- The phone app turns the countdown on by writing launcher key `game_day = "tv.cleanyour.ru.f1"` (and removes it to turn off).

## Weather add-on
- Package / applicationId: `tv.cleanyour.ru.weather`. App label «Погода». Card: `label="Weather" caption="Forecast" accent="#4FA3FF"`.
- Fork of upstream `third_party/cleanyourtv/addons/weather` (MIT, by @Sumyabazar1; keep the credit), Open-Meteo, no key.
- Config door authority `tv.cleanyour.ru.weather.config`, JSON written by the phone after the person picks and
  approves the city on the phone (Open-Meteo geocoding, language=ru):
  `{"spec":1, "name":"Москва", "admin1":"Москва", "country":"Россия", "latitude":55.75204, "longitude":37.61781,
    "timezone":"Europe/Moscow"}`
  `name`, `latitude`, `longitude` are required; the rest optional. A city set on the TV itself is replaced by a later
  phone write, and vice versa (last write wins).

## Bundled in the phone app
- The phone APK carries the built, signed add-ons as assets in `android/platform/src/main/assets/fork-addons/`:
  `tv.cleanyour.ru.f1.apk`, `tv.cleanyour.ru.weather.apk` and `index.json`:
  `{"spec":1,"addons":[{"pkg":"tv.cleanyour.ru.f1","versionCode":1,"versionName":"1.0","file":"tv.cleanyour.ru.f1.apk",
    "sha256":"<hex>","size":123,"signer_sha256":"<hex>"}, …]}`
- `scripts/fork/bundle-addons.sh` builds both add-ons, signs them and writes the assets (CI runs it before the app build).
  The assets are generated, not committed (`.gitignore`). Without them the app builds and the add-on widgets show as
  not available in this build.
- The phone trusts an add-on asset only if its sha256 and size match `index.json` and its single signer certificate equals
  the pin above (compiled into the app).
