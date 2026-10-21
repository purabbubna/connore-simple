package com.connore.web;
import com.connore.service.CommunityService;
import com.connore.util.ApiException;
import java.sql.SQLException;
public final class CommunityEndpoint implements Endpoint {
    private final CommunityService service;
    public CommunityEndpoint(CommunityService service){this.service=service;}
    public Reply handle(Request r)throws SQLException{
        String p=r.path(),m=r.method();if(m.equals("GET")&&p.equals("/communities"))return Reply.ok(service.listCommunities(r.studentId()));
        if(p.matches("/communities/[0-9]+/(join|membership|members)")){
            String[] parts=p.split("/");int id=Request.pathId(parts[2]);
            if(m.equals("POST")&&parts[3].equals("join")){service.join(r.studentId(),id);return Reply.ok();}
            if(m.equals("DELETE")&&parts[3].equals("membership")){service.leave(r.studentId(),id);return Reply.empty();}
            if(m.equals("GET")&&parts[3].equals("members"))return Reply.ok(service.listMembers(id,r.studentId()));
        }throw new ApiException(404,"NOT_FOUND");
    }
}
