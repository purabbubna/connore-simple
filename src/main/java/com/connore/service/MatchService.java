package com.connore.service;

import com.connore.dao.InterestDao;
import com.connore.dao.ProfileDao;
import com.connore.matching.MatchResult;
import com.connore.matching.MatchingStrategy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class MatchService {
    private final MatchingStrategy strategy;
    private final ProfileDao profiles = new ProfileDao();
    private final InterestDao interests = new InterestDao();
    private final ProfileService profileService = new ProfileService();

    public MatchService(MatchingStrategy strategy) {
        this.strategy = strategy;
    }

    public List<Map<String, Object>> suggestFor(int student, int limit) throws SQLException {
        var me = profileService.getProfile(student);
        var results = new ArrayList<MatchResult>();
        for (var profile : profiles.findCandidates(student)) {
            results.add(strategy.score(me, profile.withInterests(interests.findByStudent(profile.studentId()))));
        }
        results.sort(Comparator.comparingDouble(MatchResult::score).reversed());
        return results.stream()
            .limit(Math.max(1, Math.min(50, limit)))
            .map(MatchResult::publicReply)
            .toList();
    }
}
