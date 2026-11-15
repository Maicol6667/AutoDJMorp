#!/usr/bin/env bash
set -euo pipefail

# Build the complete YouTube Music + crossfade client from a pinned upstream
# revision, while retaining AutoDJMorp's Android package identity and signing key.
UPSTREAM_REPO="https://github.com/Maloy-Android/Muzza.git"
UPSTREAM_COMMIT="9e6cdc6be158e9699cf689970f30f279dddc2895"
BUILD_DIR="build-source"

rm -rf "$BUILD_DIR"
git clone --filter=blob:none "$UPSTREAM_REPO" "$BUILD_DIR"
git -C "$BUILD_DIR" checkout --detach "$UPSTREAM_COMMIT"

python3 - "$BUILD_DIR" "${GITHUB_RUN_NUMBER:-1}" <<'PY'
import pathlib, re, sys
root = pathlib.Path(sys.argv[1])
build_number = int(sys.argv[2])
gradle = root / "app/build.gradle.kts"
s = gradle.read_text(encoding="utf-8")

replacements = [
    ('applicationId = "com.maloy.muzza"', 'applicationId = "com.autodjmorp"'),
    ('versionCode = 49', f'versionCode = {build_number}'),
    ('versionName = "0.7.7"', f'versionName = "1.0.{build_number}"'),
]
for old, new in replacements:
    if old not in s:
        raise SystemExit(f"Expected upstream Gradle setting not found: {old}")
    s = s.replace(old, new, 1)

signing_anchor = '    buildTypes {'
signing_insert = '''signingConfigs {
        create("autodjRelease") {
            storeFile = file("release-key.jks")
            storePassword = System.getenv("AUTODJ_STORE_PASSWORD")
            keyAlias = System.getenv("AUTODJ_KEY_ALIAS")
            keyPassword = System.getenv("AUTODJ_KEY_PASSWORD")
        }
    }
    buildTypes {'''
if signing_anchor not in s:
    raise SystemExit("Could not locate buildTypes block")
s = s.replace(signing_anchor, signing_insert, 1)

release_anchor = '        release {\n            isMinifyEnabled = true'
release_insert = '        release {\n            signingConfig = signingConfigs.getByName("autodjRelease")\n            isMinifyEnabled = true'
if release_anchor not in s:
    raise SystemExit("Could not locate upstream release build type")
s = s.replace(release_anchor, release_insert, 1)
gradle.write_text(s, encoding="utf-8")

app_name = root / "app/src/main/res/values/app_name.xml"
if app_name.exists():
    name = app_name.read_text(encoding="utf-8")
    if '<string name="app_name">Muzza</string>' not in name:
        raise SystemExit("Could not locate upstream app label")
    app_name.write_text(name.replace(
        '<string name="app_name">Muzza</string>',
        '<string name="app_name">AutoDJMorp</string>', 1
    ), encoding="utf-8")
else:
    raise SystemExit("Upstream app label resource missing")
PY

cp app/release-key.jks "$BUILD_DIR/app/release-key.jks"
cd "$BUILD_DIR"
./gradlew --no-daemon :app:assembleFossRelease
