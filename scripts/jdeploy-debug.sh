#!/bin/bash
# inspect + launch the AndroidDeviceManager versions jdeploy installed under ~/.jdeploy

set -o pipefail

SELF="$(basename "${BASH_SOURCE[0]:-$0}")"
JDEPLOY_DIR="${JDEPLOY_HOME:-$HOME/.jdeploy}"
APP_MATCH='android[-_ ]*device[-_ ]*manager'
MAIN_CLASS_PATH='com/jpage4500/devicemanager/MainApplication.class'
RUN_LOG="${TMPDIR:-/tmp}/adm-jdeploy-run.log"

JARS=()
JVMS=()

usage() {
    cat <<EOF
usage: ./$SELF [command]        # run it, do not source it - error paths call exit

  list           show installed versions, bundled JREs and launch diagnostics (default)
  run [N|vers]   run version N, a version string, or a path to any .jar; prompts if omitted
  launcher       run the installed .app's native launcher in the foreground (shows update errors)
  bundle         dump the .app's config - which package/source/version it fetches
  tls            show the certificate each github host really serves (proxy interception check)
  cert [host]    dump a host's full chain and ask macOS (SecTrust) to verify it
  trust-root     save a host's root and print the command to trust it (CT workaround)
  logs           tail app / jdeploy / macOS crash logs
  help           this message

env:
  JAVA_BIN       java binary to run with (default: best bundled JRE, else system java)
  JAVA_OPTS      extra JVM args, e.g. JAVA_OPTS="-Dsun.java2d.opengl=false"
  JDEPLOY_HOME   override ~/.jdeploy
EOF
}

# --- discovery ---------------------------------------------------------------

find_jars() {
    JARS=()
    [ -d "$JDEPLOY_DIR" ] || return
    local list
    list=$(find "$JDEPLOY_DIR" -type f -name '*.jar' 2>/dev/null)
    [ -z "$list" ] && return

    local matched
    matched=$(echo "$list" | grep -Ei "$APP_MATCH")
    [ -z "$matched" ] && matched="$list"
    # keep only real app jars - drops jdeploy's jar-runner.jar stub
    matched=$(echo "$matched" | while IFS= read -r j; do
        unzip -l "$j" "$MAIN_CLASS_PATH" >/dev/null 2>&1 && echo "$j"
    done)
    [ -z "$matched" ] && return

    # newest first
    while IFS= read -r j; do
        [ -n "$j" ] && JARS+=("$j")
    done < <(echo "$matched" | while IFS= read -r j; do
        printf '%s\t%s\n' "$(stat -f '%m' "$j" 2>/dev/null || echo 0)" "$j"
    done | sort -rn | cut -f2-)
}

find_jvms() {
    JVMS=()
    [ -d "$JDEPLOY_DIR" ] || return
    while IFS= read -r j; do
        [ -n "$j" ] && JVMS+=("$j")
    done < <(find "$JDEPLOY_DIR" -type f -name java -perm -u+x 2>/dev/null | sort)
}

