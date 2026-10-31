package fixture;

public class BadRuntime {
    void run() throws Exception { Runtime.getRuntime().exec(new String[]{"calc"}); }
}
