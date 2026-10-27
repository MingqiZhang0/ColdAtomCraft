package org.junit.jupiter.api;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.jupiter.api.function.Executable;
/** Temporary, independent assertion shim. This is NOT JUnit 5; not for CI/production. */
public final class Assertions {
    private Assertions() { }
    private static void fail(String msg) { throw new AssertionError(msg); }
    private static boolean equal(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            if (x instanceof Double || x instanceof Float || y instanceof Double || y instanceof Float)
                return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
            return x.longValue() == y.longValue();
        }
        return Objects.equals(a,b);
    }
    public static void assertEquals(Object e, Object a) { if(!equal(e,a)) fail("expected "+e+" but got "+a); }
    public static void assertEquals(Object e, Object a, String message) { if(!equal(e,a)) fail(message+": expected "+e+" but got "+a); }
    public static void assertEquals(Object e, Object a, Supplier<String> message) { if(!equal(e,a)) fail(message.get()+": expected "+e+" but got "+a); }
    public static void assertEquals(double e, double a, double delta) { if (!(Math.abs(e-a)<=delta || Double.compare(e,a)==0)) fail("expected "+e+" +/- "+delta+" got "+a); }
    public static void assertEquals(double e, double a, double delta, String message) { if (!(Math.abs(e-a)<=delta || Double.compare(e,a)==0)) fail(message+" expected "+e+" +/- "+delta+" got "+a); }
    public static void assertNotEquals(Object e,Object a) { if(equal(e,a)) fail("unexpectedly equal: "+a); }
    public static void assertNotEquals(Object e,Object a, String msg) { if(equal(e,a)) fail(msg); }
    public static void assertTrue(boolean v) { if(!v) fail("expected true"); }
    public static void assertTrue(boolean v, String msg) { if(!v) fail(msg); }
    public static void assertTrue(boolean v, Supplier<String> msg) { if(!v) fail(msg.get()); }
    public static void assertFalse(boolean v) { if(v) fail("expected false"); }
    public static void assertFalse(boolean v, String msg) { if(v) fail(msg); }
    public static void assertFalse(boolean v, Supplier<String> msg) { if(v) fail(msg.get()); }
    public static <T extends Throwable> T assertThrows(Class<T> type, Executable e) {
        try { e.execute(); } catch (Throwable t) {
           if (type.isInstance(t)) return type.cast(t);
           throw new AssertionError("expected " + type.getName() + " but got "+t, t);
        }
        throw new AssertionError("expected exception " +type.getName());
    }
    public static <T extends Throwable> T assertThrows(Class<T> type, Executable e, String msg) { return assertThrows(type,e); }
    public static void assertDoesNotThrow(Executable e) { try { e.execute(); } catch(Throwable t) {throw new AssertionError("unexpected exception",t);} }
}
