package com.connore.tools;

import com.connore.util.Config;
import com.connore.util.Db;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Manages the active catalog while retaining historical interest IDs and student records. */
public final class InterestCatalog {
    public record Entry(String category, String interest, String description) {}

    public record Changes(int categories, int interests, int communities) {}

    public record SyncChanges(Changes added, int reassigned, int archived, int reactivated) {}

    public record Status(
            int categories,
            int interests,
            int communities,
            int missingInterests,
            int missingCommunities,
            int unpublishedInterests,
            int activeInterests,
            int activeCategories,
            int activeCommunities) {}

    private InterestCatalog() {}

    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    public static List<Entry> read(Path path) throws IOException {
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()
                || !lines.get(0).replace("\ufeff", "").equals("category\tinterest\tdescription")) {
            throw new IllegalArgumentException(
                    "Catalog must start with category, interest and description tab-separated columns.");
        }
        var entries = new ArrayList<Entry>();
        var names = new HashSet<String>();
        var categories = new HashMap<String, String>();
        for (int n = 1; n < lines.size(); n++) {
            if (lines.get(n).isBlank()) {
                continue;
            }
            String[] fields = lines.get(n).split("\t", -1);
            if (fields.length != 3) {
                throw new IllegalArgumentException("Expected three fields on catalog line " + (n + 1));
            }
            for (int i = 0; i < fields.length; i++) {
                fields[i] = fields[i].strip();
                if (fields[i].isEmpty() || fields[i].codePoints().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException(
                            "Empty or invalid field on catalog line " + (n + 1));
                }
            }
            if (fields[0].length() > 50 || fields[1].length() > 80 || fields[2].length() > 300) {
                throw new IllegalArgumentException(
                        "Field exceeds database length on catalog line " + (n + 1));
            }
            if (!names.add(normalized(fields[1]))) {
                throw new IllegalArgumentException("Duplicate interest on catalog line " + (n + 1));
            }
            String previous = categories.putIfAbsent(normalized(fields[0]), fields[0]);
            if (previous != null && !previous.equals(fields[0])) {
                throw new IllegalArgumentException(
                        "Inconsistent category spelling on catalog line " + (n + 1));
            }
            entries.add(new Entry(fields[0], fields[1], fields[2]));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Catalog contains no interests.");
        }
        return List.copyOf(entries);
    }

    public static Changes addMissing(Connection connection, List<Entry> entries) throws SQLException {
        int categories = 0;
        int interests = 0;
        int communities = 0;
        var categoryIds = new HashMap<String, Integer>();
        for (Entry entry : entries) {
            var rows = Db.query(connection,
                    "SELECT interest_id,is_approved FROM interest WHERE name=?",
                    entry.interest());
            int interestId;
            boolean approved;
            if (rows.isEmpty()) {
                Integer categoryId = categoryIds.get(entry.category());
                if (categoryId == null) {
                    var category = Db.query(connection,
                            "SELECT category_id FROM category WHERE name=?", entry.category());
                    if (category.isEmpty()) {
                        categoryId = Db.insert(connection, "INSERT INTO category(name) VALUES(?)", entry.category());
                        categories++;
                    } else {
                        categoryId = Db.integer(category.get(0).get("category_id"));
                    }
                    categoryIds.put(entry.category(), categoryId);
                }
                interestId = Db.insert(connection,
                        "INSERT INTO interest(name,category_id,is_approved) VALUES(?,?,TRUE)",
                        entry.interest(), categoryId);
                interests++;
                approved = true;
            } else {
                interestId = Db.integer(rows.get(0).get("interest_id"));
                approved = Db.bool(rows.get(0).get("is_approved"));
            }
            // Existing approval decisions, category assignments and community descriptions are preserved.
            if (approved && Db.query(connection,
                    "SELECT community_id FROM community WHERE interest_id=?", interestId).isEmpty()) {
                Db.insert(connection,
                        "INSERT INTO community(interest_id,description) VALUES(?,?)",
                        interestId, entry.description());
                communities++;
            }
        }
        return new Changes(categories, interests, communities);
    }

    public static Status status(Connection connection, List<Entry> entries) throws SQLException {
        int missing = 0;
        int missingCommunities = 0;
        int unpublished = 0;
        for (Entry entry : entries) {
            var rows = Db.query(connection, """
                    SELECT i.is_approved,c.community_id
                    FROM interest i
                    LEFT JOIN community c ON c.interest_id=i.interest_id
                    WHERE i.name=?
                    """, entry.interest());
            if (rows.isEmpty()) {
                missing++;
            } else if (!Db.bool(rows.get(0).get("is_approved"))) {
                unpublished++;
            } else if (rows.get(0).get("community_id") == null) {
                missingCommunities++;
            }
        }
        int active = Db.integer(Db.query(connection,
                "SELECT COUNT(*) AS n FROM interest WHERE is_approved=TRUE").get(0).get("n"));
        int activeCategories = Db.integer(Db.query(connection, """
                SELECT COUNT(DISTINCT category_id) AS n
                FROM interest
                WHERE is_approved=TRUE
                """).get(0).get("n"));
        int activeCommunities = Db.integer(Db.query(connection, """
                SELECT COUNT(*) AS n
                FROM community c
                JOIN interest i ON i.interest_id=c.interest_id
                WHERE i.is_approved=TRUE
                """).get(0).get("n"));
        return new Status(
                count(connection, "category"),
                count(connection, "interest"),
                count(connection, "community"),
                missing, missingCommunities, unpublished,
                active, activeCategories, activeCommunities);
    }

    public static SyncChanges synchronize(Connection connection, List<Entry> entries) throws SQLException {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Cannot synchronize an empty catalog.");
        }
        Changes added = addMissing(connection, entries);
        int categories = added.categories();
        int communities = added.communities();
        int reassigned = 0;
        int reactivated = 0;
        var categoryIds = new HashMap<String, Integer>();
        var activeIds = new ArrayList<Integer>();
        for (Entry entry : entries) {
            Integer categoryId = categoryIds.get(entry.category());
            if (categoryId == null) {
                var found = Db.query(connection,
                        "SELECT category_id FROM category WHERE name=?", entry.category());
                if (found.isEmpty()) {
                    categoryId = Db.insert(connection,
                            "INSERT INTO category(name) VALUES(?)", entry.category());
                    categories++;
                } else {
                    categoryId = Db.integer(found.get(0).get("category_id"));
                }
                categoryIds.put(entry.category(), categoryId);
            }
            var interest = Db.query(connection,
                    "SELECT interest_id,category_id,is_approved FROM interest WHERE name=?",
                    entry.interest()).get(0);
            int id = Db.integer(interest.get("interest_id"));
            activeIds.add(id);
            if (Db.integer(interest.get("category_id")) != categoryId) {
                Db.exec(connection,
                        "UPDATE interest SET category_id=? WHERE interest_id=?", categoryId, id);
                reassigned++;
            }
            if (!Db.bool(interest.get("is_approved"))) {
                Db.exec(connection, "UPDATE interest SET is_approved=TRUE WHERE interest_id=?", id);
                reactivated++;
            }
            if (Db.query(connection,
                    "SELECT community_id FROM community WHERE interest_id=?", id).isEmpty()) {
                Db.insert(connection,
                        "INSERT INTO community(interest_id,description) VALUES(?,?)",
                        id, entry.description());
                communities++;
            }
        }
        String placeholders = String.join(",", Collections.nCopies(activeIds.size(), "?"));
        int archived = Db.exec(connection,
                "UPDATE interest SET is_approved=FALSE "
                        + "WHERE is_approved=TRUE AND interest_id NOT IN (" + placeholders + ")",
                activeIds.toArray());
        return new SyncChanges(
                new Changes(categories, added.interests(), communities),
                reassigned, archived, reactivated);
    }

    private static int count(Connection connection, String table) throws SQLException {
        return Db.integer(Db.query(connection, "SELECT COUNT(*) AS n FROM " + table).get(0).get("n"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2
                || !Set.of("--check", "--apply", "--sync", "--verify").contains(args[0])) {
            System.err.println("Usage: java -cp \"out;lib/*\" com.connore.tools.InterestCatalog "
                    + "--check|--apply|--sync|--verify [sql/interest-catalog.tsv]");
            System.exit(2);
        }
        List<Entry> entries = read(Path.of(args.length == 2 ? args[1] : "sql/interest-catalog.tsv"));
        System.out.println("Catalog: " + entries.size() + " interests in "
                + entries.stream().map(Entry::category).distinct().count() + " categories.");
        var config = Config.load();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Db.configure(config);
        try {
            if (args[0].equals("--sync")) {
                SyncChanges changes = Db.tx(connection -> {
                    SyncChanges result = synchronize(connection, entries);
                    Status status = status(connection, entries);
                    if (status.activeInterests() != entries.size()
                            || status.missingInterests() != 0
                            || status.missingCommunities() != 0
                            || status.unpublishedInterests() != 0) {
                        throw new SQLException("Catalog synchronization failed; transaction rolled back.");
                    }
                    return result;
                });
                System.out.println("Catalog synchronized: " + changes.added().interests()
                        + " added, " + changes.archived() + " archived, "
                        + changes.reactivated() + " reactivated, "
                        + changes.reassigned() + " recategorized.");
                System.out.println("Historical IDs, selections, memberships and connections retained.");
            }
            if (args[0].equals("--apply")) {
                Changes changes = Db.tx(connection -> {
                    Changes added = addMissing(connection, entries);
                    Status status = status(connection, entries);
                    if (status.missingInterests() != 0 || status.missingCommunities() != 0) {
                        throw new SQLException("Catalog validation failed; transaction rolled back.");
                    }
                    return added;
                });
                System.out.println("Added " + changes.categories() + " categories, "
                        + changes.interests() + " interests and "
                        + changes.communities() + " communities.");
                System.out.println("Existing catalog entries and student records preserved.");
            }
            Status status = Db.use(connection -> status(connection, entries));
            System.out.println("Active catalog: " + status.activeInterests() + " interests in "
                    + status.activeCategories() + " categories; "
                    + status.activeCommunities() + " communities.");
            System.out.println("Database totals: " + status.categories() + " categories, "
                    + status.interests() + " interests, " + status.communities() + " communities.");
            System.out.println("Missing catalog interests: " + status.missingInterests()
                    + "; approved interests without communities: " + status.missingCommunities()
                    + "; existing unpublished interests preserved: "
                    + status.unpublishedInterests() + ".");
            if (args[0].equals("--verify")
                    && (status.missingInterests() > 0
                            || status.missingCommunities() > 0
                            || status.unpublishedInterests() > 0
                            || status.activeInterests() != entries.size())) {
                System.exit(1);
            }
        } finally {
            Db.close();
        }
    }
}
