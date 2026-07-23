#!/bin/bash
set -euo pipefail

# Build script for the NetSuite TBA Nonce Wrapper Driver.
# Produces a single fat JAR that bundles our wrapper + the original NetSuite JDBC driver.
#
# Usage: ./build.sh
# To upgrade the base driver: replace netsuite-jbdc.jar and re-run.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"
OUT_DIR="$SCRIPT_DIR/out"
BUILD_DIR="$SCRIPT_DIR/.build"
BASE_JAR="$SCRIPT_DIR/ref-binaries/netsuite-jbdc.jar"
OUTPUT_JAR="$OUT_DIR/netsuite-jetbrains-driver.jar"

if [[ ! -f "$BASE_JAR" ]]; then
    echo "Error: Base driver not found at $BASE_JAR"
    echo "Place the NetSuite JDBC driver (NQjc.jar) as ref-binaries/netsuite-jbdc.jar in this directory."
    exit 1
fi

echo "=== NetSuite JetBrains Driver Build ==="

# Clean previous build
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$OUT_DIR"

# Phase 1: Compile our wrapper classes against the original JAR
echo "[1/4] Compiling wrapper sources..."
find "$SRC_DIR" -name "*.java" > "$BUILD_DIR/sources.txt"
javac --release 8 \
    -cp "$BASE_JAR" \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/sources.txt"

# Phase 2: Extract original JAR into the build directory
echo "[2/4] Extracting original driver..."
mkdir -p "$BUILD_DIR/fat"
cd "$BUILD_DIR/fat"
jar xf "$BASE_JAR"

# Phase 3: Strip Oracle signature (invalid after modification) and merge our classes
echo "[3/4] Merging wrapper into fat JAR..."
rm -f META-INF/ORACLE_C.SF META-INF/ORACLE_C.RSA META-INF/*.SF META-INF/*.RSA META-INF/*.DSA

# Copy our compiled classes over
cp -r "$BUILD_DIR/classes/"* .

# Replace the service registration to point at our wrapper driver
mkdir -p META-INF/services
cp "$SRC_DIR/META-INF/services/java.sql.Driver" META-INF/services/java.sql.Driver

# Phase 4: Package fat JAR
echo "[4/4] Packaging fat JAR..."
jar cf "$OUTPUT_JAR" .

cd "$SCRIPT_DIR"
rm -rf "$BUILD_DIR"

JAR_SIZE=$(du -h "$OUTPUT_JAR" | cut -f1)
echo ""
echo "Done! Output: $OUTPUT_JAR ($JAR_SIZE)"
echo ""
echo "JetBrains setup:"
echo "  Driver class: com.netsuite.jetbrains.NetsuiteJetbrainsDriver"
echo "  JAR file:     $OUTPUT_JAR"
echo "  Username:     (your NetSuite user)"
echo "  Password:     {\"accountId\":\"...\",\"consumerKey\":\"...\",\"consumerSecret\":\"...\",\"tokenId\":\"...\",\"tokenSecret\":\"...\"}"
