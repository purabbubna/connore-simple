import com.connore.dao.InterestDao;
import com.connore.dao.CommunityDao;
import com.connore.tools.InterestCatalog;
import com.connore.util.Config;
import com.connore.util.Db;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs additive-import checks inside one transaction, then rolls back all test rows. */
public class CatalogDbTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("PASS: " + message);
    }
    public static void main(String[] args) throws Exception {
        var config = Config.load();
        Db.configure(config);
        Class.forName("com.mysql.cj.jdbc.Driver");
        try {
            Db.use(connection -> {
                connection.setAutoCommit(false);
                try {
                    String name = "Catalog check " + UUID.randomUUID().toString().substring(0, 8);
                    int category = Db.insert(connection,
                            "INSERT INTO category(name) VALUES(?)", name);
                    int approved = Db.insert(connection,
                            "INSERT INTO interest(name,category_id,is_approved) VALUES(?,?,TRUE)",
                            name + " approved", category);
                    int unpublished = Db.insert(connection,
                            "INSERT INTO interest(name,category_id,is_approved) VALUES(?,?,FALSE)",
                            name + " unpublished", category);
                    int community = Db.insert(connection,
                            "INSERT INTO community(interest_id,description) VALUES(?,?)",
                            approved, "Existing custom description");
                    var entries = List.of(
                            new InterestCatalog.Entry(
                                    name, name + " approved", "Replacement must not be used"),
                            new InterestCatalog.Entry(
                                    name, name + " unpublished", "Must stay unpublished"),
                            new InterestCatalog.Entry(
                                    name, name + " new", "New community description"));
                    var first = InterestCatalog.addMissing(connection, entries);
                    check(first.equals(new InterestCatalog.Changes(0, 1, 1)),
                            "Only missing interest and community inserted");
                    check(Db.integer(Db.query(connection,
                            "SELECT interest_id FROM interest WHERE name=?",
                            name + " approved").get(0).get("interest_id")) == approved,
                            "Existing interest ID preserved");
                    check(Db.text(Db.query(connection,
                            "SELECT description FROM community WHERE community_id=?",
                            community).get(0).get("description")).equals("Existing custom description"),
                            "Existing community description preserved");
                    check(!Db.bool(Db.query(connection,
                            "SELECT is_approved FROM interest WHERE interest_id=?",
                            unpublished).get(0).get("is_approved")),
                            "Existing unpublished interest stays unpublished");
                    check(Db.query(connection,
                            "SELECT community_id FROM community WHERE interest_id=?",
                            unpublished).isEmpty(),
                            "Unpublished interest does not create a community");
                    check(InterestCatalog.addMissing(connection, entries)
                            .equals(new InterestCatalog.Changes(0, 0, 0)),
                            "Repeated import adds nothing");
                    var status = InterestCatalog.status(connection, entries);
                    check(status.missingInterests() == 0
                            && status.missingCommunities() == 0
                            && status.unpublishedInterests() == 1,
                            "Catalog status accounts for existing moderation");
                    var reassignment = List.of(new InterestCatalog.Entry(
                            name + " unused", name + " approved", "Ignored"));
                    InterestCatalog.addMissing(connection, reassignment);
                    check(Db.query(connection,
                            "SELECT category_id FROM category WHERE name=?",
                            name + " unused").isEmpty(),
                            "Existing interest does not create or move to another category");
                    int retired = Db.insert(connection,
                            "INSERT INTO interest(name,category_id,is_approved) VALUES(?,?,TRUE)",
                            name + " retired", category);
                    var syncEntries = List.of(
                            new InterestCatalog.Entry(
                                    name + " compact", name + " approved", "Still preserve description"),
                            new InterestCatalog.Entry(
                                    name + " compact", name + " unpublished", "Newly active community"));
                    var synced = InterestCatalog.synchronize(connection, syncEntries);
                    check(synced.reactivated() == 1 && synced.archived() >= 2,
                            "Synchronization activates chosen interests and archives others");
                    check(Db.query(connection,
                            "SELECT interest_id FROM interest WHERE interest_id=? AND is_approved=FALSE",
                            retired).size() == 1,
                            "Archived interest retains its original ID");
                    check(Db.text(Db.query(connection,
                            "SELECT description FROM community WHERE community_id=?",
                            community).get(0).get("description")).equals("Existing custom description"),
                            "Synchronization retains existing community descriptions");
                    var again = InterestCatalog.synchronize(connection, syncEntries);
                    check(again.added().equals(new InterestCatalog.Changes(0, 0, 0))
                            && again.archived() == 0
                            && again.reactivated() == 0
                            && again.reassigned() == 0,
                            "Synchronization is idempotent");
                    return null;
                } finally {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            });
            var entries = InterestCatalog.read(Path.of("sql/interest-catalog.tsv"));
            var status = Db.use(connection -> InterestCatalog.status(connection, entries));
            check(status.missingInterests() == 0 && status.missingCommunities() == 0,
                    "Full real catalog is available");
            var grouped = new InterestDao().findAllGrouped();
            int exposed = 0;
            boolean validShape = true;
            for (var category : grouped) {
                @SuppressWarnings("unchecked")
                var interests = (List<Map<String, Object>>) category.get("interests");
                for (var interest : interests) {
                    validShape &= interest.get("interestId") instanceof Integer
                            && interest.get("name") instanceof String;
                    exposed++;
                }
            }
            check(validShape, "Every interest has the expected API field types");
            check(exposed == entries.size() && grouped.size() == 8,
                    "Only the compact catalog and its eight categories are exposed");
            check(new CommunityDao().findAll(0).size() == entries.size(),
                    "Community browser exposes only active catalog communities");
            System.out.println("All JDBC catalog checks passed; test records rolled back.");
        } finally {
            Db.close();
        }
    }
}
