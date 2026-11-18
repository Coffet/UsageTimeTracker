#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>[[:space:]]*$/\1/p' pom.xml | head -n 1)"
if [[ -z "$VERSION" ]]; then
  echo "Could not read the project version from pom.xml." >&2
  exit 1
fi
if [[ -n "${GITHUB_REF_NAME:-}" && "${GITHUB_REF_NAME#v}" != "$VERSION" ]]; then
  echo "Release tag $GITHUB_REF_NAME does not match project version $VERSION." >&2
  exit 1
fi

sh ./mvnw --batch-mode --no-transfer-progress clean package dependency:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory=target/package/lib

mkdir -p target/package target/dist
cp "target/usage-time-tracker-$VERSION.jar" target/package/
JPACKAGE="${JAVA_HOME:?Set JAVA_HOME to JDK 17}/bin/jpackage"
"$JAVA_HOME/bin/java" scripts/TrimSqliteNatives.java target/package/lib
RUNTIME_OPTIONS=(
  --java-options "-XX:-UseBASE64Intrinsics"
  --add-modules "java.desktop,java.sql,java.management,java.naming,jdk.crypto.ec,jdk.jfr,jdk.localedata,jdk.unsupported"
  --jlink-options "--strip-native-commands --strip-debug --no-man-pages --no-header-files --compress=2"
)

if [[ "$(uname -s)" == "Darwin" ]]; then
  mac_app_version="$VERSION"
  if [[ "$VERSION" =~ ^0\. ]]; then
    mac_app_version="1.0.0"
  fi
  "$JPACKAGE" \
    --input target/package \
    --dest target/app-image \
    --name UsageTimeTracker \
    --app-version "$mac_app_version" \
    --vendor "UsageTime Tracker" \
    --main-jar "usage-time-tracker-$VERSION.jar" \
    --main-class app.usagetime.tracker.AppLauncher \
    "${RUNTIME_OPTIONS[@]}" \
    --type app-image
  /usr/libexec/PlistBuddy \
    -c "Set :CFBundleShortVersionString $VERSION" \
    target/app-image/UsageTimeTracker.app/Contents/Info.plist
  case "$(uname -m)" in
    arm64) mac_arch="arm64" ;;
    x86_64) mac_arch="x86_64" ;;
    *) echo "Unsupported macOS architecture: $(uname -m)" >&2; exit 1 ;;
  esac
  hdiutil create \
    -volname "UsageTime Tracker" \
    -srcfolder target/app-image/UsageTimeTracker.app \
    -ov \
    -format UDZO \
    "target/dist/UsageTimeTracker-$VERSION-macos-$mac_arch.dmg"
elif [[ "$(uname -s)" == "Linux" ]]; then
  for package_type in deb rpm; do
    package_deps=()
    if [[ "$package_type" == "deb" ]]; then
      package_deps=(--linux-package-deps "libgtk-3-0, x11-utils, xprintidle")
    fi
    "$JPACKAGE" \
      --input target/package \
      --dest target/dist \
      --name UsageTimeTracker \
      --app-version "$VERSION" \
      --vendor "UsageTime Tracker" \
      --main-jar "usage-time-tracker-$VERSION.jar" \
      --main-class app.usagetime.tracker.AppLauncher \
      "${RUNTIME_OPTIONS[@]}" \
      --linux-app-category Utility \
      --linux-shortcut \
      "${package_deps[@]}" \
      --type "$package_type"
  done

  "$JPACKAGE" \
    --input target/package \
    --dest target/app-image \
    --name UsageTimeTracker \
    --app-version "$VERSION" \
    --vendor "UsageTime Tracker" \
    --main-jar "usage-time-tracker-$VERSION.jar" \
    --main-class app.usagetime.tracker.AppLauncher \
    "${RUNTIME_OPTIONS[@]}" \
    --type app-image

  appdir="$PWD/target/UsageTimeTracker.AppDir"
  mkdir -p "$appdir/usr/lib/usage-time-tracker"
  cp -R target/app-image/UsageTimeTracker/. "$appdir/usr/lib/usage-time-tracker/"
  cat > "$appdir/AppRun" <<'EOF'
#!/usr/bin/env sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/lib/usage-time-tracker/bin/UsageTimeTracker" "$@"
EOF
  chmod +x "$appdir/AppRun"
  cat > "$appdir/UsageTimeTracker.desktop" <<'EOF'
[Desktop Entry]
Name=UsageTime Tracker
Exec=AppRun
Icon=UsageTimeTracker
Type=Application
Categories=Utility;
Terminal=false
EOF
  cat > "$appdir/UsageTimeTracker.svg" <<'EOF'
<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">
  <circle cx="64" cy="64" r="56" fill="#26364a"/>
  <circle cx="64" cy="64" r="40" fill="none" stroke="#f4f2e9" stroke-width="6"/>
  <path d="M64 38v28l20 12" fill="none" stroke="#f4f2e9" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
EOF

  appimagetool="target/appimagetool"
  curl --fail --location --silent --show-error \
    "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage" \
    --output "$appimagetool"
  printf '%s  %s\n' \
    "b90f4a8b18967545fda78a445b27680a1642f1ef9488ced28b65398f2be7add2" \
    "$appimagetool" | sha256sum --check --status
  chmod +x "$appimagetool"
  APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 \
    "$appimagetool" "$appdir" "target/dist/UsageTimeTracker-x86_64.AppImage"
else
  echo "Unsupported packaging host: $(uname -s)" >&2
  exit 1
fi
