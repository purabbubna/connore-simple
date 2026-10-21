package com.connore.matching;
import com.connore.model.*;
import java.util.stream.Collectors;
public final class SharedInterestStrategy implements MatchingStrategy {
    public MatchResult score(StudentProfile a,StudentProfile b){
        var ids=a.interests().stream().map(Interest::interestId).collect(Collectors.toSet());var shared=b.interests().stream().filter(i->ids.contains(i.interestId())).toList();
        return new MatchResult(b,shared.size(),shared,"You both enjoy "+shared.stream().map(Interest::name).collect(Collectors.joining(", ")));
    }
}
