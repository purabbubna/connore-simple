package com.connore.service;

import com.connore.dao.ConnectionDao;
import com.connore.dao.InterestDao;
import com.connore.dao.SocialLinkDao;
import com.connore.model.AcceptedConnection;
import com.connore.model.Block;
import com.connore.model.ClosedRequest;
import com.connore.model.Connection;
import com.connore.model.PendingRequest;
import com.connore.util.ApiException;
import com.connore.util.Db;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.connore.util.ApiException.require;

public final class ConnectionService {
    private final ConnectionDao dao = new ConnectionDao();
    private final ProfileService profiles = new ProfileService();
    private final InterestDao interests = new InterestDao();
    private final SocialLinkDao social = new SocialLinkDao();

    public Map<String, Object> sendRequest(int from, int to, Integer interest) throws SQLException {
        require(from != to, 403, "REFUSED");
        profiles.requireProfile(from);
        require(profiles.hasProfile(to), 403, "REFUSED");
        require(interest == null || interests.exists(interest), 400, "INVALID_INTEREST");
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                return Db.tx(connection -> {
                    Connection row = dao.findByPair(connection, from, to);
                    if (row instanceof PendingRequest pending && !pending.expiresAt().isAfter(Instant.now())) {
                        row = pending.expire();
                        dao.markClosed(connection, (ClosedRequest) row);
                    }
                    String result;
                    if (row == null) {
                        row = dao.insertPending(connection, from, to, interest);
                        result = "PENDING";
                    } else if (row instanceof Block) {
                        throw new ApiException(403, "REFUSED");
                    } else if (row instanceof AcceptedConnection) {
                        result = "ALREADY_CONNECTED";
                    } else if (row instanceof PendingRequest pending) {
                        if (pending.requesterId() == from) result = "ALREADY_PENDING";
                        else {
                            dao.markAccepted(connection, pending.accept());
                            result = "CONNECTED";
                        }
                    } else {
                        var next = ((ClosedRequest) row).reopen(from, interest);
                        dao.reopen(connection, next);
                        result = "PENDING";
                    }
                    return Map.<String, Object>of("result", result, "connectionId", row.connectionId());
                });
            } catch (SQLException exception) {
                if (attempt == 3 || !(exception.getErrorCode() == 1062 || exception.getErrorCode() == 1213
                    || exception.getErrorCode() == 1205 || "40001".equals(exception.getSQLState()))) throw exception;
            }
        }
        throw new IllegalStateException("Retry exhausted");
    }

    public void respond(int id, int student, boolean accept) throws SQLException {
        boolean done = Db.tx(connection -> {
            Connection row = dao.findById(connection, id);
            require(row instanceof PendingRequest && row.receiverId() == student, 403, "FORBIDDEN");
            var pending = (PendingRequest) row;
            if (!pending.expiresAt().isAfter(Instant.now())) {
                dao.markClosed(connection, pending.expire());
                return false;
            }
            if (accept) dao.markAccepted(connection, pending.accept());
            else dao.markClosed(connection, pending.decline());
            return true;
        });
        require(done, 403, "FORBIDDEN");
    }

    public void block(int id, int student) throws SQLException {
        boolean done = Db.tx(connection -> {
            Connection row = dao.findById(connection, id);
            require(row != null && row.includes(student), 403, "FORBIDDEN");
            if (row instanceof PendingRequest pending) {
                if (!pending.expiresAt().isAfter(Instant.now())) {
                    dao.markClosed(connection, pending.expire());
                    return false;
                }
                dao.markBlocked(connection, pending.block(student));
            } else if (row instanceof AcceptedConnection accepted) {
                dao.markBlocked(connection, accepted.block(student));
            } else throw new ApiException(403, "FORBIDDEN");
            return true;
        });
        require(done, 403, "FORBIDDEN");
    }

    public void expireStale() throws SQLException {
        for (int id : dao.findOverdue()) {
            Db.tx(connection -> {
                var row = dao.findById(connection, id);
                if (row instanceof PendingRequest pending && !pending.expiresAt().isAfter(Instant.now()))
                    dao.markClosed(connection, pending.expire());
                return null;
            });
        }
    }

    public List<Map<String, Object>> listFor(int student, String view) throws SQLException {
        require(List.of("incoming", "outgoing", "accepted").contains(view), 400, "INVALID_VIEW");
        expireStale();
        var result = new ArrayList<Map<String, Object>>();
        for (var row : dao.findForStudent(student, view)) {
            int from = Db.integer(row.get("requester_id"));
            int to = Db.integer(row.get("receiver_id"));
            int other = from == student ? to : from;
            var profile = profiles.publicProfile(other);
            profile.put("connectionId", row.get("connection_id"));
            profile.put("status", row.get("status"));
            profile.put("contextInterestId", row.get("context_interest_id"));
            profile.put("expiresAt", row.get("expires_at") == null ? null : Db.instant(row.get("expires_at")).toString());
            if (view.equals("accepted")) {
                profile.put("socialLinks", social.findSharedByStudent(other).stream()
                    .map(link -> Map.of("platform", link.platform(), "handle", link.handle()))
                    .toList());
            }
            result.add(profile);
        }
        return result;
    }
}
