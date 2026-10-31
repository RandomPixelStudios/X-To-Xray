package fixture;

public class BadMethodHandle {
    Object run() { return java.lang.invoke.MethodHandles.lookup(); }
}
