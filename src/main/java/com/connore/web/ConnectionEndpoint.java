package com.connore.web;
import com.connore.service.ConnectionService;
import com.connore.util.ApiException;
import java.sql.SQLException;
public final class ConnectionEndpoint implements Endpoint {
    private final ConnectionService service;
    public ConnectionEndpoint(ConnectionService service){this.service=service;}
    public Reply handle(Request r)throws SQLException{
        String p=r.path(),m=r.method();if(p.equals("/connections")){
            if(m.equals("GET"))return Reply.ok(service.listFor(r.studentId(),r.query("view","incoming")));
            if(m.equals("POST"))return Reply.ok(service.sendRequest(r.studentId(),r.integer("toStudentId"),r.optionalInt("interestId")));
        }
        if(m.equals("POST")&&p.matches("/connections/[0-9]+/(respond|block)")){
            String[] parts=p.split("/");int id=Request.pathId(parts[2]);if(parts[3].equals("respond"))service.respond(id,r.studentId(),r.bool("accept"));else service.block(id,r.studentId());return Reply.ok();
        }throw new ApiException(404,"NOT_FOUND");
    }
}