jar_version() {
    local jar="$1" v
    v=$(unzip -p "$jar" app.properties 2>/dev/null | sed -n 's/^version=//p' | tr -d '\r')
    [ -n "$v" ] && { echo "$v"; return; }
    # jdeploy keeps a package.json beside the payload
    local d="$(dirname "$jar")"
    local i=0
    while [ $i -lt 4 ] && [ "$d" != "/" ]; do
        if [ -f "$d/package.json" ]; then
            v=$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$d/package.json" | head -1)
            [ -n "$v" ] && { echo "$v"; return; }
        fi
        d="$(dirname "$d")"
        i=$((i + 1))
    done
    v=$(echo "$jar" | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+' | tail -1)
    [ -n "$v" ] && echo "$v" || echo "?"
}

# major class-file version of the main class -> minimum java release
jar_java_release() {
    local major
    major=$(unzip -p "$1" "$MAIN_CLASS_PATH" 2>/dev/null | od -An -t u1 -N 8 | awk 'NR==1{print $7*256+$8}')
    if [ -n "$major" ] && [ "$major" -gt 44 ] 2>/dev/null; then
        echo $((major - 44))
    else
        echo "?"
    fi
}

arch_of() {
    local out
    out=$(lipo -archs "$1" 2>/dev/null)
    [ -n "$out" ] && { echo "$out"; return; }
    file -b "$1" 2>/dev/null | head -1
}

java_release_of() {
    "$1" -version 2>&1 | head -1 | sed -n 's/.*version "\([^"]*\)".*/\1/p'
}

# --- output ------------------------------------------------------------------

hdr() { printf '\n\033[1m== %s\033[0m\n' "$1"; }

list_versions() {
    hdr "installed versions ($JDEPLOY_DIR)"
    if [ ${#JARS[@]} -eq 0 ]; then
        echo "  none found - no AndroidDeviceManager jar under $JDEPLOY_DIR"
        return
    fi
    printf '  %-3s %-14s %-10s %-6s %-17s %s\n' "#" "VERSION" "NEEDS JAVA" "SIZE" "MODIFIED" "JAR"
    local i=1 jar
    for jar in "${JARS[@]}"; do
        printf '  %-3s %-14s %-10s %-6s %-17s %s\n' \
            "$i" \
            "$(jar_version "$jar")" \
            "$(jar_java_release "$jar")" \
            "$(du -h "$jar" 2>/dev/null | cut -f1)" \
            "$(date -r "$(stat -f '%m' "$jar" 2>/dev/null || echo 0)" '+%Y-%m-%d %H:%M' 2>/dev/null)" \
            "$jar"
        i=$((i + 1))
    done
}

list_jvms() {
    local host_arch="$(uname -m)"
    hdr "bundled JREs (host arch: $host_arch)"
    if [ ${#JVMS[@]} -eq 0 ]; then
        echo "  none found - jdeploy never downloaded a JRE (this alone will stop the app launching)"
        return
    fi
    local jvm arch note
    for jvm in "${JVMS[@]}"; do
        arch="$(arch_of "$jvm")"
        note=""
        case "$arch" in
            *"$host_arch"*) note="ok" ;;
            *) note=">>> WRONG ARCH for this mac" ;;
        esac
        printf '  %-10s %-12s %-28s %s\n' "$arch" "$(java_release_of "$jvm")" "$note" "$jvm"
    done
}

app_bundles() {
    find /Applications "$HOME/Applications" -maxdepth 2 -iname '*device*manager*.app' 2>/dev/null
}

list_app_bundles() {
    hdr "installed .app bundles"
    local found=0 app bin
    while IFS= read -r app; do
        [ -z "$app" ] && continue
        found=1
        echo "  $app"
        bin=$(find "$app/Contents/MacOS" -type f -perm -u+x 2>/dev/null | head -1)
        if [ -n "$bin" ]; then
            echo "      launcher : $(basename "$bin")  [$(arch_of "$bin")]"
        else
            echo "      launcher : >>> MISSING or not executable"
        fi
        echo "      version  : $(defaults read "$app/Contents/Info" CFBundleVersion 2>/dev/null || echo '?')"
        local q
        q=$(xattr -p com.apple.quarantine "$app" 2>/dev/null)
        [ -n "$q" ] && echo "      >>> quarantined: $q  (fix: xattr -dr com.apple.quarantine \"$app\")"
    done < <(app_bundles)
    [ $found -eq 0 ] && echo "  none found in /Applications or ~/Applications"
}

# what the native launcher was built to fetch: package name, source (npm vs github), version
show_bundle_config() {
    local app f
    while IFS= read -r app; do
        [ -z "$app" ] && continue
        hdr "bundle config: $app"
        find "$app/Contents" -maxdepth 2 \( -name '*.json' -o -name '*.xml' -o -name '*.plist' \) 2>/dev/null |
            while IFS= read -r f; do
                echo "--- $f"
                case "$f" in
                    *.plist) plutil -p "$f" 2>/dev/null | grep -viE 'icon|documenttype|urltype|CFBundleTypeExtensions' | head -40 ;;
                    *) head -60 "$f" ;;
                esac
            done
    done < <(app_bundles)
}

# run the native launcher in the foreground so its update/download errors are visible
run_launcher() {
    local app bin
    app=$(app_bundles | head -1)
    [ -z "$app" ] && { echo "no .app bundle found" >&2; exit 1; }
    bin=$(find "$app/Contents/MacOS" -type f -perm -u+x 2>/dev/null | head -1)
    [ -z "$bin" ] && { echo "no launcher binary in $app/Contents/MacOS" >&2; exit 1; }

    hdr "running native launcher"
    echo "  $bin"
    echo "  log: $RUN_LOG"
    echo
    "$bin" "$@" 2>&1 | tee "$RUN_LOG"
    local rc=${PIPESTATUS[0]}
    echo
    echo "exit code: $rc  (output saved to $RUN_LOG)"
    return $rc
}

list_host() {
    hdr "host"
    echo "  macOS   : $(sw_vers -productVersion 2>/dev/null) ($(sw_vers -buildVersion 2>/dev/null))"
    echo "  arch    : $(uname -m)"
    echo "  cpu     : $(sysctl -n machdep.cpu.brand_string 2>/dev/null)"
    echo "  date    : $(date)"
}

list_system_java() {
    hdr "system java"
    local jh
    jh=$(/usr/libexec/java_home 2>/dev/null)
    if [ -n "$jh" ]; then
        echo "  java_home : $jh"
        echo "  version   : $("$jh/bin/java" -version 2>&1 | head -1)"
        echo "  arch      : $(arch_of "$jh/bin/java")"
    else
        echo "  no JDK registered with /usr/libexec/java_home"
    fi
    local w
    w=$(command -v java)
    [ -n "$w" ] && echo "  on PATH   : $w"
}

show_logs() {
    hdr "jdeploy logs"
    local f found=0
    while IFS= read -r f; do
        [ -z "$f" ] && continue
        found=1
        echo "--- $f"
        tail -30 "$f"
    done < <(find "$JDEPLOY_DIR" -maxdepth 3 -type f \( -name '*.log' -o -name '*log*.txt' \) 2>/dev/null)
    [ $found -eq 0 ] && echo "  none under $JDEPLOY_DIR"

    hdr "launcher logs (~/Library/Logs)"
    found=0
    while IFS= read -r f; do
        [ -z "$f" ] && continue
        found=1
        echo "--- $f"
        tail -30 "$f"
    done < <(find "$HOME/Library/Logs" -maxdepth 2 -type f \( -iname '*device*manager*' -o -iname '*jdeploy*' -o -iname '*client4j*' \) 2>/dev/null)
    [ $found -eq 0 ] && echo "  none"

    hdr "app log (~/.device_manager)"
    if [ -f "$HOME/.device_manager/device_manager_log.txt" ]; then
        tail -40 "$HOME/.device_manager/device_manager_log.txt"
    else
        echo "  no log written yet - the app never got far enough to log"
    fi

    hdr "recent macOS crash reports"
    local dr="$HOME/Library/Logs/DiagnosticReports"
    local hits
    hits=$(ls -t "$dr" 2>/dev/null | grep -iE 'device.?manager|java|adm|jdeploy' | head -5)
    if [ -n "$hits" ]; then
        echo "$hits" | while IFS= read -r c; do
            echo "--- $dr/$c"
            grep -iE 'Termination|Exception Type|Crashed Thread|Reason' "$dr/$c" 2>/dev/null | head -8
        done
    else
        echo "  none"
    fi

    hdr "last run log"
    if [ -f "$RUN_LOG" ]; then
        echo "  $RUN_LOG"
        tail -40 "$RUN_LOG"
    else
        echo "  no run yet ($RUN_LOG)"
    fi
}

# --- run ---------------------------------------------------------------------

pick_java() {
    if [ -n "$JAVA_BIN" ]; then
        echo "$JAVA_BIN"
        return
    fi
    local host_arch="$(uname -m)" jvm
    # bundled JRE matching this mac's arch
    for jvm in "${JVMS[@]}"; do
        case "$(arch_of "$jvm")" in
            *"$host_arch"*) echo "$jvm"; return ;;
        esac
    done
    local jh
    jh=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home 2>/dev/null)
    [ -n "$jh" ] && { echo "$jh/bin/java"; return; }
    command -v java
}

