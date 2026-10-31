package com.xtoxray.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Allowlist check of plugin bytecode, done before any plugin class is defined.
 * Plugins may only reference their own classes, the XtoXray API and harmless JDK classes:
 * no files, network, processes, threads, reflection, native code, Minecraft or Fabric.
 */
public final class PluginVerifier {
    private static final int ASM = Opcodes.ASM9;

    private static final Set<String> JAVA_LANG = Set.of(
        "Object", "String", "StringBuilder", "StringBuffer", "CharSequence", "Comparable", "Iterable", "Runnable",
        "AutoCloseable", "Cloneable", "Record", "Enum", "Number", "Integer", "Long", "Short", "Byte", "Character",
        "Boolean", "Float", "Double", "Void", "Math", "StrictMath", "Class", "System",
        "Throwable", "Exception", "RuntimeException", "Error", "AssertionError", "IllegalArgumentException",
        "IllegalStateException", "NullPointerException", "UnsupportedOperationException", "ArithmeticException",
        "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException", "NumberFormatException", "ClassCastException",
        "SecurityException", "MatchException", "Override", "FunctionalInterface", "Deprecated", "SuppressWarnings",
        "SafeVarargs");

    private static final Set<String> JAVA_UTIL_DENIED = Set.of("ServiceLoader", "ResourceBundle", "Timer", "TimerTask",
        "Scanner", "Formatter", "Properties", "PropertyResourceBundle", "ListResourceBundle");

    private static final Set<String> OTHER_CLASSES = Set.of("java/io/Serializable", "java/io/IOException",
        "java/io/UncheckedIOException", "java/util/concurrent/ConcurrentHashMap",
        "java/util/concurrent/CopyOnWriteArrayList", "java/util/concurrent/ThreadLocalRandom");

    private static final List<String> ALLOWED_PREFIXES = List.of("java/util/function/", "java/util/regex/",
        "java/util/stream/", "java/util/concurrent/atomic/", "java/time/", "java/math/", "java/text/",
        "com/xtoxray/api/plugin/");

    /** Only these members of classes that are otherwise dangerous. */
    private static final Map<String, Set<String>> RESTRICTED_MEMBERS = Map.of(
        "java/lang/Class", Set.of("getName", "getSimpleName", "desiredAssertionStatus", "hashCode", "equals", "toString"),
        "java/lang/System", Set.of("currentTimeMillis", "nanoTime", "arraycopy", "identityHashCode", "lineSeparator"));

    private static final Set<String> ALLOWED_BOOTSTRAPS = Set.of("java/lang/invoke/LambdaMetafactory",
        "java/lang/invoke/StringConcatFactory", "java/lang/runtime/ObjectMethods", "java/lang/runtime/SwitchBootstraps");

    private PluginVerifier() {
    }

    /** May a plugin class resolve this class name (dotted) from the host? Also used by the class loader. */
    public static boolean isHostClassAllowed(String dottedName) {
        String internal = dottedName.replace('.', '/');
        return isAllowedExternal(internal) || ALLOWED_BOOTSTRAPS.contains(internal)
            || internal.equals("java/lang/invoke/MethodHandles$Lookup") || internal.equals("java/lang/invoke/MethodHandles");
    }

    private static boolean isAllowedExternal(String internal) {
        int inner = internal.indexOf('$');
        String outer = inner > 0 ? internal.substring(0, inner) : internal;
        if (OTHER_CLASSES.contains(outer)) return true;
        for (String prefix : ALLOWED_PREFIXES) {
            if (outer.startsWith(prefix)) return true;
        }
        if (outer.startsWith("java/lang/")) {
            return JAVA_LANG.contains(outer.substring("java/lang/".length()));
        }
        if (outer.startsWith("java/util/")) {
            String simple = outer.substring("java/util/".length());
            return !simple.contains("/") && !JAVA_UTIL_DENIED.contains(simple);
        }
        return false;
    }

    /** @return human-readable violations; empty means the classes passed */
    public static List<String> verify(Map<String, byte[]> classes) {
        List<String> violations = new ArrayList<>();
        Set<String> own = classes.keySet();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            try {
                ClassReader reader = new ClassReader(entry.getValue());
                if (!reader.getClassName().equals(entry.getKey())) {
                    violations.add(entry.getKey() + ": class name does not match its path");
                    continue;
                }
                reader.accept(new Checker(own, violations), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (RuntimeException e) {
                violations.add(entry.getKey() + ": unreadable class file");
            }
        }
        return violations;
    }

    private static final class Checker extends ClassVisitor {
        private final Set<String> own;
        private final List<String> violations;
        private String current;

        Checker(Set<String> own, List<String> violations) {
            super(ASM);
            this.own = own;
            this.violations = violations;
        }

        private void fail(String what) {
            violations.add(current + ": " + what);
        }

        private void checkClass(String internal) {
            if (internal == null) return;
            if (internal.startsWith("[")) {
                checkType(Type.getType(internal));
            } else if (!own.contains(internal) && !isAllowedExternal(internal)) {
                fail("uses forbidden class " + internal.replace('/', '.'));
            }
        }

        private void checkType(Type type) {
            switch (type.getSort()) {
                case Type.ARRAY -> checkType(type.getElementType());
                case Type.OBJECT -> checkClass(type.getInternalName());
                case Type.METHOD -> {
                    checkType(type.getReturnType());
                    for (Type arg : type.getArgumentTypes()) checkType(arg);
                }
                default -> { }
            }
        }

        private void checkMember(String owner, String name, String descriptor) {
            checkClass(owner);
            checkType(Type.getType(descriptor));
            Set<String> allowed = RESTRICTED_MEMBERS.get(owner);
            if (allowed != null && !allowed.contains(name)) {
                fail("uses forbidden member " + owner.replace('/', '.') + "." + name);
            }
        }

        private void checkConstant(Object value) {
            if (value instanceof Type type) {
                checkType(type);
            } else if (value instanceof Handle handle) {
                checkMember(handle.getOwner(), handle.getName(), handle.getDesc());
            } else if (value instanceof ConstantDynamic) {
                fail("uses a dynamic constant");
            }
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            current = name;
            checkClass(superName);
            if (interfaces != null) {
                for (String i : interfaces) checkClass(i);
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            checkType(Type.getType(descriptor));
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if ((access & Opcodes.ACC_NATIVE) != 0) fail("declares native method " + name);
            checkType(Type.getType(descriptor));
            return new MethodVisitor(ASM) {
                @Override
                public void visitTypeInsn(int opcode, String type) {
                    checkClass(type);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                    checkMember(owner, fieldName, fieldDescriptor);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                    checkMember(owner, methodName, methodDescriptor);
                }

                @Override
                public void visitInvokeDynamicInsn(String indyName, String indyDescriptor, Handle bootstrap, Object... args) {
                    if (!ALLOWED_BOOTSTRAPS.contains(bootstrap.getOwner())) {
                        fail("uses forbidden bootstrap " + bootstrap.getOwner().replace('/', '.'));
                    }
                    checkType(Type.getType(indyDescriptor));
                    for (Object arg : args) checkConstant(arg);
                }

                @Override
                public void visitLdcInsn(Object value) {
                    checkConstant(value);
                }

                @Override
                public void visitMultiANewArrayInsn(String arrayDescriptor, int dimensions) {
                    checkType(Type.getType(arrayDescriptor));
                }

                @Override
                public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    checkClass(type);
                }
            };
        }
    }
}
