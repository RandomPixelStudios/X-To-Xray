package com.xtoxray.plugin;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.MathLib;

/**
 * One isolated Lua state. Only pure libraries are loaded: no io, os, package, luajava, coroutine or debug,
 * and no way to compile code at runtime. Every call has an instruction budget.
 */
public final class LuaSandbox {
    /** An Error, not an Exception, so Lua's pcall cannot catch it. */
    public static final class BudgetExceeded extends Error {
        BudgetExceeded() {
            super("Plugin used too much CPU time", null, false, false);
        }
    }

    private static final int HOOK_INTERVAL = 10_000;
    private static final int MAX_HOOKS_PER_CALL = 500;
    private static final int MAX_REP_BYTES = 1024 * 1024;
    private static final Globals COMPILER = new Globals();
    private static final LuaValue SHARED_STRING_META;

    static {
        LoadState.install(COMPILER);
        LuaC.install(COMPILER);
        Globals setup = new Globals();
        setup.load(new BaseLib());
        setup.load(new org.luaj.vm2.lib.PackageLib());
        setup.load(new StringLib());
        LuaTable safe = copyStringLib(setup.get("string"));
        LuaTable meta = new LuaTable();
        meta.rawset(LuaValue.INDEX, freeze(safe));
        meta.rawset(LuaValue.METATABLE, LuaValue.FALSE);
        SHARED_STRING_META = freeze(meta);
        LuaString.s_metatable = SHARED_STRING_META;
    }

    private final Globals globals = new Globals();
    private int hooks;

    public LuaSandbox() {
        globals.load(new BaseLib());
        globals.load(new org.luaj.vm2.lib.PackageLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new MathLib());
        globals.load(new StringLib());
        LuaString.s_metatable = SHARED_STRING_META;
        globals.set("string", copyStringLib(globals.get("string")));
        globals.load(new DebugLib());
        LuaValue sethook = globals.get("debug").get("sethook");
        sethook.invoke(LuaValue.varargsOf(new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (++hooks > MAX_HOOKS_PER_CALL) throw new BudgetExceeded();
                return NONE.arg1();
            }
        }, LuaValue.EMPTYSTRING, LuaValue.valueOf(HOOK_INTERVAL)));
        for (String name : new String[]{"debug", "package", "dofile", "loadfile", "load", "loadstring", "collectgarbage", "print", "require"}) {
            globals.set(name, LuaValue.NIL);
        }
    }

    private static LuaTable copyStringLib(LuaValue original) {
        LuaTable copy = new LuaTable();
        for (Varargs n = original.next(LuaValue.NIL); !n.arg1().isnil(); n = original.next(n.arg1())) {
            copy.rawset(n.arg1(), n.arg(2));
        }
        copy.rawset("dump", LuaValue.NIL);
        LuaValue rep = original.get("rep");
        copy.rawset("rep", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue s, LuaValue n) {
                if ((long) s.checkjstring().length() * Math.max(0, n.checkint()) > MAX_REP_BYTES) {
                    throw new LuaError("string.rep result too large");
                }
                return rep.call(s, n);
            }
        });
        return copy;
    }

    private static LuaTable freeze(LuaTable table) {
        FrozenTable frozen = new FrozenTable();
        for (Varargs n = table.next(LuaValue.NIL); !n.arg1().isnil(); n = table.next(n.arg1())) {
            frozen.init(n.arg1(), n.arg(2));
        }
        frozen.sealed = true;
        return frozen;
    }

    private static final class FrozenTable extends LuaTable {
        boolean sealed;

        void init(LuaValue key, LuaValue value) {
            super.rawset(key, value);
        }

        @Override
        public void rawset(LuaValue key, LuaValue value) {
            if (sealed) throw new LuaError("table is read-only");
            super.rawset(key, value);
        }

        @Override
        public void rawset(int key, LuaValue value) {
            if (sealed) throw new LuaError("table is read-only");
            super.rawset(key, value);
        }

        @Override
        public LuaValue setmetatable(LuaValue metatable) {
            if (sealed) throw new LuaError("table is read-only");
            return super.setmetatable(metatable);
        }
    }

    public Globals globals() {
        return globals;
    }

    public void run(String source, String chunkName) {
        hooks = 0;
        COMPILER.load(source, chunkName, globals).call();
    }

    public Varargs call(LuaValue function, Varargs args) {
        hooks = 0;
        return function.invoke(args);
    }

    static VarArgFunction function(java.util.function.Function<Varargs, Varargs> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.apply(args);
            }
        };
    }
}
