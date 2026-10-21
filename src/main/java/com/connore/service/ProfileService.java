package com.connore.service;

import com.connore.dao.InterestDao;
import com.connore.dao.ProfileDao;
import com.connore.dao.SocialLinkDao;
import com.connore.model.Interest;
import com.connore.model.SocialLink;
import com.connore.model.StudentProfile;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import static com.connore.util.ApiException.require;

public final class ProfileService {
    private final ProfileDao profiles = new ProfileDao();
    private final InterestDao interests = new InterestDao();
    private final SocialLinkDao social = new SocialLinkDao();

    public StudentProfile getProfile(int id) throws SQLException {
        var profile = profiles.findById(id);
        require(profile != null, 404, "PROFILE_NOT_FOUND");
        return profile.withInterests(interests.findByStudent(id));
    }

    public Map<String, Object> publicProfile(int id) throws SQLException {
        return getProfile(id).toMap(false);
    }

    public boolean hasProfile(int id) throws SQLException {
        return profiles.findById(id) != null;
    }

    public void requireProfile(int id) throws SQLException {
        require(hasProfile(id), 409, "PROFILE_REQUIRED");
    }

    public Map<String, Object> saveProfile(int id, String name, String department, Integer year, String bio) throws SQLException {
        require(!name.isBlank() && name.length() <= 100, 400, "INVALID_NAME");
        require(department.length() <= 60, 400, "INVALID_DEPARTMENT");
        require(year == null || year >= 1 && year <= 6, 400, "INVALID_YEAR");
        require(bio.length() <= 500, 400, "BIO_TOO_LONG");
        profiles.upsert(id, name.trim(), department, year, bio);
        return getProfile(id).toMap(true);
    }

    public void setPrivacy(int id, boolean suggestible, boolean showDepartment, boolean showYear) throws SQLException {
        requireProfile(id);
        profiles.setPrivacy(id, suggestible, showDepartment, showYear);
    }

    public List<Map<String, Object>> allInterests() throws SQLException {
        return interests.findAllGrouped();
    }

    public List<Integer> interestIds(int id) throws SQLException {
        requireProfile(id);
        return interests.findByStudent(id).stream().map(Interest::interestId).toList();
    }

    public void addInterest(int id, int interest) throws SQLException {
        requireProfile(id);
        require(interests.exists(interest), 404, "INTEREST_NOT_FOUND");
        interests.link(id, interest);
    }

    public void removeInterest(int id, int interest) throws SQLException {
        requireProfile(id);
        interests.unlink(id, interest);
    }

    public List<Map<String, Object>> socialLinks(int id) throws SQLException {
        requireProfile(id);
        return social.findByStudent(id).stream().map(SocialLink::toMap).toList();
    }

    public Map<String, Object> addSocialLink(int id, String platform, String handle) throws SQLException {
        requireProfile(id);
        require(List.of("Instagram", "GitHub", "LinkedIn", "Discord", "Other").contains(platform)
            && !handle.isBlank() && handle.length() <= 100, 400, "INVALID_HANDLE");
        int link = social.insert(id, platform, handle.trim());
        return new SocialLink(link, id, platform, handle.trim(), false).toMap();
    }

    public void setShared(int link, int student, boolean shared) throws SQLException {
        requireProfile(student);
        require(social.updateShared(link, student, shared), 403, "FORBIDDEN");
    }
}
