package fixture;

public class BadFiles {
    Object run() throws Exception { return java.nio.file.Files.readAllBytes(java.nio.file.Path.of("x")); }
}
