package com.connore.web;
import com.connore.service.ProfileService;
import com.connore.util.ApiException;
import java.sql.SQLException;
public final class ProfileEndpoint implements Endpoint {
    private final ProfileService service;
    public ProfileEndpoint(ProfileService service){this.service=service;}
    public Reply handle(Request r)throws SQLException{
        int id=r.studentId();String p=r.path(),m=r.method();
        if(p.equals("/profile")){
            if(m.equals("GET"))return Reply.ok(service.getProfile(id).toMap(true));
            if(m.equals("PUT"))return Reply.ok(service.saveProfile(id,r.string("fullName"),r.string("department"),r.optionalInt("yearOfStudy"),r.string("bio")));
        }
        if(p.equals("/profile/privacy")&&m.equals("PUT")){service.setPrivacy(id,r.bool("suggestible"),r.bool("showDepartment"),r.bool("showYear"));return Reply.ok();}
        if(p.equals("/interests")&&m.equals("GET"))return Reply.ok(service.allInterests());
        if(p.equals("/profile/interests")){
            if(m.equals("GET"))return Reply.ok(service.interestIds(id));
            if(m.equals("POST")){service.addInterest(id,r.integer("interestId"));return Reply.ok();}
        }
        if(p.matches("/profile/interests/[0-9]+")&&m.equals("DELETE")){service.removeInterest(id,Request.pathId(p.substring(p.lastIndexOf('/')+1)));return Reply.empty();}
        if(p.equals("/profile/social-links")){
            if(m.equals("GET"))return Reply.ok(service.socialLinks(id));
            if(m.equals("POST"))return new Reply(201,service.addSocialLink(id,r.string("platform"),r.string("handle")));
        }
        if(p.matches("/profile/social-links/[0-9]+")&&m.equals("PUT")){service.setShared(Request.pathId(p.substring(p.lastIndexOf('/')+1)),id,r.bool("shared"));return Reply.ok();}
        throw new ApiException(404,"NOT_FOUND");
    }
}
