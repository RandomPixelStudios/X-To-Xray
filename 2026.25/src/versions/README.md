# Version-Specific Source Overrides

This directory contains version-specific code overrides for different Minecraft versions.

## Directory Structure

```
src/versions/
  v26_2/      # MC 26.2 specific code (current default)
  v26_1_2/    # MC 26.1.2 specific code
  v26_1_1/    # MC 26.1.1 specific code
  v26_1/      # MC 26.1 specific code
```

## How It Works

1. Common code lives in `src/main/`
2. If a file exists in BOTH `src/main/` AND `src/versions/vXX_X/`, the version-specific file overrides the common one
3. Files that exist ONLY in `src/versions/vXX_X/` are added for that version only
4. Files that exist ONLY in `src/main/` are shared across all versions

## Adding Version-Specific Code

If you need to change a file for a specific MC version:

1. Copy the file from `src/main/` to the appropriate `src/versions/vXX_X/` directory
2. Make your changes in the version-specific copy
3. The common version in `src/main/` remains unchanged

### Example

If `ContainerViewHandler.java` needs different code for MC 26.1:

```
src/main/java/com/xtoxray/client/ContainerViewHandler.java          # Common (26.2)
src/versions/v26_1/java/com/xtoxray/client/ContainerViewHandler.java # 26.1 override
```

## Files That Typically Need Version-Specific Code

These files use MC APIs that may change between versions:

- `ContainerViewHandler.java` - HUD rendering API
- `MixinDebugRenderer.java` - Gizmos API
- `MixinItemInHandRenderer.java` - Rendering pipeline
- `MixinPauseScreen.java` - GUI layout API
- `MixinGui.java` - Render state extraction
- `XrayModMenu.java` - GUI rendering
- `MixinCamera.java` - Camera API
- `XrayPayloads.java` - Network protocol
- `XtoXray.java` - Payload registration
- `XtoXrayClient.java` - Client networking

## Adding a New MC Version

1. Add entry to `versions.json` in project root
2. Create `src/versions/vNEW/` directory
3. If code differs, copy and modify the affected files
4. Run `build-all.bat` (Windows) or `build-all.sh` (Linux/Mac)
