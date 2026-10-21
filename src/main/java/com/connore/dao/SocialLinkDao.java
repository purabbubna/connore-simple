package com.connore.dao;

import com.connore.model.SocialLink;
import com.connore.util.Db;
import java.sql.SQLException;
import java.util.List;

public final class SocialLinkDao {
    private List<SocialLink> find(int id, boolean shared) throws SQLException {
        String sql = "SELECT * FROM social_link WHERE student_id=?"
            + (shared ? " AND shared=TRUE" : "")
            + " ORDER BY link_id";
        return Db.query(sql, id).stream()
            .map(row -> new SocialLink(
                Db.integer(row.get("link_id")), id,
                Db.text(row.get("platform")), Db.text(row.get("handle")),
                Db.bool(row.get("shared"))))
            .toList();
    }

    public List<SocialLink> findByStudent(int id) throws SQLException {
        return find(id, false);
    }

    public List<SocialLink> findSharedByStudent(int id) throws SQLException {
        return find(id, true);
    }

    public int insert(int student, String platform, String handle) throws SQLException {
        return Db.insert("""
            INSERT INTO social_link(student_id,platform,handle,shared)
            VALUES(?,?,?,FALSE)
            """, student, platform, handle);
    }

    public boolean updateShared(int link, int student, boolean shared) throws SQLException {
        if (Db.query("SELECT link_id FROM social_link WHERE link_id=? AND student_id=?", link, student).isEmpty()) {
            return false;
        }
        Db.exec("UPDATE social_link SET shared=? WHERE link_id=? AND student_id=?", shared, link, student);
        return true;
    }
}
