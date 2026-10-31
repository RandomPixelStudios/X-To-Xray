package com.xtoxray.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** A running plugin: its sandbox plus the "xtx" API, limited to the permissions it declared. */
public final class LuaPlugin {
    public enum WidgetKind { TOGGLE, SLIDER, CYCLE, BLOCKS }

    public record Widget(WidgetKind kind, String key, String label, int min, int max, List<String> options, Object fallback) {
    }

    private static final String SETTINGS_FILE = "settings.host";
    private static final int MAX_ERRORS = 5;
    private static final int MAX_WIDGETS = 32;
    private static final int MAX_BLOCKS = 1024;
    private static final int MAX_MINE = 1024;
    private static final Gson GSON = new Gson();

    private final PluginDefinition definition;
    private final PluginHost host;
    private final PluginStore store;
    private final LuaSandbox sandbox = new LuaSandbox();
    private final Map<String, List<LuaValue>> handlers = new HashMap<>();
    private final List<Widget> widgets = new ArrayList<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Object> saved = new HashMap<>();
    private int errors;
    private boolean stopped;

    public LuaPlugin(PluginDefinition definition, PluginHost host, java.nio.file.Path dataFolder) {
        this.definition = definition;
        this.host = host;
        this.store = new PluginStore(dataFolder);
        loadSettings();
        sandbox.globals().set("xtx", buildApi());
    }

    /** @return null on success, otherwise why the plugin could not start */
    public String start() {
        try {
            sandbox.run(definition.source(), definition.id());
            return null;
        } catch (LuaError | LuaSandbox.BudgetExceeded | StackOverflowError | OutOfMemoryError e) {
            stopped = true;
            handlers.clear();
            return e.getMessage();
        }
    }

    public boolean isStopped() { return stopped; }
    public PluginDefinition definition() { return definition; }
    public List<Widget> widgets() { return List.copyOf(widgets); }
    public Object value(String key) { return values.get(key); }

    public void setValue(String key, Object value) {
        values.put(key, value);
    }

    public void resetSettings() {
        for (Widget w : widgets) {
            values.put(w.key(), w.fallback() instanceof List<?> l ? new ArrayList<>(l) : w.fallback());
        }
    }

