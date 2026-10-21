package com.connore.dao;

import com.connore.model.Interest;
import com.connore.util.Db;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InterestDao {
    public List<Map<String, Object>> findAllGrouped() throws SQLException {
        var categories = new LinkedHashMap<Integer, Map<String, Object>>();
        var rows = Db.query("""
            SELECT c.category_id,c.name AS category_name,i.interest_id,i.name AS interest_name
            FROM category c
            JOIN interest i ON i.category_id=c.category_id AND i.is_approved=TRUE
            ORDER BY c.name,i.name
            """);
        for (var row : rows) {
            int id = Db.integer(row.get("category_id"));
            var category = categories.computeIfAbsent(id, key -> {
                var value = new LinkedHashMap<String, Object>();
                value.put("categoryId", id);
                value.put("name", row.get("category_name"));
                value.put("interests", new ArrayList<Map<String, Object>>());
                return value;
            });
            if (row.get("interest_id") != null) {
                @SuppressWarnings("unchecked")
                var interests = (List<Map<String, Object>>) category.get("interests");
                interests.add(Map.of(
                    "interestId", Db.integer(row.get("interest_id")),
                    "name", row.get("interest_name")));
            }
        }
        return new ArrayList<>(categories.values());
    }

    public List<Interest> findByStudent(int id) throws SQLException {
        return Db.query("""
            SELECT i.* FROM interest i
            JOIN student_interest si ON si.interest_id=i.interest_id
            WHERE si.student_id=? AND i.is_approved=TRUE
            ORDER BY i.interest_id
            """, id).stream()
            .map(row -> new Interest(
                Db.integer(row.get("interest_id")), Db.text(row.get("name")),
                Db.integer(row.get("category_id")), Db.bool(row.get("is_approved"))))
            .toList();
    }

    public boolean exists(int id) throws SQLException {
        return !Db.query("SELECT interest_id FROM interest WHERE interest_id=? AND is_approved=TRUE", id).isEmpty();
    }

    public void link(int student, int interest) throws SQLException {
        Db.exec("""
            INSERT INTO student_interest(student_id,interest_id) VALUES(?,?)
            ON DUPLICATE KEY UPDATE interest_id=VALUES(interest_id)
            """, student, interest);
    }

    public void unlink(int student, int interest) throws SQLException {
        Db.exec("DELETE FROM student_interest WHERE student_id=? AND interest_id=?", student, interest);
    }
}
