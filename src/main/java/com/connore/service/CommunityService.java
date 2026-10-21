package com.connore.service;

import com.connore.dao.CommunityDao;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.connore.util.ApiException.require;

public final class CommunityService {
    private final CommunityDao dao = new CommunityDao();
    private final ProfileService profiles = new ProfileService();

    public List<Map<String, Object>> listCommunities(int student) throws SQLException {
        return dao.findAll(student);
    }

    public void join(int student, int id) throws SQLException {
        profiles.requireProfile(student);
        require(dao.existsActive(id), 404, "COMMUNITY_NOT_FOUND");
        dao.addMember(student, id);
    }

    public void leave(int student, int id) throws SQLException {
        profiles.requireProfile(student);
        require(dao.exists(id), 404, "COMMUNITY_NOT_FOUND");
        dao.removeMember(student, id);
    }

    public List<Map<String, Object>> listMembers(int id, int viewer) throws SQLException {
        require(dao.existsActive(id), 404, "COMMUNITY_NOT_FOUND");
        var result = new ArrayList<Map<String, Object>>();
        for (int student : dao.findMembers(id)) result.add(profiles.publicProfile(student));
        return result;
    }
}
