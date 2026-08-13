#!/bin/bash
# ============================================================
# XtoXray Single-Version Build (Linux/Mac)
# ============================================================
# Usage: ./build.sh [mc_version]
#
# Examples:
#   ./build.sh          (build default version from gradle.properties)
#   ./build.sh 26.1     (build for MC 26.1)
#   ./build.sh 26.1.2   (build for MC 26.1.2)
# ============================================================

echo ""
echo "============================================"
echo " XtoXray Single-Version Builder"
echo "============================================"
echo ""

if [ -z "$1" ]; then
    echo "Building default version from gradle.properties..."
    echo ""
    ./gradlew build --no-daemon
else
    TARGET_VERSION="$1"
    echo "Building for MC ${TARGET_VERSION}..."
    
    # Read config from versions.json
    if command -v jq &> /dev/null; then
        CONFIG=$(jq -r ".versions[] | select(.mc_version == \"${TARGET_VERSION}\" and .enabled == true) | \"\(.fabric_api)|\(.fabric_loader)|\(.java_version)|\(.modmenu_version)\"" versions.json)
    elif command -v python3 &> /dev/null; then
        CONFIG=$(python3 -c "
import json
with open('versions.json') as f:
    data = json.load(f)
for v in data['versions']:
    if v['mc_version'] == '${TARGET_VERSION}' and v.get('enabled', True):
        print(f\"{v['fabric_api']}|{v['fabric_loader']}|{v['java_version']}|{v['modmenu_version']}\")
        break
")
    else
        echo "ERROR: jq or python3 required"
        exit 1
    fi
    
    if [ -z "$CONFIG" ]; then
        echo "ERROR: Version ${TARGET_VERSION} not found or disabled in versions.json"
        echo ""
        echo "Available versions:"
        if command -v jq &> /dev/null; then
            jq -r '.versions[] | select(.enabled == true) | "  - \(.mc_version)"' versions.json
        elif command -v python3 &> /dev/null; then
            python3 -c "
import json
with open('versions.json') as f:
    data = json.load(f)
for v in data['versions']:
    if v.get('enabled', True):
        print(f'  - {v[\"mc_version\"]}')
"
        fi
        exit 1
    fi
    
    IFS='|' read -r FABRIC_API LOADER JAVA_VER MODMENU <<< "$CONFIG"
    
    echo "  Fabric API: ${FABRIC_API}"
    echo "  Loader:     ${LOADER}"
    echo "  Java:       ${JAVA_VER}"
    echo ""
    
    ./gradlew build \
        -Pmc_version="${TARGET_VERSION}" \
        -Pfabric_api_version="${FABRIC_API}" \
        -Ploader_version="${LOADER}" \
        -Pjava_version="${JAVA_VER}" \
        -Pmodmenu_version="${MODMENU}" \
        --no-daemon
fi

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================"
    echo " Build successful!"
    echo "============================================"
    echo ""
    echo "Output JARs in dist/:"
    find dist -name "*.jar" 2>/dev/null | while read jar; do
        echo "  $jar"
    done
else
    echo ""
    echo "============================================"
    echo " Build FAILED!"
    echo "============================================"
fi

echo ""
