package com.xtoxray.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.PluginDescriptor;
import com.xtoxray.api.plugin.PluginLogger;
import com.xtoxray.api.plugin.PluginStorage;
import com.xtoxray.api.plugin.XrayAccess;
import com.xtoxray.api.plugin.event.EventBus;
import com.xtoxray.api.plugin.event.XrayToggleEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PluginSecurityTest {
    private static final String YAML = "id: good\nname: Good\nversion: 1\nauthor: T\nmainClass: fixture.GoodPlugin\napiVersion: "
        + Plugin.API_VERSION + "\npermissions: storage\n";

    private static byte[] bytesOf(String internal) throws IOException {
        try (InputStream in = PluginSecurityTest.class.getClassLoader().getResourceAsStream(internal + ".class")) {
            return in.readAllBytes();
        }
    }

    private static byte[] jar(String yaml, Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            if (yaml != null) {
                zip.putNextEntry(new ZipEntry("plugin.yml"));
                zip.write(yaml.getBytes(StandardCharsets.UTF_8));
            }
            for (var e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
            }
        }
        return out.toByteArray();
    }

    private static Map<String, byte[]> goodClasses() throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        for (String c : List.of("fixture/GoodPlugin", "fixture/GoodPlugin$Pair", "fixture/GoodPlugin$Mode")) {
            map.put(c + ".class", bytesOf(c));
        }
        return map;
    }

    @Test
    void realPluginWithLambdasAndRecordsPassesAndReceivesEvents() throws Exception {
        PluginJar jar = PluginJar.read(jar(YAML, goodClasses()));
        PluginClassLoader loader = new PluginClassLoader("good", jar.classes(), getClass().getClassLoader());
        Class<?> main = Class.forName("fixture.GoodPlugin", true, loader);
        assertNotSame(fixture.GoodPlugin.class, main, "must be defined from verified bytes, not by the host");
        Plugin plugin = (Plugin) main.getDeclaredConstructor().newInstance();
        List<Object> errors = new ArrayList<>();
        PluginEventBus bus = new PluginEventBus((o, e) -> errors.add(e));
        plugin.onEnable(context(jar.descriptor(), bus.scopedTo(plugin)));
        bus.post(new XrayToggleEvent(true));
        assertEquals(List.of(), errors);
        assertEquals(1, main.getField("received").getInt(null));
        bus.removeOwner(plugin);
        bus.post(new XrayToggleEvent(false));
        assertEquals(1, main.getField("received").getInt(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BadRuntime", "BadReflection", "BadFiles", "BadNetwork", "BadThread", "BadSystem",
        "BadMethodHandle", "BadGetLoader", "BadMinecraft", "BadInternal"})
    void dangerousCodeIsRejected(String name) throws Exception {
        Map<String, byte[]> classes = goodClasses();
        classes.put("fixture/" + name + ".class", bytesOf("fixture/" + name));
        PluginLoadException e = assertThrows(PluginLoadException.class, () -> PluginJar.read(jar(YAML, classes)));
        assertTrue(e.getMessage().contains("security check"), e.getMessage());
    }

    @Test
    void classLoaderHidesHostClasses() throws Exception {
        PluginClassLoader loader = new PluginClassLoader("x", Map.of(), getClass().getClassLoader());
        for (String hidden : List.of("java.lang.Runtime", "java.lang.Thread", "java.net.Socket", "com.xtoxray.plugin.PluginManager")) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(hidden), hidden);
        }
        assertNotNull(loader.loadClass("com.xtoxray.api.plugin.Plugin"));
    }

    @Test
    void reservedPackageNestedJarAndBadDescriptorAreRejected() throws Exception {
        Map<String, byte[]> spoof = goodClasses();
        spoof.put("com/xtoxray/api/plugin/Evil.class", new byte[]{1});
        assertThrows(PluginLoadException.class, () -> PluginJar.read(jar(YAML, spoof)));
        Map<String, byte[]> nested = goodClasses();
        nested.put("lib/evil.jar", new byte[]{1});
        assertThrows(PluginLoadException.class, () -> PluginJar.read(jar(YAML, nested)));
        assertThrows(PluginLoadException.class, () -> PluginJar.read(jar(null, goodClasses())));
        for (String bad : List.of(YAML.replace("id: good", "id: ../../evil"), YAML.replace("id: good", "id: veinminer"),
            YAML.replace("permissions: storage", "permissions: root"), YAML.replace("apiVersion: " + Plugin.API_VERSION, "apiVersion: 1"))) {
            assertThrows(PluginLoadException.class, () -> PluginJar.read(jar(bad, goodClasses())), bad);
        }
    }

    @Test
    void renamedClassIsRejected() throws Exception {
        Map<String, byte[]> classes = goodClasses();
        classes.put("fixture/Hidden.class", bytesOf("fixture/BadRuntime"));
        assertThrows(PluginLoadException.class, () -> PluginJar.read(jar(YAML, classes)));
    }

    @Test
    void storageCannotEscapeItsFolder(@TempDir Path dir) throws Exception {
        PluginStorageImpl storage = new PluginStorageImpl(dir.resolve("data"));
        storage.write("a.json", "x");
        assertEquals("x", storage.read("a.json").orElseThrow());
        for (String evil : List.of("../evil", "..", "a/b", "a\\b", "", ".hidden", "/etc/passwd")) {
            assertThrows(IllegalArgumentException.class, () -> storage.write(evil, "x"), evil);
        }
        assertThrows(IOException.class, () -> storage.write("big", "x".repeat(1024 * 1024 + 1)));
        assertFalse(Files.exists(dir.resolve("evil")));
    }

    private static final String INDEX = "{\"sequence\":5,\"expires\":\"2099-01-01T00:00:00Z\",\"revoked\":[\"abc\"],\"plugins\":[{\"id\":\"good\",\"name\":\"§cGood\",\"description\":\"d\",\"author\":\"a\",\"version\":\"1\",\"apiVersion\":2,\"url\":\"https://github.com/x\",\"sha256\":\"00\",\"permissions\":[\"storage\"]}]}";

    @Test
    void indexRequiresValidFreshSignature() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        byte[] json = INDEX.getBytes(StandardCharsets.UTF_8);
        byte[] sig = sign(pair, json);

        RepositoryIndex index = RepositoryIndex.parseSigned(json, sig, pub, 5);
        assertEquals("Good", index.plugins().get(0).name());
        assertTrue(index.revoked().contains("abc"));

        assertThrows(PluginLoadException.class, () -> RepositoryIndex.parseSigned(json, sign(other, json), pub, 0), "wrong key");
        byte[] tampered = INDEX.replace("00", "11").getBytes(StandardCharsets.UTF_8);
        assertThrows(PluginLoadException.class, () -> RepositoryIndex.parseSigned(tampered, sig, pub, 0), "tampered");
        assertThrows(PluginLoadException.class, () -> RepositoryIndex.parseSigned(json, sig, pub, 6), "rollback");
    }

    private static byte[] sign(KeyPair pair, byte[] data) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(data);
        return Base64.getEncoder().encode(signer.sign());
    }

    private static PluginContext context(PluginDescriptor descriptor, EventBus bus) {
        return new PluginContext() {
            @Override public PluginDescriptor getDescriptor() { return descriptor; }
            @Override public PluginLogger getLogger() {
                return new PluginLogger() {
                    @Override public void info(String m) { }
                    @Override public void warn(String m) { }
                    @Override public void error(String m) { }
                };
            }
            @Override public EventBus getEventBus() { return bus; }
            @Override public PluginStorage getStorage() { throw new SecurityException(); }
            @Override public XrayAccess getXray() { throw new SecurityException(); }
        };
    }
}
