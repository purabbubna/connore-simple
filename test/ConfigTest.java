import com.connore.util.Config;
import java.nio.file.*;
import java.util.*;

public final class ConfigTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Path parent = Path.of("out", "test-data", "config").toAbsolutePath().normalize();
        Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "isolated-");
        Path dotenv = directory.resolve(".env"), legacy = directory.resolve("config.properties");
        try {
            var defaults = Config.load(directory, Map.of());
            check(defaults.getProperty("server.host").equals("127.0.0.1"), "Localhost is the default binding");
            check(defaults.getProperty("server.port").equals("8081"), "Simple version uses its own default server port");
            Files.writeString(legacy, "db.user=legacy-user\ndb.password=legacy-secret\nserver.port=8090\n");
            check(Config.load(directory, Map.of()).getProperty("db.user").equals("legacy-user"), "Legacy configuration still works");
            Files.writeString(dotenv, "\ufeff# dotenv\nexport DB_USER=dotenv-user\nDB_PASSWORD=\"a # b\\\\c\\\"d\" # comment\nSERVER_PORT=8091\nSESSION_MINUTES=45\n");
            var config = Config.load(directory, Map.of("DB_USER", "environment-user", "DB_PASSWORD", "", "SERVER_PORT", "8092"));
            check(config.getProperty("db.user").equals("environment-user"), "Process environment wins");
            check(config.getProperty("db.password").isEmpty(), "Explicit empty password overrides fallback");
            check(config.getProperty("server.port").equals("8092"), "Port precedence");
            check(config.getProperty("session.minutes").equals("45"), "Dotenv overrides defaults");
            check(Config.load(directory, Map.of()).getProperty("db.password").equals("a # b\\c\"d"), "Quoted values preserve spaces, hashes and escaped characters");
            Files.writeString(dotenv, "DB_PASSWORD='literal # value'\nDB_USER=local-user # comment\n");
            check(Config.load(directory, Map.of()).getProperty("db.password").equals("literal # value"), "Single-quoted values stay literal");
            check(Config.load(directory, Map.of()).getProperty("db.user").equals("local-user"), "Unquoted comments are excluded");
            Files.writeString(dotenv, "DB_PASSWORD= # no password on this local server\n");
            check(Config.load(directory, Map.of()).getProperty("db.password").isEmpty(), "Comment after an empty unquoted value stays empty");
            Files.writeString(dotenv, "DB_PASSWORD=\"#literal-password\"\n");
            check(Config.load(directory, Map.of()).getProperty("db.password").equals("#literal-password"), "Quoted leading hash remains part of the value");
            for (String invalid : List.of("SERVER_PORT=0", "SERVER_PORT=65536", "SERVER_PORT=not-a-port", "SESSION_MINUTES=0", "DB_USER=", "DB_PASSWORD=\"unfinished")) {
                Files.writeString(dotenv, invalid + "\n");
                try {Config.load(directory, Map.of());throw new AssertionError("Expected invalid configuration");}
                catch (IllegalArgumentException expected) {check(!expected.getMessage().contains("unfinished"), "Errors omit secret values");}
            }
        } finally {
            Files.deleteIfExists(dotenv);Files.deleteIfExists(legacy);Files.deleteIfExists(directory);
        }
        System.out.println("Configuration checks passed: " + checks);
    }
}
