#!/usr/bin/env bash
# macOS installer for corporate networks that run TLS inspection (Netskope, Zscaler, ...).
#
# Downloads the latest release, installs it via jdeploy, and teaches the private JRE that
# jdeploy keeps in ~/.jdeploy to trust your company's root CA. Without that trust the
# installer dies with the misleading "Cannot load app info because the app.xml file could
# not be found", and the app's in-app update check fails silently once installed.
#
# Normal installs are unaffected -- use the Releases page. This is only for proxied networks.
#
#   ./install-corporate.sh              download + install + patch
#   ./install-corporate.sh --fix-only   just re-patch (after jdeploy pulls a newer JRE)
#   ./install-corporate.sh --dry-run    do everything except launch the installer

set -euo pipefail

REPO="jpage4500/AndroidDeviceManager"
ALIAS="corp-proxy-root"
STOREPASS="changeit"
CORP_STORE="$HOME/.jdeploy/corp-cacerts"
PROBE_HOSTS=(github.com www.jdeploy.com)
FIX_ONLY=0
DRY_RUN=0
for arg in "$@"; do
  case "$arg" in
    --fix-only) FIX_ONLY=1 ;;
    --dry-run)  DRY_RUN=1 ;;
    *) echo "usage: $0 [--fix-only] [--dry-run]" >&2; exit 2 ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }

[ "$(uname -s)" = "Darwin" ] || die "macOS only. On Windows/Linux, import your corporate root CA into the jdeploy JRE's cacerts by hand."
command -v curl >/dev/null || die "curl not found"
command -v openssl >/dev/null || die "openssl not found"

case "$(uname -m)" in
  arm64)  ARCH=arm64 ;;
  x86_64) ARCH=x64 ;;
  *) die "unsupported architecture: $(uname -m)" ;;
esac

WORK=$(mktemp -d -t adm-install)
trap 'rm -rf "$WORK"' EXIT
PEM="$WORK/corp-roots.pem"
: > "$PEM"

# ---------------------------------------------------------------- corporate root CA

# A public root lives in the read-only System Roots keychain, so a root that also appears
# in /Library/Keychains/System.keychain is one your IT dept installed -- i.e. an interceptor.
echo "Looking for a TLS-inspecting proxy..."
SEEN_CNS=""
for host in "${PROBE_HOSTS[@]}"; do
  chain=$(echo | openssl s_client -connect "$host:443" -servername "$host" 2>&1 || true)
  cn=$(printf '%s\n' "$chain" | grep '^depth=' | head -1 | sed -n 's/.*CN *= *//p' || true)
  [ -n "$cn" ] || continue
  # the same proxy usually fronts every host -- only collect each root once
  case "$SEEN_CNS" in *"[$cn]"*) echo "  $host is intercepted by: $cn"; continue ;; esac
  SEEN_CNS="$SEEN_CNS[$cn]"
  found=$(security find-certificate -a -c "$cn" -p /Library/Keychains/System.keychain 2>/dev/null || true)
  if printf '%s' "$found" | grep -q "BEGIN CERTIFICATE"; then
    echo "  $host is intercepted by: $cn"
    printf '%s\n' "$found" >> "$PEM"
  else
    echo "  $host is not intercepted"
  fi
done

if grep -q "BEGIN CERTIFICATE" "$PEM"; then
  INTERCEPTED=1
  echo "Found $(grep -c 'BEGIN CERTIFICATE' "$PEM") corporate root certificate(s)."
else
  INTERCEPTED=0
  echo "No TLS interception detected -- installing normally."
fi

# ---------------------------------------------------------------- keytool discovery

find_keytool() {
  local c
  for c in "$(/usr/libexec/java_home 2>/dev/null)/bin/keytool" \
           "$(command -v keytool 2>/dev/null || true)" \
           "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" \
           "$HOME"/.jdeploy/jre-*/*/jre/*/Contents/Home/bin/keytool; do
    [ -n "$c" ] && [ -x "$c" ] && "$c" -help >/dev/null 2>&1 && { echo "$c"; return 0; }
  done
  return 1
}

# Adds the corporate roots to a truststore, once. Args: keytool, keystore path.
import_roots() {
  local kt="$1" ks="$2" n=0 cert
  "$kt" -list -keystore "$ks" -storepass "$STOREPASS" -alias "$ALIAS-0" >/dev/null 2>&1 && return 1
  # split the PEM so each cert gets its own alias
  (cd "$WORK" && rm -f xx* && split -p "-----BEGIN CERTIFICATE-----" "$PEM" xx) 2>/dev/null || true
  for cert in "$WORK"/xx*; do
    [ -s "$cert" ] || continue
    grep -q "BEGIN CERTIFICATE" "$cert" || continue
    "$kt" -importcert -noprompt -trustcacerts -alias "$ALIAS-$n" \
      -keystore "$ks" -storepass "$STOREPASS" -file "$cert" >/dev/null 2>&1 || true
    n=$((n + 1))
  done
  return 0
}

