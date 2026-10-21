package com.connore.web;
import com.connore.service.*;
import com.connore.util.ApiException;
import java.sql.SQLException;
import java.util.Map;
public final class AuthEndpoint implements Endpoint {
    private final AuthService service;private final ProfileService profiles;private final AuthFilter sessions;
    public AuthEndpoint(AuthService service,ProfileService profiles,AuthFilter sessions){this.service=service;this.profiles=profiles;this.sessions=sessions;}
    public Reply handle(Request r)throws SQLException{
        if(r.method().equals("GET")&&r.path().equals("/session"))return Reply.ok(Map.of("studentId",r.studentId(),"hasProfile",profiles.hasProfile(r.studentId())));
        if(r.method().equals("POST"))switch(r.path()){
            case "/auth/register":return new Reply(201,Map.of("accountId",service.register(r.string("email"),r.string("password"))));
            case "/auth/login":var a=service.login(r.string("email"),r.string("password"));sessions.create(r.exchange(),a.accountId());return Reply.ok(Map.of("studentId",a.accountId(),"hasProfile",profiles.hasProfile(a.accountId())));
            case "/auth/logout":sessions.invalidate(r.exchange());return Reply.empty();
        }
        throw new ApiException(404,"NOT_FOUND");
    }
}
