# XtoXray Plugin Tutorial (API 2)

1. Copy this folder and put `xtoxray-<version>-api.jar` (from the XtoXray build) into `libs/`.
2. Edit `src/main/resources/plugin.yml` and rename the package and main class. Your package must not start with `com.xtoxray`.
3. Run `gradle build`. The plugin is in `build/libs/`.
4. Drag the jar onto the **Plugins** tab of the XtoXray menu and press **Allow**.

## plugin.yml

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Unique, 2-32 characters: `a-z`, `0-9`, `_`, `-` |
| `name`, `version` | yes | Shown in the menu |
| `mainClass` | yes | Class implementing `Plugin`, with a public no-argument constructor |
| `apiVersion` | yes | Must be `2` |
| `description`, `author` | no | Shown in the menu |
| `permissions` | no | Comma-separated list, see below |

| Permission | Allows |
|---|---|
| `storage` | `context.getStorage()`: private text files, 1 MiB each, 16 MiB total |
| `xray.read` | `context.getXray()`: status and whitelist |
| `xray.whitelist` | Changing the whitelist (also needs `xray.read`) |

## Events

Register with `context.getEventBus().register(this)` and mark public methods with `@Subscribe`:

- `XrayToggleEvent(active)`
- `BlockMinedEvent(blockId, x, y, z)`
- `VeinMinedEvent(blockId, count)`

Handlers run on the client thread, so keep them fast. A plugin is stopped after 5 errors.

## What is blocked

Every class in the jar is checked before it loads. A plugin may only use its own classes, the XtoXray API and basic Java (`java.lang` basics, `java.util`, streams, regex, `java.time`, `java.math`, `java.text`).

Blocked: files, network, processes, threads, reflection, method handles, native code, Minecraft and Fabric classes, nested jars and bundled libraries that use any of these. The menu shows the exact reason when a jar is blocked. `System.out` is blocked too; use `context.getLogger()`.

## Publishing

Open a pull request at https://github.com/RandomPixelStudios/X-To-Xray-Plugins. Its README explains the steps.
