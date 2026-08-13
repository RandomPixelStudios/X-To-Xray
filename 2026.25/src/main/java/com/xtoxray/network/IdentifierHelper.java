package com.xtoxray.network;

import java.lang.reflect.Method;

/**
 * Resolves Identifier (26.x) / ResourceLocation (1.21.x) via reflection.
 * No compile-time dependency on either class.
 */
public class IdentifierHelper {
    private static final Class<?> ID_CLASS;
    private static final Method PARSE_METHOD;

    static {
        Class<?> idCls = null;
        Method parseM = null;
        try {
            idCls = Class.forName("net.minecraft.resources.Identifier");
            parseM = idCls.getMethod("parse", String.class);
        } catch (Throwable t) {
            try {
                idCls = Class.forName("net.minecraft.resources.ResourceLocation");
                try { parseM = idCls.getMethod("tryParse", String.class); }
                catch (Throwable t2) { /* will use constructor fallback */ }
            } catch (Throwable t3) { /* neither found */ }
        }
        ID_CLASS = idCls;
        PARSE_METHOD = parseM;
    }

    /** Create an Identifier/ResourceLocation from a "namespace:path" string. */
    public static Object create(String id) {
        if (ID_CLASS == null) return null;
        try {
            if (PARSE_METHOD != null) return PARSE_METHOD.invoke(null, id);
            String[] parts = id.split(":", 2);
            String ns = parts.length > 1 ? parts[0] : "minecraft";
            String path = parts.length > 1 ? parts[1] : parts[0];
            return ID_CLASS.getConstructor(String.class, String.class).newInstance(ns, path);
        } catch (Throwable t) { return null; }
    }

    public static boolean isAvailable() { return ID_CLASS != null; }
}
