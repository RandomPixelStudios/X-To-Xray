package fixture;

public class BadThread {
    void run() { new Thread(() -> { }).start(); }
}
