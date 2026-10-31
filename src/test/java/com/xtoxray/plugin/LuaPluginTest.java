package com.xtoxray.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaValue;

class LuaPluginTest {
    static class FakeHost implements PluginHost {
        final Map<String, String> world = new HashMap<>();
        final List<Pos> mined = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        String silk;
        int durability;
        @Override public String blockAt(int x, int y, int z) { return world.getOrDefault(x + "," + y + "," + z, "minecraft:stone"); }
        @Override public double[] playerPosition() { return new double[]{0, 0, 0}; }
        @Override public List<EntityBox> entities(double r) { return List.of(new EntityBox("hostile", 0, 0, 0, 1, 1, 1), new EntityBox("player", 0, 0, 0, 1, 1, 1)); }
        @Override public void mine(List<Pos> b, String s, int d) { mined.addAll(b); silk = s; durability = d; }
        @Override public void drawBox(double a, double b, double c, double d, double e, double f, int color, boolean top) { colors.add(color); }
        @Override public boolean xrayActive() { return false; }
        @Override public Set<String> xrayBlocks() { return Set.of(); }
        @Override public boolean xrayChange(String id, boolean add) { return true; }
        @Override public void chat(String m) { }
        @Override public void log(String id, String m) { }
    }

    private static String resource(String name) throws Exception {
        try (InputStream in = LuaPluginTest.class.getResourceAsStream("/plugins/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String script(String permissions, String body) {
        return "--[[xtoxray\n{\"format\":1,\"id\":\"test\",\"name\":\"§cTest\",\"version\":\"1\",\"permissions\":[" + permissions + "]}\n]]\n" + body;
    }

    private static LuaPlugin load(String source, PluginHost host, Path dir) throws Exception {
        LuaPlugin plugin = new LuaPlugin(PluginDefinition.parse(source), host, dir);
        assertNull(plugin.start());
        return plugin;
    }

    @Test
    void veinminerMinesTheConnectedVeinOnly(@TempDir Path dir) throws Exception {
        FakeHost host = new FakeHost();
        host.world.put("1,0,0", "minecraft:diamond_ore");
        host.world.put("2,0,0", "minecraft:diamond_ore");
        host.world.put("9,0,0", "minecraft:diamond_ore");
        LuaPlugin plugin = load(resource("veinminer.lua"), host, dir);
        plugin.fire("block_mined", LuaValue.valueOf("minecraft:diamond_ore"), LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0));
        assertEquals(Set.of(new PluginHost.Pos(1, 0, 0), new PluginHost.Pos(2, 0, 0)), Set.copyOf(host.mined));
        assertEquals("tool", host.silk);
        assertEquals(1, host.durability);
        host.mined.clear();
        plugin.fire("block_mined", LuaValue.valueOf("minecraft:stone"), LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0));
        assertTrue(host.mined.isEmpty(), "not whitelisted");
        assertFalse(plugin.isStopped());
    }

    @Test
    void veinminerHonoursSettingsAcrossRestart(@TempDir Path dir) throws Exception {
        FakeHost host = new FakeHost();
        LuaPlugin first = load(resource("veinminer.lua"), host, dir);
        first.setValue("max", 3);
        first.setValue("enabled", false);
        first.saveSettings();
        LuaPlugin second = load(resource("veinminer.lua"), host, dir);
        assertEquals(3, second.value("max"));
        assertEquals(false, second.value("enabled"));
        host.world.put("1,0,0", "minecraft:diamond_ore");
        second.fire("block_mined", LuaValue.valueOf("minecraft:diamond_ore"), LuaValue.valueOf(0), LuaValue.valueOf(0), LuaValue.valueOf(0));
        assertTrue(host.mined.isEmpty());
    }

    @Test
    void hitboxesDrawsEachEntityInItsColor(@TempDir Path dir) throws Exception {
        FakeHost host = new FakeHost();
        LuaPlugin plugin = load(resource("hitboxes.lua"), host, dir);
        plugin.setValue("color_player", "Green");
        plugin.setValue("show_hostile", false);
        plugin.fire("render");
        assertEquals(List.of(0xFF00FF00), host.colors);
        assertFalse(plugin.isStopped());
    }

