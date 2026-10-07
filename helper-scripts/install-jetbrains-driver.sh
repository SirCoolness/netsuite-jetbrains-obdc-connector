#!/usr/bin/env bash
set -euo pipefail

# Installs the built driver at a stable path and renders the JetBrains config templates
# (config-templates/) with your values. It never touches your IDE config or project: it
# only writes under the install directory and prints what to do next.
#
# Usage:
#   helper-scripts/install-jetbrains-driver.sh [--env-file FILE] [--build]
#
# Environment (or set them in the --env-file, a KEY=value file):
#   NETSUITE_ACCOUNT_ID   account id, e.g. 1234567 or 1234567_SB1 (required for the data source files)
#   NETSUITE_ROLE_ID      role id used for SuiteAnalytics Connect (required for the data source files)
#   NETSUITE_JDBC_HOME    install directory (default: ~/.local/share/netsuite-jdbc)
#
# Secret values are never printed. The rendered files contain the account id and are
# created readable by you only.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
BUILT_JAR="$REPO_DIR/out/netsuite-jetbrains-driver.jar"
TEMPLATES="$REPO_DIR/config-templates"

build=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file) set -a; source "$2"; set +a; shift 2 ;;
        --build) build=true; shift ;;
        -h|--help) sed -n '4,18p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

if $build || [[ ! -f "$BUILT_JAR" ]]; then
    "$REPO_DIR/build.sh"
fi

home="${NETSUITE_JDBC_HOME:-$HOME/.local/share/netsuite-jdbc}"
out="$home/jetbrains"
umask 077
mkdir -p "$out"

jar="$home/netsuite-jetbrains-driver.jar"
cp "$BUILT_JAR" "$jar.tmp" && mv "$jar.tmp" "$jar"
chmod 644 "$jar"

render() {
    sed -e "s|__DRIVER_JAR__|$jar|g" \
        -e "s|__ACCOUNT_ID__|${NETSUITE_ACCOUNT_ID:-__ACCOUNT_ID__}|g" \
        -e "s|__ACCOUNT_HOST__|${account_host:-__ACCOUNT_HOST__}|g" \
        -e "s|__ROLE_ID__|${NETSUITE_ROLE_ID:-__ROLE_ID__}|g" \
        -e "s|__DATA_SOURCE_UUID__|$uuid|g" \
        "$TEMPLATES/$1" > "$out/$1"
}

# <account>.connect.api.netsuite.com uses the account id in lower case with "_" as "-"
account_host=""
if [[ -n "${NETSUITE_ACCOUNT_ID:-}" ]]; then
    account_host="$(printf '%s' "$NETSUITE_ACCOUNT_ID" | tr '[:upper:]_' '[:lower:]-')"
fi
uuid="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen 2>/dev/null || echo 00000000-0000-4000-8000-000000000000)"

render databaseDrivers.xml
missing=()
[[ -z "${NETSUITE_ACCOUNT_ID:-}" ]] && missing+=(NETSUITE_ACCOUNT_ID)
[[ -z "${NETSUITE_ROLE_ID:-}" ]] && missing+=(NETSUITE_ROLE_ID)
if [[ ${#missing[@]} -eq 0 ]]; then
    for f in dataSources.xml dataSources.local.xml jdbc-url.txt; do render "$f"; done
fi

echo "Driver jar:  $jar"
echo "Driver class: com.netsuite.jetbrains.NetsuiteJetbrainsDriver"
echo "Rendered:    $out/databaseDrivers.xml"
if [[ ${#missing[@]} -eq 0 ]]; then
    echo "             $out/dataSources.xml"
    echo "             $out/dataSources.local.xml"
    echo "             $out/jdbc-url.txt  (the data source URL; contains your account id)"
else
    echo "Skipped the data source files: set ${missing[*]} to render them."
fi
echo ""
echo "Next (see README.md, \"JetBrains setup\"):"
echo "  1. Database > + > Driver: add the jar above, class com.netsuite.jetbrains.NetsuiteJetbrainsDriver,"
echo "     dialect left on Generic SQL. If the driver already exists, point it at the jar above instead."
echo "  2. Data source: URL from jdbc-url.txt, user TBA, password = credential JSON"
echo "     (helper-scripts/generate-password.sh copies it to the clipboard), Save: Forever."
