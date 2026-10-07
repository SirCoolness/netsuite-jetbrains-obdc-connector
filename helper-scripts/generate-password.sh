#!/usr/bin/env bash
set -euo pipefail

# Builds the JSON password for the NetSuite JetBrains driver (GenerateNonce=true) and copies
# it to the clipboard, without printing it. Paste it into the data source's Password field.
#
# Usage:
#   helper-scripts/generate-password.sh [--env-file FILE]
#
# Values come from NETSUITE_ACCOUNT_ID, NETSUITE_CONSUMER_KEY, NETSUITE_CONSUMER_SECRET,
# NETSUITE_TOKEN_ID and NETSUITE_TOKEN_SECRET (environment or --env-file); any that are
# unset are prompted for, secrets without echo.

if [[ "${1:-}" == "--env-file" ]]; then
    set -a; source "$2"; set +a
fi

echo "=== NetSuite JetBrains driver password generator ==="

ask() { # ask VAR "Prompt" [secret]
    if [[ -z "${!1:-}" ]]; then
        if [[ -n "${3:-}" ]]; then
            read -rsp "$2: " "$1"; echo
        else
            read -rp "$2: " "$1"
        fi
    fi
}
ask NETSUITE_ACCOUNT_ID "Account ID"
ask NETSUITE_CONSUMER_KEY "Consumer Key" secret
ask NETSUITE_CONSUMER_SECRET "Consumer Secret" secret
ask NETSUITE_TOKEN_ID "Token ID" secret
ask NETSUITE_TOKEN_SECRET "Token Secret" secret

json="{\"accountId\":\"${NETSUITE_ACCOUNT_ID}\",\"consumerKey\":\"${NETSUITE_CONSUMER_KEY}\",\"consumerSecret\":\"${NETSUITE_CONSUMER_SECRET}\",\"tokenId\":\"${NETSUITE_TOKEN_ID}\",\"tokenSecret\":\"${NETSUITE_TOKEN_SECRET}\"}"

copy_to_clipboard() {
    if command -v wl-copy &>/dev/null; then
        printf '%s' "$1" | wl-copy
    elif command -v pbcopy &>/dev/null; then
        printf '%s' "$1" | pbcopy
    elif command -v xclip &>/dev/null; then
        printf '%s' "$1" | xclip -selection clipboard
    elif command -v xsel &>/dev/null; then
        printf '%s' "$1" | xsel --clipboard --input
    elif command -v clip.exe &>/dev/null; then
        printf '%s' "$1" | clip.exe
    else
        return 1
    fi
}

if copy_to_clipboard "$json"; then
    echo "Password JSON copied to the clipboard. Paste it into the data source Password field (Save: Forever)."
else
    echo "No clipboard utility found (wl-copy, pbcopy, xclip, xsel, clip.exe)." >&2
    read -rp "Print the password JSON to this terminal instead? [y/N] " answer
    if [[ "$answer" == [yY]* ]]; then
        printf '%s\n' "$json"
    fi
fi