# Patches every JRE jdeploy has downloaded, so the installed app's update check works.
patch_jdeploy_jres() {
  local kt="$1" ks patched=0
  shopt -s nullglob
  for ks in "$HOME"/.jdeploy/jre-*/*/jre/*/Contents/Home/lib/security/cacerts; do
    if import_roots "$kt" "$ks"; then
      [ -f "$ks.orig" ] || cp "$ks" "$ks.orig"
      echo "  patched $ks"
      patched=$((patched + 1))
    else
      echo "  already patched $ks"
    fi
  done
  shopt -u nullglob
  [ "$patched" -gt 0 ] && return 0 || return 1
}

KEYTOOL=""
if [ "$INTERCEPTED" = 1 ]; then
  KEYTOOL=$(find_keytool || true)
fi

# ---------------------------------------------------------------- --fix-only

if [ "$FIX_ONLY" = 1 ]; then
  [ "$INTERCEPTED" = 1 ] || { echo "Nothing to do."; exit 0; }
  [ -n "$KEYTOOL" ] || die "no Java found -- run without --fix-only to install first"
  echo "Patching jdeploy JREs..."
  patch_jdeploy_jres "$KEYTOOL" || true
  echo "Done."
  exit 0
fi

# ---------------------------------------------------------------- build a trusted store

# The installer runs on a JRE jdeploy downloads on the fly, which may not exist yet. Point
# that JVM at our own truststore via JAVA_TOOL_OPTIONS so the very first run already works.
USE_STORE=0
if [ "$INTERCEPTED" = 1 ] && [ -n "$KEYTOOL" ]; then
  BASE=$(dirname "$(dirname "$KEYTOOL")")/lib/security/cacerts
  if [ -f "$BASE" ]; then
    mkdir -p "$(dirname "$CORP_STORE")"
    cp "$BASE" "$CORP_STORE"
    chmod u+w "$CORP_STORE"
    import_roots "$KEYTOOL" "$CORP_STORE" || true
    USE_STORE=1
    echo "Built a trusted store at $CORP_STORE"
  fi
elif [ "$INTERCEPTED" = 1 ]; then
  echo
  echo "No Java installation found, so the truststore can't be prepared in advance."
  echo "The installer will show one error; this script then fixes it and reopens the"
  echo "installer automatically. Close the error dialog when it appears."
  echo
fi

# ---------------------------------------------------------------- download

echo "Finding the latest release..."
JSON=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest") \
  || die "could not reach api.github.com (rate limited? offline?)"
TAG=$(printf '%s' "$JSON" | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' | head -1)
DL=$(printf '%s' "$JSON" | grep -o "https://[^\"]*Installer-mac-$ARCH-[^\"]*\.tgz" | head -1)
[ -n "$DL" ] || die "no mac-$ARCH installer in the latest release"
echo "  version $TAG"

echo "Downloading..."
curl -fsSL -o "$WORK/installer.tgz" "$DL" || die "download failed"
tar xzf "$WORK/installer.tgz" -C "$WORK"

APP=$(find "$WORK" -maxdepth 2 -name "*.app" -type d | head -1)
[ -n "$APP" ] || die "no .app found in the downloaded archive"

# Without this macOS runs the installer from a read-only translocated path
xattr -dr com.apple.quarantine "$APP" 2>/dev/null || true

LAUNCHER=$(find "$APP/Contents/MacOS" -type f -perm -u+x | head -1)
[ -n "$LAUNCHER" ] || die "no launcher binary inside $APP"

# ---------------------------------------------------------------- install

run_installer() {
  if [ "$DRY_RUN" = 1 ]; then
    echo "  [dry-run] would launch: $LAUNCHER"
    [ "$USE_STORE" = 1 ] && echo "  [dry-run] with trustStore=$CORP_STORE"
    return 0
  fi
  if [ "$USE_STORE" = 1 ]; then
    JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$CORP_STORE -Djavax.net.ssl.trustStorePassword=$STOREPASS" \
      "$LAUNCHER" || true
  else
    "$LAUNCHER" || true
  fi
}

is_installed() {
  [ -d "$HOME/Applications/Android Device Manager.app" ] || [ -d "/Applications/Android Device Manager.app" ]
}

echo "Starting the installer..."
run_installer

# jdeploy has now downloaded its JRE even if the install itself failed, so it can be patched.
if [ "$INTERCEPTED" = 1 ]; then
  [ -n "$KEYTOOL" ] || KEYTOOL=$(find_keytool || true)
  if [ -n "$KEYTOOL" ]; then
    echo "Patching jdeploy JREs so in-app update checks work..."
    patch_jdeploy_jres "$KEYTOOL" || true
    if ! is_installed; then
      echo "Reopening the installer now that certificates are trusted..."
      USE_STORE=0
      run_installer
    fi
  fi
fi

echo
if is_installed; then
  echo "Installed. Launch \"Android Device Manager\" from Applications."
  echo "jdeploy will auto-update it on launch from now on."
  echo
  echo "If a future update pulls a newer JRE, in-app update checks may warn again -- rerun:"
  echo "  $0 --fix-only"
else
  echo "The installer did not finish. Rerun this script; the certificate fix is already in place."
fi
