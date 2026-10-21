package com.connore.web;
import com.connore.service.*;
import com.connore.util.ApiException;
import java.sql.SQLException;
public final class MatchEndpoint implements Endpoint {
    private final MatchService service;private final ConnectionService connections;
    public MatchEndpoint(MatchService service,ConnectionService connections){this.service=service;this.connections=connections;}
    public Reply handle(Request r)throws SQLException{if(r.method().equals("GET")&&r.path().equals("/matches")){connections.expireStale();return Reply.ok(service.suggestFor(r.studentId(),r.queryInt("limit",30)));}throw new ApiException(404,"NOT_FOUND");}
}
