package com.connore.dao;

import com.connore.model.Account;
import com.connore.util.Db;
import java.sql.SQLException;

public final class AccountDao {
    public Account findByEmail(String email) throws SQLException {
        var rows = Db.query("SELECT * FROM account WHERE college_email=?", email);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        return new Account(
            Db.integer(row.get("account_id")),
            Db.text(row.get("college_email")),
            Db.text(row.get("password_hash")),
            Db.bool(row.get("email_verified")),
            Db.instant(row.get("created_at")));
    }

    public int insert(String email, String hash) throws SQLException {
        return Db.insert("INSERT INTO account(college_email,password_hash) VALUES(?,?)", email, hash);
    }
}
