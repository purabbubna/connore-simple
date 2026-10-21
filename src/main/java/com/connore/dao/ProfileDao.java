package com.connore.dao;

import com.connore.model.StudentProfile;
import com.connore.util.Db;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class ProfileDao {
    private StudentProfile map(Map<String, Object> row) {
        return new StudentProfile(
            Db.integer(row.get("student_id")),
            Db.text(row.get("full_name")), Db.text(row.get("department")),
            row.get("year_of_study") == null ? null : Db.integer(row.get("year_of_study")),
            Db.text(row.get("bio")), Db.bool(row.get("show_department")),
            Db.bool(row.get("show_year")), Db.bool(row.get("suggestible")), List.of());
    }

    public StudentProfile findById(int id) throws SQLException {
        var rows = Db.query("SELECT * FROM student_profile WHERE student_id=?", id);
        return rows.isEmpty() ? null : map(rows.get(0));
    }

    public void upsert(int id, String name, String department, Integer year, String bio) throws SQLException {
        Db.exec("""
            INSERT INTO student_profile(student_id,full_name,department,year_of_study,bio)
            VALUES(?,?,?,?,?)
            ON DUPLICATE KEY UPDATE full_name=?,department=?,year_of_study=?,bio=?
            """, id, name, department, year, bio, name, department, year, bio);
    }

    public void setPrivacy(int id, boolean suggestible, boolean showDepartment, boolean showYear) throws SQLException {
        Db.exec("""
            UPDATE student_profile SET suggestible=?,show_department=?,show_year=?
            WHERE student_id=?
            """, suggestible, showDepartment, showYear, id);
    }

    public List<StudentProfile> findCandidates(int id) throws SQLException {
        return Db.query("""
            SELECT p.* FROM student_profile p
            WHERE p.student_id<>? AND p.suggestible=TRUE
              AND EXISTS (
                SELECT 1 FROM student_interest a
                JOIN student_interest b ON a.interest_id=b.interest_id
                JOIN interest i ON i.interest_id=a.interest_id AND i.is_approved=TRUE
                WHERE a.student_id=? AND b.student_id=p.student_id
              )
              AND NOT EXISTS (
                SELECT 1 FROM `connection` c
                WHERE c.student_low_id=LEAST(?,p.student_id)
                  AND c.student_high_id=GREATEST(?,p.student_id)
                  AND c.status IN ('PENDING','ACCEPTED','BLOCKED')
              )
            """, id, id, id, id).stream().map(this::map).toList();
    }
}
