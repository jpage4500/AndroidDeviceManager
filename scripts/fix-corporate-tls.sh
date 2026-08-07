#!/usr/bin/env bash
# macOS: fixes the jdeploy installer failing behind a TLS-inspecting corporate proxy.
#
# Symptom: "Cannot load app info because the app.xml file could not be found".
# Cause: jdeploy uses a private JRE under ~/.jdeploy whose truststore lacks your
# company's root CA, so its HTTPS calls die with a PKIX error. macOS trusts the root,
# which is why curl works. This imports that root into every jdeploy JRE truststore.
# Safe to re-run.

set -euo pipefail

PROBE_HOST="${1:-www.jdeploy.com}"
ALIAS="corp-proxy-root"
STOREPASS="changeit"

[ "$(uname -s)" = "Darwin" ] || { echo "macOS only -- on Windows/Linux import your corporate root into the jdeploy JRE's cacerts manually."; exit 1; }
command -v openssl >/dev/null || { echo "openssl not found"; exit 1; }

echo "Probing $PROBE_HOST for an intercepting root CA..."
# openssl prints the verify chain (depth=...) on stderr
CHAIN=$(echo | openssl s_client -connect "$PROBE_HOST:443" -servername "$PROBE_HOST" 2>&1 || true)
ROOT_CN=$(printf '%s\n' "$CHAIN" | grep '^depth=' | head -1 | sed -n 's/.*CN *= *//p' || true)

if [ -z "$ROOT_CN" ]; then
  echo "Could not read the certificate chain. Are you online?"
  exit 1
fi
echo "Chain root: $ROOT_CN"

# public roots live in the read-only System Roots keychain; a corporate root has to be added here
PEM=$(mktemp -t corp-root)
trap 'rm -f "$PEM"' EXIT
security find-certificate -a -c "$ROOT_CN" -p /Library/Keychains/System.keychain > "$PEM" 2>/dev/null || true

if ! grep -q "BEGIN CERTIFICATE" "$PEM"; then
  echo "No corporate root found in the System keychain -- traffic to $PROBE_HOST"
  echo "does not appear to be intercepted. Nothing to do."
  exit 0
fi

shopt -s nullglob
STORES=( "$HOME"/.jdeploy/jre-*/*/jre/*/Contents/Home/lib/security/cacerts )
if [ ${#STORES[@]} -eq 0 ]; then
  echo "No jdeploy JRE found under ~/.jdeploy."
  echo "Run the installer once first (it will fail), then re-run this script."
  exit 1
fi

patched=0
for ks in "${STORES[@]}"; do
  # prefer the JRE's own keytool, but only if it actually runs
  kt=""
  for cand in "${ks%/lib/security/cacerts}/bin/keytool" "$(command -v keytool || true)"; do
    if [ -n "$cand" ] && [ -x "$cand" ] && "$cand" -help >/dev/null 2>&1; then kt="$cand"; break; fi
  done
  [ -n "$kt" ] || { echo "no working keytool for $ks -- skipping"; continue; }

  if "$kt" -list -keystore "$ks" -storepass "$STOREPASS" -alias "$ALIAS" >/dev/null 2>&1; then
    echo "already patched: $ks"
    continue
  fi

  [ -f "$ks.orig" ] || cp "$ks" "$ks.orig"
  if "$kt" -importcert -noprompt -trustcacerts -alias "$ALIAS" \
       -keystore "$ks" -storepass "$STOREPASS" -file "$PEM" >/dev/null 2>&1; then
    echo "patched: $ks"
    patched=$((patched + 1))
  else
    echo "FAILED to patch: $ks"
  fi
done

echo
echo "Done. Patched $patched truststore(s); backups saved alongside as cacerts.orig."
echo "Re-run the installer now."
echo
echo "If jdeploy later downloads a newer JRE, it starts untrusted again -- re-run this script."
