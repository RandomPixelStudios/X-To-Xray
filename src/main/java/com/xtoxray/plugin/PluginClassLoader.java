package com.xtoxray.plugin;

import java.util.Map;

/** Defines classes only from verified in-memory bytes and hides everything else on the host. */
public final class PluginClassLoader extends ClassLoader {
    private final Map<String, byte[]> classes;

    public PluginClassLoader(String id, Map<String, byte[]> classes, ClassLoader host) {
        super("xtoxray-plugin-" + id, host);
        this.classes = classes;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                byte[] bytes = classes.get(name.replace('.', '/'));
                if (bytes != null) {
                    loaded = defineClass(name, bytes, 0, bytes.length);
                } else if (PluginVerifier.isHostClassAllowed(name)) {
                    loaded = getParent().loadClass(name);
                } else {
                    throw new ClassNotFoundException(name);
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    @Override
    public java.net.URL getResource(String name) {
        return null;
    }

    @Override
    public java.util.Enumeration<java.net.URL> getResources(String name) {
        return java.util.Collections.emptyEnumeration();
    }
}
