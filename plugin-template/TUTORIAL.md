# XtoXray Plugin Developer Tutorial

This tutorial shows you how to create, compile and import your own plugin for XtoXray.

## Prerequisites

- Java 21 or higher
- Gradle (or the included `gradlew`)
- A text editor or IDE (IntelliJ IDEA, Eclipse, VS Code)

## Step 1: Create a project

Create a new folder for your plugin, e.g. `my-plugin/`. Inside, create the following structure:

```
my-plugin/
  build.gradle
  settings.gradle
  plugin.yml
  src/
    main/
      java/
        com/
          xtoxray/
            myplugin/
              MyPlugin.java
```

## Step 2: Create plugin.yml

The `plugin.yml` describes your plugin:

```yaml
name: MyPlugin
version: 1.0.0
description: My first XtoXray plugin
author: YourName
mainClass: com.xtoxray.myplugin.MyPlugin
```

| Field | Required? | Description |
|-------|-----------|-------------|
| `name` | Yes | Unique plugin name (no spaces) |
| `version` | Yes | Version number, e.g. `1.0.0` |
| `description` | No | Short description |
| `author` | No | Your name |
| `mainClass` | Yes | Full class name of the main class |

## Step 3: Create build.gradle

```gradle
plugins {
    id 'java'
}

group = 'com.xtoxray'
version = '1.0.0'

repositories {
    mavenCentral()
}

dependencies {
    compileOnly files('libs/xtoxray-2026.27-api.jar')
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

jar {
    from sourceSets.main.output
    manifest {
        attributes(
            'Plugin-Class': 'com.xtoxray.myplugin.MyPlugin'
        )
    }
}
```

## Step 4: Implement the plugin class

Create `src/main/java/com/xtoxray/myplugin/MyPlugin.java`:

```java
package com.xtoxray.myplugin;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.PluginLogger;

public class MyPlugin implements Plugin {
    private PluginLogger logger;

    @Override
    public void onEnable(PluginContext context) {
        this.logger = context.getLogger();
        logger.info("MyPlugin enabled!");
        
        // Example: Create custom folder
        // Path dataFolder = context.getDataFolder();
        // Files.createDirectories(dataFolder);
    }

    @Override
    public void onDisable(PluginContext context) {
        logger.info("MyPlugin disabled!");
    }

    @Override
    public String getName() {
        return "MyPlugin";
    }

    @Override
    public String getDescription() {
        return "My first plugin for XtoXray";
    }

    @Override
    public String getAuthor() {
        return "YourName";
    }
}
```

## Step 5: Compile the plugin

```bash
# In your plugin folder
gradlew.bat build
```

The finished JAR will be in `build/libs/MyPlugin-1.0.0.jar`.

## Step 6: Install the plugin

1. Launch Minecraft with XtoXray
2. Open the XtoXray menu
3. Go to the **Plugins** tab
4. Manually copy your `.jar` file into the plugins folder:
   - Location: `<minecraft_dir>/xtoxray/plugins/`
   - This is the same folder where Minecraft is running from
5. The plugin appears in the **Installed** list automatically
6. Use the **Uninstall** button to remove a plugin

## Important notes

### What plugins CANNOT do

- **No access to Minecraft internals** such as blocks, entities or rendering
- **No modification of vanilla behavior** (no mixins)
- **No network code** or server communication

The API is intentionally limited to ensure stability and security.

### Lifecycle

```
onEnable()  →  plugin runs  →  onDisable()
```

- `onEnable()` is called when the plugin is loaded
- `onDisable()` is called when the plugin is uninstalled or Minecraft shuts down

### Logging

Always use the provided logger:

```java
PluginLogger logger = context.getLogger();
logger.info("Info message");
logger.warn("Warning");
logger.error("Error");
```

### Storing data

Each plugin has its own folder:

```java
Path dataFolder = context.getDataFolder();
// Example: .minecraft/xtoxray/plugins/MyPlugin/
```

## Troubleshooting

### "Plugin could not be loaded"

- Check if `plugin.yml` exists in your JAR
- Check if `mainClass` is correct
- Check if the JAR throws any additional errors

### "ClassNotFoundException"

- Make sure the main class is listed in the manifest or `plugin.yml`
- Check the spelling of the package name

### Build failed

- Make sure you are using Java 21
- Ensure the `xtoxray-2026.27-api.jar` is correctly referenced in your `build.gradle`

## Advanced topics

### Events (future)

The event bus API is already in place, but not yet bound to all game events. In the future, plugins will be able to react to:

- Block interactions
- Player events
- Chat messages

### Configuration

Store configurations as JSON in your DataFolder:

```java
import com.google.gson.Gson;

Gson gson = new Gson();
Path config = context.getDataFolder().resolve("config.json");
String json = gson.toJson(myConfigObject);
Files.writeString(config, json);
```

## Support

If you have questions or problems, contact the XtoXray community or create an issue in the repository.
