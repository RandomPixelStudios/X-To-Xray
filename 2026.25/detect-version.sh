#!/bin/bash
# ============================================================
# XtoXray Version Detector (Linux/Mac)
# ============================================================
# Detects the Minecraft version from a XtoXray JAR file
# Usage: ./detect-version.sh <path-to-jar>
# ============================================================

echo ""
echo "============================================"
echo " XtoXray Version Detector"
echo "============================================"
echo ""

if [ -z "$1" ]; then
    echo "Usage: $0 <path-to-xtoxray-jar>"
    echo ""
    echo "Examples:"
    echo "  $0 dist/26.2/xtoxray-mc26_2-2026.22+26.2.jar"
    echo "  $0 dist/26.1/xtoxray-mc26_1-2026.22+26.1.jar"
    exit 1
fi

JAR_PATH="$1"

if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: File not found: $JAR_PATH"
    exit 1
fi

echo "Checking JAR: $JAR_PATH"
echo ""

# Method 1: Check JAR filename for MC version
JAR_NAME=$(basename "$JAR_PATH" .jar)
echo "JAR filename: $JAR_NAME"

# Extract MC version from filename pattern: xtoxray-mcXX_Y-modversion
if echo "$JAR_NAME" | grep -qP 'mc\d'; then
    MC_PART=$(echo "$JAR_NAME" | grep -oP 'mc\d+_\d+')
    echo "Detected from filename: $MC_PART"
fi

# Method 2: Check JAR manifest for MC version
echo ""
echo "Checking JAR manifest..."
if unzip -p "$JAR_PATH" META-INF/MANIFEST.MF 2>/dev/null | grep -q "XtoXray-MC-Version"; then
    MC_VERSION=$(unzip -p "$JAR_PATH" META-INF/MANIFEST.MF 2>/dev/null | grep "XtoXray-MC-Version" | cut -d: -f2 | tr -d ' ')
    echo "Detected MC version from manifest: $MC_VERSION"
fi

# Method 3: Check fabric.mod.json inside the JAR
echo ""
echo "Checking fabric.mod.json inside JAR..."
if unzip -l "$JAR_PATH" 2>/dev/null | grep -q "fabric.mod.json"; then
    if command -v python3 &> /dev/null; then
        unzip -p "$JAR_PATH" fabric.mod.json 2>/dev/null | python3 -c "
import json, sys
data = json.load(sys.stdin)
print(f\"Mod version: {data.get('version', 'unknown')}\")
deps = data.get('depends', {})
if 'minecraft' in deps:
    print(f\"MC dependency: {deps['minecraft']}\")
"
    elif command -v python &> /dev/null; then
        unzip -p "$JAR_PATH" fabric.mod.json 2>/dev/null | python -c "
import json, sys
data = json.load(sys.stdin)
print('Mod version:', data.get('version', 'unknown'))
deps = data.get('depends', {})
if 'minecraft' in deps:
    print('MC dependency:', deps['minecraft'])
"
    elif command -v jq &> /dev/null; then
        unzip -p "$JAR_PATH" fabric.mod.json 2>/dev/null | jq -r '"Mod version: \(.version)\nMC dependency: \(.depends.minecraft // "unknown")"'
    else
        echo "  (install python3 or jq for detailed info)"
    fi
else
    echo "  fabric.mod.json not found in JAR"
fi

echo ""
echo "============================================"
echo " Supported XtoXray versions in dist/:"
echo "============================================"
echo ""

if [ -d "dist" ]; then
    for dir in dist/*/; do
        if [ -d "$dir" ]; then
            echo "  ${dir}"
            for jar in "${dir}"*.jar; do
                if [ -f "$jar" ]; then
                    echo "    $(basename "$jar")"
                fi
            done
        fi
    done
else
    echo "  (dist/ directory not found)"
fi

echo ""
