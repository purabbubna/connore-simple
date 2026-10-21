package com.connore.dao;

import com.connore.model.AcceptedConnection;
import com.connore.model.Block;
import com.connore.model.ClosedRequest;
import com.connore.model.Connection;
import com.connore.model.PendingRequest;
import com.connore.util.Db;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ConnectionDao {
    private static final String SELECT = """
        SELECT c.*,p.expires_at,a.connected_at,cr.outcome,cr.closed_at,b.blocked_by,b.blocked_at
        FROM `connection` c
        LEFT JOIN pending_request p ON p.connection_id=c.connection_id
        LEFT JOIN accepted_connection a ON a.connection_id=c.connection_id
        LEFT JOIN closed_request cr ON cr.connection_id=c.connection_id
        LEFT JOIN `block` b ON b.connection_id=c.connection_id
        """;

    private Connection map(Map<String, Object> row) {
        int id = Db.integer(row.get("connection_id"));
        int from = Db.integer(row.get("requester_id"));
        int to = Db.integer(row.get("receiver_id"));
        Integer interest = row.get("context_interest_id") == null
            ? null : Db.integer(row.get("context_interest_id"));
        var created = Db.instant(row.get("created_at"));
        return switch (Db.text(row.get("status"))) {
            case "PENDING" -> new PendingRequest(id, from, to, interest, created, Db.instant(row.get("expires_at")));
            case "ACCEPTED" -> new AcceptedConnection(id, from, to, interest, created, Db.instant(row.get("connected_at")));
            case "CLOSED" -> new ClosedRequest(id, from, to, interest, created,
                Db.text(row.get("outcome")), Db.instant(row.get("closed_at")));
            case "BLOCKED" -> new Block(id, from, to, interest, created,
                Db.integer(row.get("blocked_by")), Db.instant(row.get("blocked_at")));
            default -> throw new IllegalStateException("Invalid connection status");
        };
    }

    public Connection findByPair(java.sql.Connection connection, int first, int second) throws SQLException {
        var rows = Db.query(connection,
            SELECT + "WHERE c.student_low_id=? AND c.student_high_id=? FOR UPDATE",
            Math.min(first, second), Math.max(first, second));
        return rows.isEmpty() ? null : map(rows.get(0));
    }

    public Connection findById(java.sql.Connection connection, int id) throws SQLException {
        var rows = Db.query(connection, SELECT + "WHERE c.connection_id=? FOR UPDATE", id);
        return rows.isEmpty() ? null : map(rows.get(0));
    }

    public PendingRequest insertPending(java.sql.Connection connection, int from, int to, Integer interest) throws SQLException {
        int id = Db.insert(connection, """
            INSERT INTO `connection`(requester_id,receiver_id,context_interest_id,status)
            VALUES(?,?,?,'PENDING')
            """, from, to, interest);
        var now = Instant.now();
        var pending = new PendingRequest(id, from, to, interest, now, now.plusSeconds(172800));
        insertSubtype(connection, pending);
        return pending;
    }

    private void insertSubtype(java.sql.Connection connection, Connection next) throws SQLException {
        if (next instanceof PendingRequest pending) {
            Db.exec(connection, "INSERT INTO pending_request(connection_id,expires_at) VALUES(?,?)",
                pending.connectionId(), Timestamp.from(pending.expiresAt()));
        } else if (next instanceof AcceptedConnection accepted) {
            Db.exec(connection, "INSERT INTO accepted_connection(connection_id,connected_at) VALUES(?,?)",
                accepted.connectionId(), Timestamp.from(accepted.connectedAt()));
        } else if (next instanceof ClosedRequest closed) {
            Db.exec(connection, "INSERT INTO closed_request(connection_id,outcome,closed_at) VALUES(?,?,?)",
                closed.connectionId(), closed.outcome(), Timestamp.from(closed.closedAt()));
        } else if (next instanceof Block blocked) {
            Db.exec(connection, "INSERT INTO `block`(connection_id,blocked_by,blocked_at) VALUES(?,?,?)",
                blocked.connectionId(), blocked.blockedBy(), Timestamp.from(blocked.blockedAt()));
        }
    }

    private void transition(java.sql.Connection connection, Connection next) throws SQLException {
        for (String table : List.of("pending_request", "accepted_connection", "closed_request", "`block`")) {
            Db.exec(connection, "DELETE FROM " + table + " WHERE connection_id=?", next.connectionId());
        }
        insertSubtype(connection, next);
        Db.exec(connection, """
            UPDATE `connection` SET requester_id=?,receiver_id=?,context_interest_id=?,status=?,created_at=?
            WHERE connection_id=?
            """, next.requesterId(), next.receiverId(), next.contextInterestId(), next.status(),
            Timestamp.from(next.createdAt()), next.connectionId());
    }

    public void markAccepted(java.sql.Connection connection, AcceptedConnection next) throws SQLException {
        transition(connection, next);
    }

    public void markClosed(java.sql.Connection connection, ClosedRequest next) throws SQLException {
        transition(connection, next);
    }

    public void reopen(java.sql.Connection connection, PendingRequest next) throws SQLException {
        transition(connection, next);
    }

    public void markBlocked(java.sql.Connection connection, Block next) throws SQLException {
        transition(connection, next);
    }

    public List<Integer> findOverdue() throws SQLException {
        return Db.query("SELECT connection_id FROM pending_request WHERE expires_at<=UTC_TIMESTAMP() ORDER BY connection_id")
            .stream().map(row -> Db.integer(row.get("connection_id"))).toList();
    }

    public List<Map<String, Object>> findForStudent(int id, String view) throws SQLException {
        String where = switch (view) {
            case "incoming" -> "c.receiver_id=? AND c.status='PENDING'";
            case "outgoing" -> "c.requester_id=? AND c.status IN ('PENDING','CLOSED')";
            case "accepted" -> "(c.requester_id=? OR c.receiver_id=?) AND c.status='ACCEPTED'";
            default -> throw new IllegalArgumentException("Invalid view");
        };
        String sql = """
            SELECT c.connection_id,c.requester_id,c.receiver_id,c.context_interest_id,c.status,p.expires_at
            FROM `connection` c
            LEFT JOIN pending_request p ON p.connection_id=c.connection_id
            WHERE
            """ + where + " ORDER BY c.created_at DESC";
        return view.equals("accepted") ? Db.query(sql, id, id) : Db.query(sql, id);
    }
}
