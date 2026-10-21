package com.connore.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Process environment overrides .env, then legacy properties, then local defaults. */
public final class Config {
    private static final Map<String, String> KEYS = Map.of(
        "DB_URL", "db.url", "DB_USER", "db.user", "DB_PASSWORD", "db.password",
        "COLLEGE_DOMAIN", "college.domain", "SERVER_HOST", "server.host",
        "SERVER_PORT", "server.port", "SESSION_MINUTES", "session.minutes");

    private Config() {}

    public static Properties load() throws IOException {
        return load(Path.of("."), System.getenv());
    }

    public static Properties load(Path directory, Map<String, String> environment) throws IOException {
        var config = new Properties();
        config.setProperty("db.url", "jdbc:mysql://localhost:3306/connore?serverTimezone=UTC&characterEncoding=UTF-8");
        config.setProperty("db.user", "root");
        config.setProperty("db.password", "");
        config.setProperty("college.domain", "sitpune.edu.in");
        config.setProperty("server.host", "127.0.0.1");
        config.setProperty("server.port", "8081");
        config.setProperty("session.minutes", "30");

        Path legacy = directory.resolve("config.properties");
        if (Files.isRegularFile(legacy)) {
            try (var input = Files.newInputStream(legacy)) { config.load(input); }
        }
        Map<String, String> dotenv = readEnv(directory.resolve(".env"));
        for (var key : KEYS.entrySet()) {
            if (dotenv.containsKey(key.getKey())) config.setProperty(key.getValue(), dotenv.get(key.getKey()));
            if (environment.containsKey(key.getKey())) config.setProperty(key.getValue(), environment.get(key.getKey()));
        }
        for (String key : new String[] {"db.url", "db.user", "college.domain", "server.host"}) {
            if (config.getProperty(key).isBlank()) throw new IllegalArgumentException("Configuration value is empty: " + key);
        }
        positiveNumber(config, "server.port", 65535);
        positiveNumber(config, "session.minutes", Long.MAX_VALUE / 60);
        return config;
    }

    private static void positiveNumber(Properties config, String key, long max) {
        try {
            long number = Long.parseLong(config.getProperty(key));
            if (number < 1 || number > max) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid positive number for " + key);
        }
    }

    private static Map<String, String> readEnv(Path path) throws IOException {
        var values = new LinkedHashMap<String, String>();
        if (!Files.isRegularFile(path)) return values;
        int number = 0;
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            number++;
            String line = raw.strip();
            if (number == 1 && line.startsWith("\ufeff")) line = line.substring(1).stripLeading();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring(7).stripLeading();
            int equals = line.indexOf('=');
            if (equals < 1 || !line.substring(0, equals).strip().matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw invalidLine(number);
            String key = line.substring(0, equals).strip();
            values.put(key, value(line.substring(equals + 1).strip(), number));
        }
        return values;
    }

    private static String value(String text, int line) {
        if (text.isEmpty() || text.startsWith("#")) return "";
        char quote = text.charAt(0);
        if (quote != '\'' && quote != '"') {
            int comment = text.indexOf(" #");
            return (comment < 0 ? text : text.substring(0, comment)).stripTrailing();
        }
        var result = new StringBuilder();
        for (int i = 1; i < text.length(); i++) {
            char next = text.charAt(i);
            if (next == quote) {
                String rest = text.substring(i + 1).strip();
                if (!rest.isEmpty() && !rest.startsWith("#")) throw invalidLine(line);
                return result.toString();
            }
            if (quote == '"' && next == '\\' && i + 1 < text.length()) {
                char escaped = text.charAt(++i);
                switch (escaped) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case '\\', '"' -> result.append(escaped);
                    default -> result.append('\\').append(escaped);
                }
            } else result.append(next);
        }
        throw invalidLine(line);
    }

    private static IllegalArgumentException invalidLine(int line) {
        // Never include a line's contents: it may contain a database password.
        return new IllegalArgumentException("Invalid .env syntax on line " + line);
    }
}
