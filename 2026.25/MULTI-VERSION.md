# XtoXray - Multi-Version Build System

This project supports building XtoXray for multiple Minecraft versions from a single codebase.

## Supported Versions

| Minecraft | Fabric API | Fabric Loader | Status |
|-----------|-----------|---------------|--------|
| 26.2      | 0.154.2+26.2 | 0.19.3     | ✅ Default |
| 26.1.2    | 0.153.1+26.1.2 | 0.19.3   | ✅ |
| 26.1.1    | 0.153.0+26.1.1 | 0.19.3   | ✅ |
| 26.1      | 0.152.0+26.1  | 0.19.3    | ✅ |

## Quick Start

### Build ALL versions at once

**Windows:**
```batch
build-all.bat
```

**Linux/Mac:**
```bash
chmod +x build-all.sh
./build-all.sh
```

### Build a single version

```bash
# Build for MC 26.2 (default)
./gradlew build

# Build for MC 26.1
./gradlew build -Pmc_version=26.1 -Pfabric_api_version=0.152.0+26.1

# Build for MC 26.1.2
./gradlew build -Pmc_version=26.1.2 -Pfabric_api_version=0.153.1+26.1.2
```

### Output

Built JARs are placed in:
```
dist/
  26.2/xtoxray-mc26_2-2026.22+26.2.jar
  26.1.2/xtoxray-mc26_1_2-2026.22+26.1.2.jar
  26.1.1/xtoxray-mc26_1_1-2026.22+26.1.1.jar
  26.1/xtoxray-mc26_1-2026.22+26.1.jar
```

## Detecting MC Version from a JAR

Use the detect-version script to check which MC version a built JAR targets:

**Windows:**
```batch
detect-version.bat dist\26.2\xtoxray-mc26_2-2026.22+26.2.jar
```

**Linux/Mac:**
```bash
./detect-version.sh dist/26.2/xtoxray-mc26_2-2026.22+26.2.jar
```

## Adding a New MC Version

### Step 1: Add to versions.json

Edit `versions.json` and add a new entry:

```json
{
  "mc_version": "26.3",
  "fabric_loader": "0.19.3",
  "fabric_api": "0.155.0+26.3",
  "java_version": "25",
  "loom_version": "1.17.13",
  "modmenu_version": "20.0.1",
  "source_dir": "v26_3",
  "enabled": true
}
```

### Step 2: Create version-specific source directory (if needed)

If the new version has API changes, create a version-specific directory:

```bash
mkdir -p src/versions/v26_3/java/com/xtoxray/client
```

Copy the files that need modification from `src/main/` to `src/versions/v26_3/` and make your changes.

### Step 3: Build

```bash
./gradlew build -Pmc_version=26.3 -Pfabric_api_version=0.155.0+26.3
```

## Version-Specific Source Overrides

The build system uses a **source set merging** approach:

```
src/
  main/                    # Common code (shared across all versions)
    java/com/xtoxray/...   
  versions/
    v26_2/                 # MC 26.2 specific overrides (if any)
    v26_1_2/               # MC 26.1.2 specific overrides (if any)
    v26_1_1/               # MC 26.1.1 specific overrides (if any)
    v26_1/                 # MC 26.1 specific overrides (if any)
```

**Rules:**
1. Files in `src/main/` are shared across ALL versions
2. If a file exists in BOTH `src/main/` AND `src/versions/vXX_X/`, the version-specific file **overrides** the common one
3. Files that exist ONLY in `src/versions/vXX_X/` are added for that version only

### When to use version-specific files

Only create version-specific files when the Minecraft API changes between versions. Common API changes include:

- **Rendering API** changes (GuiGraphicsExtractor, PoseStack, etc.)
- **Network API** changes (CustomPacketPayload, StreamCodec, etc.)
- **Mixin targets** (method names, parameters, injection points)
- **GUI API** changes (Screen, Button, Layout classes)
- **HUD API** changes (HudElementRegistry, VanillaHudElements)

### Example: Different Mixin for MC 26.1

If `MixinCamera.java` needs different code for MC 26.1:

```
src/main/java/com/xtoxray/client/mixin/MixinCamera.java          # Common (26.2)
src/versions/v26_1/java/com/xtoxray/client/mixin/MixinCamera.java # 26.1 override
```

## Project Structure

```
XtoXray/
├── build.gradle              # Multi-version build config
├── gradle.properties         # Default version properties
├── versions.json             # All supported MC versions
├── build-all.bat             # Windows: build all versions
├── build-all.sh              # Linux/Mac: build all versions
├── detect-version.bat        # Windows: detect MC version from JAR
├── detect-version.sh         # Linux/Mac: detect MC version from JAR
├── src/
│   ├── main/                 # Common source code
│   │   ├── java/...
│   │   └── resources/
│   └── versions/             # Version-specific overrides
│       ├── README.md
│       ├── v26_2/
│       ├── v26_1_2/
│       ├── v26_1_1/
│       └── v26_1/
├── dist/                     # Built JARs (organized by version)
│   ├── 26.2/
│   ├── 26.1.2/
│   ├── 26.1.1/
│   └── 26.1/
└── MULTI-VERSION.md          # This file
```

## Troubleshooting

### "Could not resolve" errors for Fabric API

The Fabric API versions in `versions.json` are estimates. If a version doesn't exist on Maven:

1. Check https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
2. Find the correct version for your MC version
3. Update `versions.json`

### Build fails for a specific version

1. Check if the Fabric API version exists
2. Check if there are API changes that need version-specific code
3. Create version-specific source files in `src/versions/v<version>/`

### "Source set not found" errors

Make sure the version-specific directory name matches the pattern `v<mc_version_with_underscores>`:
- MC 26.2 → `v26_2`
- MC 26.1.2 → `v26_1_2`
- MC 1.21.4 → `v1_21_4`
