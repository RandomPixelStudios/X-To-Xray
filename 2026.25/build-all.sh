#!/bin/bash
# ============================================================
# XtoXray Multi-Version Build Script (Linux/Mac)
# ============================================================
# Builds the mod for ALL Minecraft versions defined in versions.json
# Output goes to: dist/<mc_version>/xtoxray-mc<version>.jar
# ============================================================

set -e

echo ""
echo "============================================"
echo " XtoXray Multi-Version Builder"
echo "============================================"
echo ""

# Check if versions.json exists
if [ ! -f "versions.json" ]; then
    echo "ERROR: versions.json not found!"
    echo "Please run this script from the project root directory."
    exit 1
fi

# Check if gradlew exists
if [ ! -f "gradlew" ]; then
    echo "ERROR: gradlew not found!"
    echo "Please run this script from the project root directory."
    exit 1
fi

chmod +x gradlew

echo "Reading versions from versions.json..."
echo ""

COUNT=0
SUCCESS=0
FAILED=0

# Parse versions.json using jq or python
if command -v jq &> /dev/null; then
    # Use jq if available
    VERSIONS=$(jq -r '.versions[] | select(.enabled == true) | "\(.mc_version)|\(.fabric_api)|\(.fabric_loader)|\(.java_version)|\(.modmenu_version)"' versions.json)
elif command -v python3 &> /dev/null; then
    # Use python3 if jq is not available
    VERSIONS=$(python3 -c "
import json
with open('versions.json') as f:
    data = json.load(f)
for v in data['versions']:
    if v.get('enabled', True):
        print(f\"{v['mc_version']}|{v['fabric_api']}|{v['fabric_loader']}|{v['java_version']}|{v['modmenu_version']}\")
")
elif command -v python &> /dev/null; then
    # Use python (Windows)
    VERSIONS=$(python -c "
import json
with open('versions.json') as f:
    data = json.load(f)
for v in data['versions']:
    if v.get('enabled', True):
        print(f\"{v['mc_version']}|{v['fabric_api']}|{v['fabric_loader']}|{v['java_version']}|{v['modmenu_version']}\")
")
else
    echo "ERROR: Neither jq nor python3 found!"
    echo "Please install jq (https://stedolan.github.io/jq/) or python3."
    exit 1
fi

while IFS='|' read -r MC_VER FABRIC_API LOADER JAVA_VER MODMENU; do
    COUNT=$((COUNT + 1))
    
    echo ""
    echo "--------------------------------------------"
    echo " Building for MC ${MC_VER}..."
    echo "   Fabric API: ${FABRIC_API}"
    echo "   Loader:     ${LOADER}"
    echo "   Java:       ${JAVA_VER}"
    echo "--------------------------------------------"
    
    # Clean first to avoid conflicts
    ./gradlew clean > /dev/null 2>&1 || true
    
    # Build with version-specific properties
    if ./gradlew build \
        -Pmc_version="${MC_VER}" \
        -Pfabric_api_version="${FABRIC_API}" \
        -Ploader_version="${LOADER}" \
        -Pjava_version="${JAVA_VER}" \
        -Pmodmenu_version="${MODMENU}" \
        --no-daemon; then
        echo "  [SUCCESS] MC ${MC_VER} built successfully!"
        SUCCESS=$((SUCCESS + 1))
    else
        echo "  [FAILED]  MC ${MC_VER} build failed!"
        FAILED=$((FAILED + 1))
    fi
done <<< "$VERSIONS"

echo ""
echo "============================================"
echo " Build Summary"
echo "============================================"
echo " Total versions: ${COUNT}"
echo " Successful:     ${SUCCESS}"
echo " Failed:         ${FAILED}"
echo "============================================"
echo ""

# List the built JARs
echo "Built JARs in dist/:"
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
    echo "  (no builds found)"
fi

echo ""
echo "Done!"
