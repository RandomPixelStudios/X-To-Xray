package fixture;

public class BadReflection {
    Object run() throws Exception { return Class.forName("java.lang.Runtime"); }
}
