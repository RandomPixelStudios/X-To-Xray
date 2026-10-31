package fixture;

public class BadGetLoader {
    Object run() { return getClass().getClassLoader(); }
}