    /** Runs all handlers of an event. A misbehaving plugin is stopped, never the game. */
    public void fire(String event, LuaValue... args) {
        List<LuaValue> list = handlers.get(event);
        if (list == null || stopped) return;
        Varargs varargs = LuaValue.varargsOf(args);
        for (LuaValue handler : List.copyOf(list)) {
            try {
                sandbox.call(handler, varargs);
            } catch (LuaError e) {
                host.log(definition.id(), "Error: " + e.getMessage());
                if (++errors >= MAX_ERRORS) stop("too many errors");
            } catch (LuaSandbox.BudgetExceeded | StackOverflowError | OutOfMemoryError e) {
                stop(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (stopped) return;
        }
    }

    private void stop(String reason) {
        stopped = true;
        handlers.clear();
        host.log(definition.id(), "Stopped: " + reason);
    }

    private boolean has(PluginPermission permission) {
        return definition.permissions().contains(permission);
    }

    private LuaTable buildApi() {
        LuaTable api = new LuaTable();
        api.set("log", fn(a -> {
            host.log(definition.id(), a.checkjstring(1));
            return LuaValue.NONE;
        }));
        api.set("on", fn(a -> {
            String event = a.checkjstring(1);
            if (!Set.of("tick", "block_mined", "xray_toggle", "render").contains(event)) throw new LuaError("Unknown event: " + event);
            if (event.equals("render") && !has(PluginPermission.RENDER)) throw new LuaError("Needs permission: render");
            handlers.computeIfAbsent(event, k -> new ArrayList<>()).add(a.checkfunction(2));
            return LuaValue.NONE;
        }));
        api.set("setting", fn(a -> toLua(values.get(a.checkjstring(1)))));
        api.set("ui", buildUi());
        if (has(PluginPermission.CHAT)) api.set("chat", fn(a -> {
            host.chat(PluginDefinition.clean(a.checkjstring(1)));
            return LuaValue.NONE;
        }));
        if (has(PluginPermission.STORAGE)) api.set("storage", buildStorage());
        LuaTable world = new LuaTable();
        if (has(PluginPermission.WORLD_READ)) {
            world.set("block", fn(a -> {
                String id = host.blockAt(a.checkint(1), a.checkint(2), a.checkint(3));
                return id == null ? LuaValue.NIL : LuaValue.valueOf(id);
            }));
            world.set("player", fn(a -> {
                double[] p = host.playerPosition();
                if (p == null) return LuaValue.NIL;
                return LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(p[0]), LuaValue.valueOf(p[1]), LuaValue.valueOf(p[2])});
            }));
        }
        if (has(PluginPermission.ENTITIES_READ)) world.set("entities", fn(a -> {
            LuaTable result = new LuaTable();
            int i = 1;
            for (PluginHost.EntityBox e : host.entities(a.checkdouble(1))) {
                LuaTable t = new LuaTable();
                t.set("kind", e.kind());
                t.set("min_x", e.minX()); t.set("min_y", e.minY()); t.set("min_z", e.minZ());
                t.set("max_x", e.maxX()); t.set("max_y", e.maxY()); t.set("max_z", e.maxZ());
                result.set(i++, t);
            }
            return result;
        }));
        if (has(PluginPermission.WORLD_MINE)) world.set("mine", fn(a -> {
            LuaTable list = a.checktable(1);
            LuaValue options = a.opttable(2, new LuaTable());
            if (list.length() > MAX_MINE) throw new LuaError("Too many blocks");
            List<PluginHost.Pos> positions = new ArrayList<>();
            for (int i = 1; i <= list.length(); i++) {
                LuaValue p = list.get(i);
                positions.add(new PluginHost.Pos(p.get("x").checkint(), p.get("y").checkint(), p.get("z").checkint()));
            }
            host.mine(positions, options.get("silk").optjstring("tool"), Math.max(0, Math.min(10, options.get("durability").optint(0))));
            return LuaValue.NONE;
        }));
        api.set("world", world);
        if (has(PluginPermission.RENDER)) {
            LuaTable draw = new LuaTable();
            draw.set("box", fn(a -> {
                host.drawBox(a.checkdouble(1), a.checkdouble(2), a.checkdouble(3), a.checkdouble(4), a.checkdouble(5), a.checkdouble(6),
                    (int) a.optlong(7, 0xFFFF0000L), a.optboolean(8, false));
                return LuaValue.NONE;
            }));
            api.set("draw", draw);
        }
        if (has(PluginPermission.XRAY)) {
            LuaTable xray = new LuaTable();
            xray.set("active", fn(a -> LuaValue.valueOf(host.xrayActive())));
            xray.set("blocks", fn(a -> toLua(new ArrayList<>(host.xrayBlocks()))));
            xray.set("add", fn(a -> LuaValue.valueOf(host.xrayChange(a.checkjstring(1), true))));
            xray.set("remove", fn(a -> LuaValue.valueOf(host.xrayChange(a.checkjstring(1), false))));
            api.set("xray", xray);
        }
        return api;
    }

    private LuaTable buildUi() {
        LuaTable ui = new LuaTable();
        ui.set("toggle", fn(a -> add(new Widget(WidgetKind.TOGGLE, a.checkjstring(1), PluginDefinition.clean(a.checkjstring(2)), 0, 0, List.of(), a.optboolean(3, false)))));
        ui.set("slider", fn(a -> {
            int min = a.checkint(3);
            int max = Math.max(min, a.checkint(4));
            return add(new Widget(WidgetKind.SLIDER, a.checkjstring(1), PluginDefinition.clean(a.checkjstring(2)), min, max, List.of(), Math.max(min, Math.min(max, a.optint(5, min)))));
        }));
        ui.set("cycle", fn(a -> {
            List<String> options = strings(a.checktable(3), 32);
            if (options.isEmpty()) throw new LuaError("cycle needs options");
            String fallback = a.optjstring(4, options.get(0));
            return add(new Widget(WidgetKind.CYCLE, a.checkjstring(1), PluginDefinition.clean(a.checkjstring(2)), 0, 0, List.copyOf(options), fallback));
        }));
        ui.set("blocks", fn(a -> add(new Widget(WidgetKind.BLOCKS, a.checkjstring(1), PluginDefinition.clean(a.checkjstring(2)), 0, 0, List.of(), strings(a.opttable(3, new LuaTable()), MAX_BLOCKS)))));
        return ui;
    }

