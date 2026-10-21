package com.connore.dao;

import com.connore.util.Db;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class CommunityDao {
    public List<Map<String, Object>> findAll(int student) throws SQLException {
        var communities = Db.query("""
            SELECT c.community_id AS communityId,c.interest_id AS interestId,c.description,
                   i.name,cat.name AS category,
                   (SELECT COUNT(*) FROM community_membership m
                    WHERE m.community_id=c.community_id) AS memberCount,
                   EXISTS(SELECT 1 FROM community_membership m
                          WHERE m.community_id=c.community_id AND m.student_id=?) AS joined
            FROM community c
            JOIN interest i ON i.interest_id=c.interest_id AND i.is_approved=TRUE
            JOIN category cat ON cat.category_id=i.category_id
            ORDER BY cat.name,i.name
            """, student);
        communities.forEach(row -> row.put("joined", Db.bool(row.get("joined"))));
        return communities;
    }

    public boolean exists(int id) throws SQLException {
        return !Db.query("SELECT community_id FROM community WHERE community_id=?", id).isEmpty();
    }

    public boolean existsActive(int id) throws SQLException {
        return !Db.query("""
            SELECT c.community_id FROM community c
            JOIN interest i ON i.interest_id=c.interest_id
            WHERE c.community_id=? AND i.is_approved=TRUE
            """, id).isEmpty();
    }

    public List<Integer> findMembers(int id) throws SQLException {
        return Db.query("SELECT student_id FROM community_membership WHERE community_id=? ORDER BY joined_at", id)
            .stream().map(row -> Db.integer(row.get("student_id"))).toList();
    }

    public void addMember(int student, int community) throws SQLException {
        Db.exec("""
            INSERT INTO community_membership(student_id,community_id) VALUES(?,?)
            ON DUPLICATE KEY UPDATE community_id=VALUES(community_id)
            """, student, community);
    }

    public void removeMember(int student, int community) throws SQLException {
        Db.exec("DELETE FROM community_membership WHERE student_id=? AND community_id=?", student, community);
    }
}
