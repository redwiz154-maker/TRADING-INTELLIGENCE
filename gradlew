#!/bin/sh
set -e
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
GRADLE_VERSION=8.9
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-$GRADLE_VERSION-bin"
ZIP="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
DIST="$CACHE_DIR/gradle-$GRADLE_VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  echo "Gradle $GRADLE_VERSION not found. Downloading..."
  curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
  rm -rf "$DIST.tmp"
  mkdir -p "$DIST.tmp"
  unzip -q "$ZIP" -d "$DIST.tmp"
  mv "$DIST.tmp/gradle-$GRADLE_VERSION" "$DIST"
  rm -rf "$DIST.tmp" "$ZIP"
fi
exec "$DIST/bin/gradle" "$@"
