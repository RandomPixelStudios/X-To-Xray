package fixture;

public class BadNetwork {
    Object run() throws Exception { return new java.net.Socket("example.com", 80); }
}
