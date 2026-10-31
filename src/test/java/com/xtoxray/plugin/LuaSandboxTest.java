package com.xtoxray.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.luaj.vm2.LuaValue;

class LuaSandboxTest {
    private static LuaValue eval(String code) {
        LuaSandbox box = new LuaSandbox();
        box.run("result = (function() " + code + " end)()", "t");
        return box.globals().get("result");
    }

    @Test
    void dangerousGlobalsAreGone() {
        for (String name : List.of("os", "io", "luajava", "package", "require", "debug", "load", "loadstring", "loadfile", "dofile", "coroutine", "collectgarbage", "print")) {
            assertTrue(eval("return " + name).isnil(), name);
        }
        assertTrue(eval("return string.dump").isnil());
        assertTrue(eval("return ('x').dump").isnil());
    }

    @Test
    void normalLuaWorks() {
        assertEquals("a-3", eval("local t={} table.insert(t,'a') return string.format('%s-%d', t[1], math.floor(3.5))").tojstring());
        assertEquals("X", eval("return ('x'):upper()").tojstring());
        assertEquals(2, eval("local m=setmetatable({}, {__index=function() return 2 end}) return m.z").toint());
    }

    @Test
    @Timeout(20)
    void infiniteLoopsAreStopped() {
        assertThrows(LuaSandbox.BudgetExceeded.class, () -> eval("while true do end"));
        assertThrows(LuaSandbox.BudgetExceeded.class, () -> eval("while true do pcall(function() while true do end end) end"));
        assertThrows(LuaSandbox.BudgetExceeded.class, () -> eval("local function f() return f() end while true do pcall(f) end"));
    }

    @Test
    void pluginsCannotAffectEachOtherThroughStrings() {
        assertThrows(RuntimeException.class, () -> eval("getmetatable('').__index = {upper=function() return 'evil' end}"));
        assertThrows(RuntimeException.class, () -> eval("rawset(getmetatable('').__index, 'upper', 1)"));
        eval("string.upper = function() return 'evil' end");
        assertEquals("X", eval("return ('x'):upper()").tojstring());
        assertEquals("X", eval("return string.upper('x')").tojstring());
        assertThrows(RuntimeException.class, () -> eval("setmetatable('', {})"));
    }

    @Test
    void hugeStringsAreRefused() {
        assertThrows(RuntimeException.class, () -> eval("return string.rep('x', 2000000000)"));
    }

    @Test
    void sharedStateDoesNotLeak() {
        LuaSandbox a = new LuaSandbox();
        LuaSandbox b = new LuaSandbox();
        a.run("secret = 1", "a");
        b.run("seen = secret", "b");
        assertTrue(b.globals().get("seen").isnil());
    }

    @Test
    void bytecodeCannotBeLoaded() {
        assertThrows(RuntimeException.class, () -> new LuaSandbox().run("\u001bLuaR", "b"));
    }
}
