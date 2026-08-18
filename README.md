![title](https://cdn.modrinth.com/data/cached_images/049be141d08297f59c5db885b9842dfdb9abf063.png)

[README.md](https://github.com/user-attachments/files/31196689/README.md)
# XtoXray Plugin Development

## Creating a Plugin

1. Create a new Java project
2. Add the XtoXray API to your dependencies
3. Implement the `Plugin` interface
4. Create a `plugin.yml` file with your plugin metadata
5. Build as a JAR and drop it into the XtoXray plugins folder

## Plugin Structure

```
src/main/java/com/xtoxray/yourplugin/
  YourPlugin.java
  listeners/
    YourListener.java
```

## plugin.yml

```yaml
name: YourPlugin
version: 1.0.0
description: What your plugin does
author: YourName
mainClass: com.xtoxray.yourplugin.YourPlugin
```

| Field | Required? | Description |
|-------|-----------|-------------|
| `name` | Yes | Unique plugin name (no spaces) |
| `version` | Yes | Version number, e.g. `1.0.0` |
| `description` | No | Short description |
| `author` | No | Your name |
| `mainClass` | Yes | Full class name of the main class |

## Plugin Interface

```java
public interface Plugin {
    void onEnable(PluginContext context);
    void onDisable(PluginContext context);
    String getName();
    String getDescription();
    String getAuthor();
}
```

## PluginContext

Provides access to:
- `getLogger()` - PluginLogger for your plugin
- `getDataFolder()` - Path to store plugin data
- `getEventBus()` - Event system for listening to game events

## Events

Create event classes and listeners:

```java
public class MyEvent {
    // event data
}

public class MyListener {
    @Subscribe
    public void onMyEvent(MyEvent event) {
        // handle event
    }
}
```

## Building

```bash
./gradlew build
```

The output JAR can be dropped into the XtoXray plugins folder.

## API Location

The plugin API classes are bundled with XtoXray:
- `com.xtoxray.api.plugin.Plugin`
- `com.xtoxray.api.plugin.PluginContext`
- `com.xtoxray.api.plugin.PluginLogger`
- `com.xtoxray.api.plugin.event.EventBus`
- `com.xtoxray.api.plugin.event.Listener`
- `com.xtoxray.api.plugin.event.Subscribe`
