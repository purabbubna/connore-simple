import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

public class Build {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("out"));
        var files = new ArrayList<String>(List.of("-encoding", "UTF-8", "--release", "17", "-d", "out"));
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            paths.filter(p -> p.toString().endsWith(".java"))
                .sorted().forEach(p -> files.add(p.toString()));
        }
        if (args.length > 0 && args[0].equals("test")) {
            try (var paths = Files.walk(Path.of("test"))) {
                paths.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> files.add(p.toString()));
            }
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Install JDK 17 or later, not a JRE.");
        int status = compiler.run(null, null, null, files.toArray(String[]::new));
        if (status != 0) System.exit(status);
        System.out.println("Connore compiled successfully.");
    }
}
