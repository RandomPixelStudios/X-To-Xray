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
    private static final int MAX_TIMERS = 256;
    private static final int MAX_LISTED = 64;
    private static final int MAX_JSON_DEPTH = 32;
    private static final Gson GSON = new Gson();

    private final PluginDefinition definition;
    private final PluginHost host;
    private final PluginStore store;
    private final LuaSandbox sandbox = new LuaSandbox();
    private final Map<String, List<LuaValue>> handlers = new HashMap<>();
    private final List<Widget> widgets = new ArrayList<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Object> saved = new HashMap<>();
    private final Set<String> hiddenHud = new java.util.HashSet<>();
    private final Map<String, LuaValue> commands = new LinkedHashMap<>();
    private final Map<String, LuaValue> keyBinds = new LinkedHashMap<>();
    private final Map<String, Boolean> keyStates = new HashMap<>();
    private final Map<Integer, Timer> timers = new LinkedHashMap<>();
    private int nextTimer = 1;
    private boolean usedControls;
    private int errors;
    private boolean stopped;

    private static final class Timer {
        final LuaValue function;
        final int every;
        int remaining;

        Timer(LuaValue function, int delay, int every) {
            this.function = function;
            this.remaining = delay;
            this.every = every;
        }
    }

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

    public Set<String> hiddenHud() { return stopped ? Set.of() : hiddenHud; }

    /** Runs all handlers of an event. A misbehaving plugin is stopped, never the game. @return false if a handler returned false */
    public boolean fire(String event, LuaValue... args) {
        if (stopped) return true;
        if (event.equals("tick")) runTick();
        List<LuaValue> list = handlers.get(event);
        if (list == null || stopped) return true;
        boolean allowed = true;
        Varargs varargs = LuaValue.varargsOf(args);
        for (LuaValue handler : List.copyOf(list)) {
            Varargs result = guarded(handler, varargs);
            if (result != null && result.arg1().isboolean() && !result.arg1().toboolean()) allowed = false;
            if (stopped) break;
        }
        return allowed;
    }

    /** @return true if this plugin registered the command */
    public boolean runCommand(String name, String arguments) {
        LuaValue command = commands.get(name);
        if (command == null || stopped) return false;
        guarded(command, LuaValue.valueOf(arguments));
        return true;
    }

    public Set<String> commandNames() { return stopped ? Set.of() : Set.copyOf(commands.keySet()); }

    private void runTick() {
        for (Map.Entry<String, LuaValue> bind : List.copyOf(keyBinds.entrySet())) {
            boolean down = host.keyDown(bind.getKey());
            boolean was = Boolean.TRUE.equals(keyStates.put(bind.getKey(), down));
            if (down && !was) guarded(bind.getValue(), LuaValue.NONE);
            if (stopped) return;
        }
        for (Map.Entry<Integer, Timer> entry : List.copyOf(timers.entrySet())) {
            Timer timer = entry.getValue();
            if (--timer.remaining > 0) continue;
            if (timer.every > 0) timer.remaining = timer.every;
            else timers.remove(entry.getKey());
            guarded(timer.function, LuaValue.NONE);
            if (stopped) return;
        }
    }

    private Varargs guarded(LuaValue function, Varargs args) {
        try {
            return sandbox.call(function, args);
        } catch (LuaError e) {
            host.log(definition.id(), "Error: " + e.getMessage());
            if (++errors >= MAX_ERRORS) stop("too many errors");
        } catch (LuaSandbox.BudgetExceeded | StackOverflowError | OutOfMemoryError e) {
            stop(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return null;
    }

    private void stop(String reason) {
        stopped = true;
        handlers.clear();
        timers.clear();
        commands.clear();
        keyBinds.clear();
        hiddenHud.clear();
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
            if (!Set.of("tick", "block_mined", "xray_toggle", "render", "hud", "join", "leave", "shutdown", "chat_message").contains(event)) throw new LuaError("Unknown event: " + event);
            if (event.equals("render") && !has(PluginPermission.RENDER)) throw new LuaError("Needs permission: render");
            if (event.equals("hud") && !has(PluginPermission.HUD)) throw new LuaError("Needs permission: hud");
            if (event.equals("chat_message") && !has(PluginPermission.CHAT_READ)) throw new LuaError("Needs permission: chat.read");
            handlers.computeIfAbsent(event, k -> new ArrayList<>()).add(a.checkfunction(2));
            return LuaValue.NONE;
        }));
        api.set("setting", fn(a -> toLua(values.get(a.checkjstring(1)))));
        api.set("set_setting", fn(a -> {
            String key = a.checkjstring(1);
            Object current = values.get(key);
            if (current == null) throw new LuaError("Unknown setting: " + key);
            LuaValue v = a.arg(2);
            if (current instanceof Boolean) values.put(key, v.checkboolean());
            else if (current instanceof Integer) {
                Widget w = widgets.stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow();
                values.put(key, Math.max(w.min(), Math.min(w.max(), v.checkint())));
            } else if (current instanceof String) values.put(key, v.checkjstring());
            else values.put(key, strings(v.checktable(), MAX_BLOCKS));
            return LuaValue.NONE;
        }));
        api.set("after", fn(a -> addTimer(a.checkint(1), 0, a.checkfunction(2))));
        api.set("every", fn(a -> addTimer(a.checkint(1), Math.max(1, a.checkint(1)), a.checkfunction(2))));
        api.set("cancel", fn(a -> {
            timers.remove(a.checkint(1));
            return LuaValue.NONE;
        }));
        api.set("command", fn(a -> {
            String name = a.checkjstring(1);
            if (!name.matches("[a-z0-9_-]{1,32}")) throw new LuaError("Invalid command name");
            if (commands.size() >= MAX_LISTED && !commands.containsKey(name)) throw new LuaError("Too many commands");
            commands.put(name, a.checkfunction(2));
            return LuaValue.NONE;
        }));
        api.set("time", fn(a -> LuaValue.valueOf((double) System.currentTimeMillis())));
        LuaTable json = new LuaTable();
        json.set("encode", fn(a -> LuaValue.valueOf(GSON.toJson(toJson(a.arg1(), 0)))));
        json.set("decode", fn(a -> {
            String text = a.checkjstring(1);
            if (text.length() > PluginDefinition.MAX_BYTES * 4) throw new LuaError("JSON is too large");
            try {
                return fromJson(com.google.gson.JsonParser.parseString(text), 0);
            } catch (com.google.gson.JsonParseException e) {
                throw new LuaError("Invalid JSON");
            }
        }));
        api.set("json", json);
        api.set("ui", buildUi());
        if (has(PluginPermission.HUD)) api.set("hud", buildHud());
        if (has(PluginPermission.PLAYER_READ) || has(PluginPermission.PLAYER_CONTROL)) api.set("player", buildPlayer());
        if (has(PluginPermission.INPUT)) {
            LuaTable input = new LuaTable();
            input.set("is_down", fn(a -> LuaValue.valueOf(host.keyDown(a.checkjstring(1)))));
            input.set("bind", fn(a -> {
                String key = a.checkjstring(1);
                if (keyBinds.size() >= MAX_LISTED && !keyBinds.containsKey(key)) throw new LuaError("Too many key binds");
                keyBinds.put(key, a.checkfunction(2));
                return LuaValue.NONE;
            }));
            api.set("input", input);
        }
        if (has(PluginPermission.SOUND)) {
            LuaTable sound = new LuaTable();
            sound.set("play", fn(a -> LuaValue.valueOf(host.playSound(a.checkjstring(1), a.optdouble(2, 1), a.optdouble(3, 1)))));
            api.set("sound", sound);
        }
        if (has(PluginPermission.OPTIONS)) {
            LuaTable options = new LuaTable();
            options.set("get", fn(a -> toLua(host.option(a.checkjstring(1)))));
            options.set("set", fn(a -> {
                LuaValue v = a.arg(2);
                Object value = v.isboolean() ? (Object) v.toboolean() : (Object) v.checkdouble();
                return LuaValue.valueOf(host.setOption(a.checkjstring(1), value));
            }));
            api.set("options", options);
        }
        if (has(PluginPermission.CHAT)) api.set("chat", fn(a -> {
            host.chat(PluginDefinition.clean(a.checkjstring(1)));
            return LuaValue.NONE;
        }));
        if (has(PluginPermission.CHAT)) {
            api.set("actionbar", fn(a -> {
                host.actionBar(PluginDefinition.clean(a.checkjstring(1)));
                return LuaValue.NONE;
            }));
            api.set("title", fn(a -> {
                host.title(PluginDefinition.clean(a.checkjstring(1)), PluginDefinition.clean(a.optjstring(2, "")));
                return LuaValue.NONE;
            }));
        }
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
            world.set("info", fn(a -> toLua(host.worldInfo())));
            world.set("light", fn(a -> toLua(host.lightAt(a.checkint(1), a.checkint(2), a.checkint(3)))));
            world.set("biome", fn(a -> toLua(host.biomeAt(a.checkint(1), a.checkint(2), a.checkint(3)))));
            world.set("looking_at", fn(a -> toLua(host.lookingAt(Math.max(0, Math.min(128, a.optdouble(1, 5)))))));
        }
        if (has(PluginPermission.ENTITIES_READ)) world.set("entity_list", fn(a -> toLua(host.entityList(a.checkdouble(1)))));
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
            draw.set("line", fn(a -> {
                host.drawLine(a.checkdouble(1), a.checkdouble(2), a.checkdouble(3), a.checkdouble(4), a.checkdouble(5), a.checkdouble(6),
                    (int) a.optlong(7, 0xFFFF0000L), Math.max(0.1, Math.min(16, a.optdouble(8, 1))));
                return LuaValue.NONE;
            }));
            draw.set("text", fn(a -> {
                host.drawText(PluginDefinition.clean(a.checkjstring(1)), a.checkdouble(2), a.checkdouble(3), a.checkdouble(4),
                    (int) a.optlong(5, 0xFFFFFFFFL), Math.max(0.1, Math.min(8, a.optdouble(6, 1))));
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
            xray.set("set_active", fn(a -> LuaValue.valueOf(host.xraySetActive(a.checkboolean(1)))));
            xray.set("night_vision", fn(a -> LuaValue.valueOf(host.xrayNightVision())));
            xray.set("set_night_vision", fn(a -> {
                host.xraySetNightVision(a.checkboolean(1));
                return LuaValue.NONE;
            }));
            xray.set("render_distance", fn(a -> LuaValue.valueOf(host.xrayRenderDistance())));
            xray.set("set_render_distance", fn(a -> {
                host.xraySetRenderDistance(a.checkint(1));
                return LuaValue.NONE;
            }));
            api.set("xray", xray);
        }
        return api;
    }

    private Varargs addTimer(int delay, int every, LuaValue function) {
        if (timers.size() >= MAX_TIMERS) throw new LuaError("Too many timers");
        int id = nextTimer++;
        timers.put(id, new Timer(function, Math.max(1, delay), every));
        return LuaValue.valueOf(id);
    }

    private LuaTable buildHud() {
        LuaTable hud = new LuaTable();
        hud.set("size", fn(a -> {
            int[] size = host.hudSize();
            return LuaValue.varargsOf(LuaValue.valueOf(size[0]), LuaValue.valueOf(size[1]));
        }));
        hud.set("text_width", fn(a -> LuaValue.valueOf(host.textWidth(PluginDefinition.clean(a.checkjstring(1))))));
        hud.set("text", fn(a -> {
            host.hudText(PluginDefinition.clean(a.checkjstring(1)), a.checkint(2), a.checkint(3), (int) a.optlong(4, 0xFFFFFFFFL), a.optboolean(5, true));
            return LuaValue.NONE;
        }));
        hud.set("rect", fn(a -> {
            host.hudRect(a.checkint(1), a.checkint(2), a.checkint(3), a.checkint(4), (int) a.optlong(5, 0x80000000L));
            return LuaValue.NONE;
        }));
        hud.set("item", fn(a -> LuaValue.valueOf(host.hudItem(a.checkjstring(1), a.checkint(2), a.checkint(3)))));
        hud.set("hide", fn(a -> setHudPart(a.checkjstring(1), true)));
        hud.set("show", fn(a -> setHudPart(a.checkjstring(1), false)));
        return hud;
    }

    private Varargs setHudPart(String part, boolean hidden) {
        if (!PluginHost.HUD_PARTS.contains(part)) throw new LuaError("Unknown HUD part: " + part);
        if (hidden) hiddenHud.add(part);
        else hiddenHud.remove(part);
        return LuaValue.NONE;
    }

    private LuaTable buildPlayer() {
        LuaTable player = new LuaTable();
        if (has(PluginPermission.PLAYER_READ)) {
            player.set("info", fn(a -> toLua(host.playerInfo())));
            player.set("inventory", fn(a -> toLua(host.inventory())));
        }
        if (has(PluginPermission.PLAYER_CONTROL)) {
            player.set("set_look", fn(a -> LuaValue.valueOf(host.setLook(a.checkdouble(1), a.checkdouble(2)))));
            player.set("select_slot", fn(a -> LuaValue.valueOf(host.selectSlot(a.checkint(1)))));
            player.set("set_control", fn(a -> {
                String control = a.checkjstring(1);
                if (!PluginHost.CONTROLS.contains(control)) throw new LuaError("Unknown control: " + control);
                usedControls = true;
                return LuaValue.valueOf(host.setControl(control, a.checkboolean(2)));
            }));
        }
        return player;
    }

    public boolean usedControls() { return usedControls; }

    private static JsonElement toJson(LuaValue v, int depth) {
        if (depth > MAX_JSON_DEPTH) throw new LuaError("JSON is nested too deeply");
        if (v.isnil()) return com.google.gson.JsonNull.INSTANCE;
        if (v.isboolean()) return new com.google.gson.JsonPrimitive(v.toboolean());
        if (v.isnumber()) return new com.google.gson.JsonPrimitive(v.todouble());
        if (v.isstring()) return new com.google.gson.JsonPrimitive(v.tojstring());
        if (!v.istable()) throw new LuaError("Cannot encode " + v.typename());
        LuaTable t = v.checktable();
        if (t.length() > 0) {
            JsonArray array = new JsonArray();
            for (int i = 1; i <= t.length(); i++) array.add(toJson(t.get(i), depth + 1));
            return array;
        }
        JsonObject object = new JsonObject();
        for (Varargs n = t.next(LuaValue.NIL); !n.arg1().isnil(); n = t.next(n.arg1())) {
            object.add(n.arg1().tojstring(), toJson(n.arg(2), depth + 1));
        }
        return object;
    }

    private static LuaValue fromJson(JsonElement e, int depth) {
        if (depth > MAX_JSON_DEPTH) throw new LuaError("JSON is nested too deeply");
        if (e.isJsonNull()) return LuaValue.NIL;
        if (e.isJsonArray()) {
            LuaTable t = new LuaTable();
            int i = 1;
            for (JsonElement x : e.getAsJsonArray()) t.set(i++, fromJson(x, depth + 1));
            return t;
        }
        if (e.isJsonObject()) {
            LuaTable t = new LuaTable();
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) t.set(x.getKey(), fromJson(x.getValue(), depth + 1));
            return t;
        }
        var p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return LuaValue.valueOf(p.getAsBoolean());
        if (p.isNumber()) return LuaValue.valueOf(p.getAsDouble());
        return LuaValue.valueOf(p.getAsString());
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

    /** Failures inside the game code become Lua errors, so they count against the plugin instead of crashing the game. */
    private static LuaValue fn(java.util.function.Function<Varargs, Varargs> body) {
        return LuaSandbox.function(args -> {
            try {
                return body.apply(args);
            } catch (LuaError e) {
                throw e;
            } catch (RuntimeException | LinkageError e) {
                throw new LuaError("Game call failed: " + e);
            }
        });
    }

    private static LuaValue toLua(Object value) {
        if (value instanceof Boolean b) return LuaValue.valueOf(b);
        if (value instanceof Integer i) return LuaValue.valueOf(i);
        if (value instanceof String s) return LuaValue.valueOf(s);
        if (value instanceof Number n) return LuaValue.valueOf(n.doubleValue());
        if (value instanceof List<?> list) {
            LuaTable t = new LuaTable();
            int i = 1;
            for (Object o : list) t.set(i++, toLua(o));
            return t;
        }
        if (value instanceof Map<?, ?> map) {
            LuaTable t = new LuaTable();
            map.forEach((k, v) -> t.set(String.valueOf(k), toLua(v)));
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
