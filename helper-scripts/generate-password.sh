#!/usr/bin/env bash
set -euo pipefail

# Generates the JSON password string for the NetSuite OBDC nonce connector.
# Prompts for each credential field, then copies the result to clipboard.

echo "=== NetSuite OBDC Nonce Credential Generator ==="
echo ""

read -rp "Account ID: " account_id
read -rp "Consumer Key: " consumer_key
read -rp "Consumer Secret: " consumer_secret
read -rp "Token ID: " token_id
read -rp "Token Secret: " token_secret

json="{\"accountId\":\"${account_id}\",\"consumerKey\":\"${consumer_key}\",\"consumerSecret\":\"${consumer_secret}\",\"tokenId\":\"${token_id}\",\"tokenSecret\":\"${token_secret}\"}"

echo ""
echo "Generated password string:"
echo "$json"
echo ""

# Cross-platform clipboard copy
copy_to_clipboard() {
    if command -v wl-copy &>/dev/null; then
        echo -n "$1" | wl-copy
    elif command -v pbcopy &>/dev/null; then
        echo -n "$1" | pbcopy
    elif command -v xclip &>/dev/null; then
        echo -n "$1" | xclip -selection clipboard
    elif command -v xsel &>/dev/null; then
        echo -n "$1" | xsel --clipboard --input
    elif command -v clip.exe &>/dev/null; then
        echo -n "$1" | clip.exe
    else
        echo "(No clipboard utility found — copy the string above manually)"
        return 1
    fi
    return 0
}

if copy_to_clipboard "$json"; then
    echo "Copied to clipboard."
fi
