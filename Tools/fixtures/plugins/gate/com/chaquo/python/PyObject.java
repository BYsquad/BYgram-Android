package com.chaquo.python;
/** Gate-only linkage stand-in. The gate must never enter Python. */
public final class PyObject {
    public PyObject call(Object... args) { throw new AssertionError("Python initialized by gate"); }
    public <T> T toJava(Class<T> type) { throw new AssertionError("Python initialized by gate"); }
}
