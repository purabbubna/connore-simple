package com.connore.matching;
import com.connore.model.*;
import java.util.*;
public record MatchResult(StudentProfile candidate,double score,List<Interest> sharedInterests,String reason) {
    public Map<String,Object> publicReply(){var m=candidate.toMap(false);m.put("sharedInterests",sharedInterests.stream().map(Interest::toMap).toList());m.put("reason",reason);return m;}
}
