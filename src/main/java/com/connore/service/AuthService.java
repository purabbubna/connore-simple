package com.connore.service;

import com.connore.dao.AccountDao;
import com.connore.model.Account;
import com.connore.util.ApiException;
import com.connore.util.PasswordHasher;
import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;
import static com.connore.util.ApiException.require;

public final class AuthService {
    private final AccountDao accounts = new AccountDao();
    private final PasswordHasher hasher = new PasswordHasher();
    private final String domain;
    private final String dummyHash;

    public AuthService(String domain) {
        this.domain = domain.toLowerCase(Locale.ROOT);
        dummyHash = hasher.hash("timing-placeholder-9b8f");
    }

    private String email(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public int register(String raw, String password) throws SQLException {
        String email = email(raw);
        require(email.length() <= 100 && email.matches("^[^\\s@]+@" + Pattern.quote(domain) + "$"), 400, "NOT_COLLEGE_EMAIL");
        require(password.length() >= 8 && password.length() <= 200, 400, "PASSWORD_TOO_SHORT");
        require(accounts.findByEmail(email) == null, 409, "EMAIL_EXISTS");
        try {
            return accounts.insert(email, hasher.hash(password));
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1062) throw new ApiException(409, "EMAIL_EXISTS");
            throw exception;
        }
    }

    public Account login(String raw, String password) throws SQLException {
        require(password.length() <= 200 && raw.length() <= 100, 401, "BAD_CREDENTIALS");
        Account account = accounts.findByEmail(email(raw));
        boolean valid = hasher.verify(password, account == null ? dummyHash : account.passwordHash());
        require(account != null && valid, 401, "BAD_CREDENTIALS");
        return account;
    }
}
