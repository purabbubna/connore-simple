import com.connore.tools.InterestCatalog;
import java.nio.file.*;
import java.util.*;

public class CatalogTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
    private static void rejects(Path file, String data, String name) throws Exception {
        Files.writeString(file, data);
        try { InterestCatalog.read(file); throw new AssertionError(name); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        var catalog = InterestCatalog.read(Path.of("sql/interest-catalog.tsv"));
        check(catalog.size() == 48, "Compact 48-interest catalog is present");
        check(catalog.stream().map(InterestCatalog.Entry::category).distinct().count() == 8, "Catalog spans eight categories");
        check(catalog.stream().anyMatch(e -> e.interest().equals("Photography")), "Photography remains discoverable");
        var dir = Path.of("out", "test-data", "catalog");Files.createDirectories(dir);
        var file = Files.createTempFile(dir, "catalog-", ".tsv");
        String header = "category\tinterest\tdescription\n";
        try {
            Files.writeString(file, "\ufeff" + header + "Arts\t Photography \t Meet photographers.\n\n");
            check(InterestCatalog.read(file).get(0).interest().equals("Photography"), "BOM and whitespace handled");
            rejects(file, header + "Arts\tCafe\tA\nArts\tCAFÉ\tB\n", "Rejects collation-equivalent duplicate names");
            rejects(file, header + "Arts\t\tA\n", "Rejects blank interest");
            rejects(file, header + "Arts\tA\t" + "x".repeat(301) + "\n", "Rejects overlong descriptions");
            rejects(file, header + "Arts\tA\tB\tC\n", "Rejects additional TSV fields");
            rejects(file, header + "Arts\tA\tB\narts\tC\tD\n", "Rejects inconsistent category case");
            rejects(file, "wrong\n", "Rejects malformed header");
            rejects(file, header, "Rejects empty catalog");
            rejects(file, header + "Arts\tA\tInvalid\u0000text\n", "Rejects control characters");
        } finally { Files.deleteIfExists(file); }
        System.out.println("Catalog checks passed: " + checks);
    }
}