    private Varargs add(Widget widget) {
        if (widgets.size() >= MAX_WIDGETS) throw new LuaError("Too many settings");
        widgets.add(widget);
        Object stored = saved.get(widget.key());
        boolean usable = stored != null && (widget.fallback() instanceof List<?> ? stored instanceof List<?>
            : widget.fallback().getClass().isInstance(stored));
        Object value = usable ? stored : widget.fallback();
        if (value instanceof Integer i) value = Math.max(widget.min(), Math.min(widget.max(), i));
        values.put(widget.key(), value instanceof List<?> l ? new ArrayList<>(l) : value);
        return LuaValue.NONE;
    }

    private static List<String> strings(LuaTable table, int limit) {
        if (table.length() > limit) throw new LuaError("List is too long");
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= table.length(); i++) result.add(table.get(i).checkjstring());
        return result;
    }

    private LuaTable buildStorage() {
        LuaTable storage = new LuaTable();
        storage.set("read", fn(a -> io(() -> store.read(checkName(a)).<LuaValue>map(LuaValue::valueOf).orElse(LuaValue.NIL))));
        storage.set("write", fn(a -> io(() -> {
            store.write(checkName(a), a.checkjstring(2));
            return LuaValue.NONE;
        })));
        storage.set("delete", fn(a -> io(() -> {
            store.delete(checkName(a));
            return LuaValue.NONE;
        })));
        return storage;
    }

    private static String checkName(Varargs a) {
        String name = a.checkjstring(1);
        if (name.endsWith(".host")) throw new LuaError("Invalid file name");
        return name;
    }

    private interface IoCall {
        Varargs run() throws IOException;
    }

    private static Varargs io(IoCall call) {
        try {
            return call.run();
        } catch (IOException | IllegalArgumentException e) {
            throw new LuaError(e.getMessage());
        }
    }

    private static LuaValue fn(java.util.function.Function<Varargs, Varargs> body) {
        return LuaSandbox.function(body);
    }

    private static LuaValue toLua(Object value) {
        if (value instanceof Boolean b) return LuaValue.valueOf(b);
        if (value instanceof Integer i) return LuaValue.valueOf(i);
        if (value instanceof String s) return LuaValue.valueOf(s);
        if (value instanceof List<?> list) {
            LuaTable t = new LuaTable();
            int i = 1;
            for (Object o : list) t.set(i++, LuaValue.valueOf(String.valueOf(o)));
            return t;
        }
        return LuaValue.NIL;
    }

    private void loadSettings() {
        try {
            String json = readSettingsFile();
            if (json == null) return;
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            for (String key : root.keySet()) {
                JsonElement e = root.get(key);
                if (e.isJsonArray()) {
                    List<String> list = new ArrayList<>();
                    e.getAsJsonArray().forEach(x -> list.add(x.getAsString()));
                    saved.put(key, list);
                } else if (e.getAsJsonPrimitive().isBoolean()) {
                    saved.put(key, e.getAsBoolean());
                } else if (e.getAsJsonPrimitive().isNumber()) {
                    saved.put(key, e.getAsInt());
                } else {
                    saved.put(key, e.getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            host.log(definition.id(), "Could not read settings: " + e.getMessage());
        }
    }

    private String readSettingsFile() throws IOException {
        return store.read(SETTINGS_FILE).orElse(null);
    }

    public void saveSettings() {
        JsonObject root = new JsonObject();
        values.forEach((key, value) -> {
            if (value instanceof Boolean b) root.addProperty(key, b);
            else if (value instanceof Integer i) root.addProperty(key, i);
            else if (value instanceof String s) root.addProperty(key, s);
            else if (value instanceof List<?> list) {
                JsonArray array = new JsonArray();
                list.forEach(o -> array.add(String.valueOf(o)));
                root.add(key, array);
            }
        });
        try {
            store.write(SETTINGS_FILE, GSON.toJson(root));
        } catch (IOException e) {
            host.log(definition.id(), "Could not save settings: " + e.getMessage());
        }
    }
}
