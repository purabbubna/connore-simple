package com.connore.matching;
import com.connore.model.StudentProfile;
public interface MatchingStrategy { MatchResult score(StudentProfile a,StudentProfile b); }
