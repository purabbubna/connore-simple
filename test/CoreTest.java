import com.connore.model.*;
import com.connore.matching.*;
import com.connore.util.*;
import java.time.Instant;
import java.util.*;

public class CoreTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        var hasher=new PasswordHasher();String a=hasher.hash("campus-test-pass"),b=hasher.hash("campus-test-pass");
        check(!a.equals(b),"per-account salts");check(hasher.verify("campus-test-pass",a),"correct password");check(!hasher.verify("wrong",a),"wrong password");check(!hasher.verify("x","bad-hash"),"invalid hash");
        String json="{\"name\":\"Purab \\u263a\",\"value\":5,\"flags\":[true,false,null],\"text\":\"line\\nquote\\\"\"}";var parsed=Json.object(json);check(Json.stringify(parsed).contains("Purab"),"JSON roundtrip");check(Json.object(Json.stringify(parsed)).equals(parsed),"JSON data preserved");
        for(String bad:List.of("{\"a\":1,\"a\":2}","[1,]","{\"a\":}","{\"a\":01}","true false","{\"x\":\"bad\nline\"}")){try{Json.parse(bad);throw new AssertionError("Accepted invalid JSON: "+bad);}catch(ApiException expected){}}
        var p=new PendingRequest(8,1,2,3,Instant.now(),Instant.now().plusSeconds(172800));check(p.accept().status().equals("ACCEPTED"),"accept transition");check(p.decline().outcome().equals("DECLINED"),"decline transition");check(p.expire().outcome().equals("EXPIRED"),"expiry transition");check(p.block(2).blockedBy()==2,"block transition");var reopened=p.decline().reopen(2,4);check(reopened.connectionId()==8&&reopened.requesterId()==2&&reopened.receiverId()==1&&reopened.contextInterestId()==4,"reopen same row, reversed sender");
        var i=new Interest(1,"Photography",1,true);var me=new StudentProfile(1,"A","CS",2,"Hello",false,false,true,List.of(i));var other=new StudentProfile(2,"B","IT",1,"Hi",false,false,true,List.of(i));var result=new SharedInterestStrategy().score(me,other);check(result.score()==1&&result.sharedInterests().size()==1,"shared-interest strategy");check(!result.publicReply().containsKey("score"),"score never sent to browser");check(!other.toMap(false).containsKey("department")&&!other.toMap(false).containsKey("yearOfStudy"),"privacy defaults");check(other.toMap(true).containsKey("department"),"owner sees own fields");
        System.out.println("Java core checks passed: password hashing, JSON, transitions, privacy, matching.");
    }
}