    @Test
    void permissionsAreEnforced(@TempDir Path dir) throws Exception {
        FakeHost host = new FakeHost();
        String body = "xtx.on('tick', function() xtx.world.mine({{x=1,y=1,z=1}}) end)";
        LuaPlugin noPermission = load(script("", body), host, dir);
        noPermission.fire("tick");
        assertTrue(host.mined.isEmpty());
        LuaPlugin allowed = load(script("\"world.mine\"", body), host, dir);
        allowed.fire("tick");
        assertEquals(1, host.mined.size());
        assertNotNull(new LuaPlugin(PluginDefinition.parse(script("", "xtx.on('render', function() end)")), host, dir).start());
        assertNotNull(new LuaPlugin(PluginDefinition.parse(script("", "xtx.storage.write('a','b')")), host, dir).start());
    }

    @Test
    void badPluginsAreStoppedNotTheGame(@TempDir Path dir) throws Exception {
        FakeHost host = new FakeHost();
        LuaPlugin looping = load(script("", "xtx.on('tick', function() while true do end end)"), host, dir);
        looping.fire("tick");
        assertTrue(looping.isStopped());
        LuaPlugin erroring = load(script("", "xtx.on('tick', function() error('x') end)"), host, dir);
        for (int i = 0; i < 5; i++) erroring.fire("tick");
        assertTrue(erroring.isStopped());
        assertNotNull(new LuaPlugin(PluginDefinition.parse(script("", "while true do end")), host, dir).start());
        assertNotNull(new LuaPlugin(PluginDefinition.parse(script("", "syntax error here")), host, dir).start());
    }

    @Test
    void storageIsConfinedAndCannotTouchSettings(@TempDir Path dir) throws Exception {
        FakeHost host = new FakeHost();
        LuaPlugin ok = load(script("\"storage\"", "xtx.storage.write('a.txt','hi') v = xtx.storage.read('a.txt')"), host, dir);
        assertEquals("hi", Files.readString(dir.resolve("a.txt")));
        for (String evil : List.of("../evil", "..", "a/b", "/etc/passwd", "settings.host")) {
            String body = "xtx.storage.write('" + evil + "','x')";
            assertNotNull(new LuaPlugin(PluginDefinition.parse(script("\"storage\"", body)), host, dir).start(), evil);
        }
        assertFalse(Files.exists(dir.resolve("../evil")));
    }

    @Test
    void headerIsValidatedWithoutRunningTheScript() throws Exception {
        assertEquals("Test", PluginDefinition.parse(script("", "error('must not run')")).name());
        for (String bad : List.of("print('no header')", script("\"root\"", ""),
            script("", "").replace("\"id\":\"test\"", "\"id\":\"../../evil\""),
            script("", "").replace("\"format\":1", "\"format\":2"),
            script("", "").replace("\"version\":\"1\"", "\"version\":\"1\",\"x\":1"),
            script("", "x".repeat(PluginDefinition.MAX_BYTES)))) {
            assertThrows(PluginLoadException.class, () -> PluginDefinition.parse(bad));
        }
    }

    private static String index(long sequence) throws Exception {
        String plugin = resource("hitboxes.lua").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"sequence\":" + sequence + ",\"expires\":\"2099-01-01T00:00:00Z\",\"revoked\":[\"abc\"],\"plugins\":[\"" + plugin + "\"]}";
    }

    @Test
    void indexRequiresValidFreshSignature() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        byte[] json = index(5).getBytes(StandardCharsets.UTF_8);
        byte[] sig = sign(pair, json);
        RepositoryIndex parsed = RepositoryIndex.parseSigned(json, sig, pub, 5);
        assertEquals("hitboxes", parsed.plugins().get(0).id());
        assertThrows(PluginLoadException.class, () -> RepositoryIndex.parseSigned(json, sign(other, json), pub, 0));
        assertThrows(PluginLoadException.class, () -> RepositoryIndex.parseSigned(index(6).getBytes(StandardCharsets.UTF_8), sig, pub, 0));
        assertThrows(PluginLoadException.class, () -> RepositoryIndex.parseSigned(json, sig, pub, 6));
    }

    private static byte[] sign(KeyPair pair, byte[] data) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(data);
        return Base64.getEncoder().encode(signer.sign());
    }
}
