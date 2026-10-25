#!/usr/bin/env bash
set -e

APP_HOME=$( cd -P "$( dirname "$0" )" >/dev/null 2>&1 && pwd )
cd "$APP_HOME"

# Self-healing CI preparation for GitHub Actions & clean runners
if [ -n "$GITHUB_ACTIONS" ] || [ -n "$CI" ]; then
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
  export ANDROID_HOME="$ANDROID_SDK_ROOT"

  # 1. Accept all Android SDK licenses so AGP 9.1.1 auto-installs platforms;android-36.1 & build-tools;36.0.0
  mkdir -p "$ANDROID_SDK_ROOT/licenses" 2>/dev/null || sudo mkdir -p "$ANDROID_SDK_ROOT/licenses" 2>/dev/null || true
  chmod -R 777 "$ANDROID_SDK_ROOT/licenses" 2>/dev/null || sudo chmod -R 777 "$ANDROID_SDK_ROOT" 2>/dev/null || true
  printf "\n8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n84831b9409646a918e30573bab4c9c91346d8abd\n" > "$ANDROID_SDK_ROOT/licenses/android-sdk-license" 2>/dev/null || true
  printf "\n84831b9409646a918e30573bab4c9c91346d8abd\n504667f4c0de7af1a06de9f4b1727b84351f2910\n" > "$ANDROID_SDK_ROOT/licenses/android-sdk-preview-license" 2>/dev/null || true

  # 2. Ensure .env and .env.example exist for Secrets Gradle Plugin
  if [ ! -f ".env.example" ]; then
    echo "GEMINI_API_KEY=PLACEHOLDER" > .env.example
  fi
  if [ ! -f ".env" ]; then
    cp .env.example .env
  fi

  # 3. Restore valid debug.keystore from ASCII debug.keystore.base64
  if [ -f "debug.keystore.base64" ]; then
    rm -f debug.keystore
    tr -d '\r\n ' < debug.keystore.base64 | base64 -d > debug.keystore || rm -f debug.keystore
  fi
  if ! keytool -list -keystore debug.keystore -storepass android >/dev/null 2>&1; then
    rm -f debug.keystore
    keytool -genkeypair -v \
      -keystore debug.keystore \
      -alias androiddebugkey \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000 \
      -storepass android \
      -keypass android \
      -dname "CN=Android Debug,O=Android,C=US"
  fi

  # 4. Remove any legacy binary PNG/WebP/TTF files that may have been corrupted by UTF-8 GitHub export
  rm -rf app/src/main/res/font \
         app/src/main/res/mipmap-mdpi \
         app/src/main/res/mipmap-hdpi \
         app/src/main/res/mipmap-xhdpi \
         app/src/main/res/mipmap-xxhdpi \
         app/src/main/res/mipmap-xxxhdpi
fi

# Locate or bootstrap Gradle 9.3.1 without requiring a binary gradle-wrapper.jar in git
GRADLE_BIN=""
if command -v gradle >/dev/null 2>&1; then
  if gradle --version 2>/dev/null | grep -q "Gradle 9\."; then
    GRADLE_BIN="gradle"
  fi
fi

if [ -z "$GRADLE_BIN" ]; then
  GRADLE_DIST_DIR="/tmp/gradle-9.3.1-bin"
  if [ ! -x "$GRADLE_DIST_DIR/gradle-9.3.1/bin/gradle" ]; then
    echo "Downloading Gradle 9.3.1..."
    mkdir -p "$GRADLE_DIST_DIR"
    curl -sL "https://services.gradle.org/distributions/gradle-9.3.1-bin.zip" -o "$GRADLE_DIST_DIR/gradle.zip"
    unzip -q -o "$GRADLE_DIST_DIR/gradle.zip" -d "$GRADLE_DIST_DIR"
    chmod +x "$GRADLE_DIST_DIR/gradle-9.3.1/bin/gradle"
  fi
  GRADLE_BIN="$GRADLE_DIST_DIR/gradle-9.3.1/bin/gradle"
fi

if [ -n "$GITHUB_ACTIONS" ] || [ -n "$CI" ]; then
  "$GRADLE_BIN" :app:assembleDebug -x lint --no-daemon --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8" --stacktrace
  if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    mkdir -p dist-apk app/build/outputs/apk/release
    cp app/build/outputs/apk/debug/app-debug.apk dist-apk/SHERA-AI.apk
    cp app/build/outputs/apk/debug/app-debug.apk dist-apk/SHERA-AI-debug.apk
    cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release.apk
    cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk
  fi
else
  exec "$GRADLE_BIN" "$@"
fi