run_version() {
    local sel="$1" jar="" i=1 j

    # an explicit path wins - lets you run a jar downloaded straight from GitHub Releases
    if [ -n "$sel" ] && [ -f "$sel" ]; then
        run_jar "$sel"
        return $?
    fi

    if [ ${#JARS[@]} -eq 0 ]; then
        echo "no versions found under $JDEPLOY_DIR" >&2
        exit 1
    fi

    if [ -z "$sel" ]; then
        list_versions
        echo
        printf 'run which # [1]: '
        read -r sel </dev/tty
        [ -z "$sel" ] && sel=1
    fi

    if echo "$sel" | grep -qE '^[0-9]+$' && [ "$sel" -le ${#JARS[@]} ] 2>/dev/null; then
        jar="${JARS[$((sel - 1))]}"
    else
        for j in "${JARS[@]}"; do
            if [ "$(jar_version "$j")" = "$sel" ]; then jar="$j"; break; fi
        done
    fi
    [ -z "$jar" ] && { echo "no version matching '$sel'" >&2; exit 1; }
    run_jar "$jar"
}

run_jar() {
    local jar="$1"
    local java_bin
    java_bin="$(pick_java)"
    [ -z "$java_bin" ] && { echo "no java found - install a JDK 17+ or set JAVA_BIN" >&2; exit 1; }

    local needs="$(jar_java_release "$jar")"
    local has="$(java_release_of "$java_bin")"
    hdr "launching"
    echo "  version : $(jar_version "$jar")"
    echo "  jar     : $jar"
    echo "  java    : $java_bin"
    echo "  java ver: $has  (jar needs $needs+)"
    echo "  arch    : $(arch_of "$java_bin")  (host $(uname -m))"
    echo "  log     : $RUN_LOG"
    echo
    echo "  \$ $java_bin $JAVA_OPTS -jar $jar"
    echo

    "$java_bin" $JAVA_OPTS -jar "$jar" 2>&1 | tee "$RUN_LOG"
    local rc=${PIPESTATUS[0]}
    echo
    echo "exit code: $rc  (output saved to $RUN_LOG)"
    return $rc
}

# --- tls ---------------------------------------------------------------------

# the launcher fetches package-info.json over https - show what cert each host really serves
probe_tls() {
    local hosts="api.github.com github.com objects.githubusercontent.com release-assets.githubusercontent.com"
    local host cert
    hdr "TLS probe (is this network intercepting github hosts?)"
    for host in $hosts; do
        echo "--- $host"
        cert=$(echo | openssl s_client -connect "$host:443" -servername "$host" 2>/dev/null |
            openssl x509 -noout -subject -issuer -dates -ext subjectAltName 2>/dev/null)
        if [ -z "$cert" ]; then
            echo "      >>> no certificate returned (connection blocked?)"
        else
            echo "$cert" | sed 's/^/      /'
            echo "$cert" | grep -qiE 'github|githubusercontent' ||
                echo "      >>> cert does not mention github - TLS is being intercepted"
        fi
        printf '      curl: '
        curl -sS -o /dev/null -w 'http %{http_code}\n' --max-time 15 "https://$host/" 2>&1 | head -1
    done
    echo
    echo "  a cert whose issuer is not a public CA (e.g. Netskope/Zscaler) means inspection is on."
    echo "  the Go launcher does not use the java truststore, so it fails independently of java."
}

# openssl/curl accept a chain that macOS may still reject - ask macOS itself
probe_cert() {
    local host="${1:-release-assets.githubusercontent.com}"
    local dir="${TMPDIR:-/tmp}/adm-tls"
    rm -rf "$dir"; mkdir -p "$dir" || return

    hdr "certificate chain: $host"
    openssl s_client -connect "$host:443" -servername "$host" -showcerts </dev/null 2>/dev/null >"$dir/chain.txt"
    awk -v d="$dir" '/-----BEGIN CERTIFICATE-----/{n++} n{print > (d "/cert" n ".pem")}' "$dir/chain.txt"

    local f n
    for f in "$dir"/cert*.pem; do
        [ -f "$f" ] || continue
        echo "--- $(basename "$f")"
        openssl x509 -in "$f" -noout -subject -issuer -dates -serial 2>/dev/null | sed 's/^/      /'
        n=$(openssl x509 -in "$f" -noout -text 2>/dev/null | grep -c 'Log ID')
        if [ "$n" -gt 0 ]; then
            echo "      SCTs     : $n embedded (apple wants >=2 from known logs)"
        else
            echo "      SCTs     : >>> none embedded"
        fi
        # apple rejects certs issued after 2020-09-01 that are valid longer than 398 days
        local nb na days
        nb=$(openssl x509 -in "$f" -noout -startdate 2>/dev/null | cut -d= -f2)
        na=$(openssl x509 -in "$f" -noout -enddate 2>/dev/null | cut -d= -f2)
        nb=$(date -j -f '%b %e %T %Y %Z' "$nb" '+%s' 2>/dev/null)
        na=$(date -j -f '%b %e %T %Y %Z' "$na" '+%s' 2>/dev/null)
        if [ -n "$nb" ] && [ -n "$na" ]; then
            days=$(((na - nb) / 86400))
            printf '      validity : %s days' "$days"
            [ "$days" -gt 398 ] && printf '  >>> over apple 398-day limit'
            echo
        fi
    done

    hdr "macOS verdict (SecTrust - the same verifier the Go launcher uses)"
    if [ -f "$dir/cert1.pem" ]; then
        local args=""
        for f in "$dir"/cert*.pem; do
            [ -f "$f" ] && args="$args -c $f"
        done
        security verify-cert $args -p ssl -s "$host" 2>&1 | sed 's/^/      /'
        echo
        echo "  if this fails while curl succeeds, macOS policy (CT / 398-day / stale trust store)"
        echo "  is the blocker - not the network and not the certificate's hostnames."
    else
        echo "  could not retrieve a chain"
    fi
}

# apple skips CT for chains ending at an admin-installed anchor - offer the root as one
trust_root() {
    local host="${1:-release-assets.githubusercontent.com}"
    local dir="${TMPDIR:-/tmp}/adm-tls"
    [ -f "$dir/cert1.pem" ] || probe_cert "$host" >/dev/null 2>&1

    local f last=""
    for f in "$dir"/cert*.pem; do [ -f "$f" ] && last="$f"; done
    [ -z "$last" ] && { echo "no chain retrieved - try: ./$SELF cert $host" >&2; return 1; }

    local name out
    name=$(openssl x509 -in "$last" -noout -subject 2>/dev/null | sed 's/.*CN *= *//; s/[^A-Za-z0-9]/-/g')
    out="$HOME/Downloads/${name:-anchor}.pem"
    cp "$last" "$out" || return 1

    hdr "candidate trust anchor (top of $host's chain)"
    openssl x509 -in "$out" -noout -subject -issuer -dates 2>/dev/null | sed 's/^/      /'
    echo "      file    : $out"
    cat <<EOF

  Apple does not enforce Certificate Transparency on chains that terminate at a
  user- or admin-installed anchor - only on ones ending at a built-in system root.
  Trusting this root directly should therefore let SecTrust, and the launcher, accept
  the cert without needing a CT log list this OS will never receive.

  install (prompts for your admin password):
      sudo security add-trusted-cert -d -r trustRoot \\
           -k /Library/Keychains/System.keychain "$out"

  verify it took, then launch:
      ./$SELF cert $host        # expect the CT complaint to be gone
      ./$SELF launcher

  undo at any time:
      sudo security remove-trusted-cert -d "$out"

  This is a genuine Let's Encrypt root that current macOS versions already ship - you are
  pre-trusting what this EOL system would have received in a trust store update.
EOF
}

# --- main --------------------------------------------------------------------

: "${JAVA_BIN:=}"
: "${JAVA_OPTS:=}"

cmd="${1:-list}"
case "$cmd" in
    help | -h | --help) usage ;;
    logs)
        show_logs
        ;;
    bundle)
        show_bundle_config
        ;;
    tls)
        probe_tls
        probe_cert "release-assets.githubusercontent.com"
        ;;
    cert)
        probe_cert "${2:-release-assets.githubusercontent.com}"
        ;;
    trust-root)
        trust_root "${2:-release-assets.githubusercontent.com}"
        ;;
    launcher)
        shift
        run_launcher "$@"
        ;;
    run)
        find_jars
        find_jvms
        run_version "${2:-}"
        ;;
    list | "")
        find_jars
        find_jvms
        list_host
        list_versions
        list_jvms
        list_app_bundles
        list_system_java
        echo
        echo "run one with: ./$SELF run [#]"
        ;;
    *)
        usage
        exit 1
        ;;
esac
