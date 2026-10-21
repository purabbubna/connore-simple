import com.connore.dao.*;
import com.connore.service.*;
import com.connore.matching.*;
import com.connore.util.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.sql.SQLException;

/** Runs only on a separate, EMPTY database whose name ends with _test. */
public class IntegrationTest {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS: "+label);}
    interface Action {void run()throws Exception;}
    static void rejected(Action action,String label)throws Exception{try{action.run();throw new AssertionError("Expected rejection: "+label);}catch(ApiException|SQLException expected){System.out.println("PASS (rejected): "+label);}}
    static void script(Path path)throws Exception{
        String sql=Files.readString(path).replaceAll("(?m)^--[^\\n]*","").replaceAll("(?im)^CREATE DATABASE[^;]*;","").replaceAll("(?im)^USE [^;]*;","");
        for(String statement:sql.split(";"))if(!statement.isBlank())Db.exec(statement.trim());
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Usage: java -cp out;lib/* IntegrationTest test-db.properties");
        var cfg=new Properties();try(var in=Files.newInputStream(Path.of(args[0]))){cfg.load(in);}
        if(!cfg.getProperty("db.url","").matches("jdbc:mysql://[^/]+/[a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)*_test(?:\\?.*)?"))throw new IllegalArgumentException("The database name must end with _test.");
        Db.configure(cfg);Class.forName("com.mysql.cj.jdbc.Driver");
        check(Db.integer(Db.query("SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema=DATABASE()").get(0).get("n"))==0,"test database starts empty");
        try{
            script(Path.of("sql/schema.sql"));
            check(Db.integer(Db.query("SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema=DATABASE()").get(0).get("n"))==13,"13 schema tables created");
            for(String table:List.of("account","student_profile","category","interest","community","community_membership","student_interest","social_link","`connection`","pending_request","accepted_connection","closed_request","`block`"))
                check(Db.integer(Db.query("SELECT COUNT(*) AS n FROM "+table).get(0).get("n"))==0,"starts empty: "+table);
            script(Path.of("test/fixtures.sql"));
            var auth=new AuthService("sitpune.edu.in");var profiles=new ProfileService();var community=new CommunityService();var requests=new ConnectionService();
            int a=auth.register("a@sitpune.edu.in","CampusTest123!"),b=auth.register("b@sitpune.edu.in","CampusTest123!"),c=auth.register("c@sitpune.edu.in","CampusTest123!"),d=auth.register("d@sitpune.edu.in","CampusTest123!");
            rejected(()->auth.register("a@sitpune.edu.in","CampusTest123!"),"duplicate email");rejected(()->auth.register("other@gmail.com","CampusTest123!"),"non-college email");rejected(()->auth.login("a@sitpune.edu.in","wrong"),"wrong password");check(auth.login("a@sitpune.edu.in","CampusTest123!").accountId()==a,"correct password");
            check(!new AccountDao().findByEmail("a@sitpune.edu.in").passwordHash().equals("CampusTest123!"),"only password hash stored");
            for(int id:List.of(a,b,c,d)){profiles.saveProfile(id,"Student "+id,"CS",2,"Hello");profiles.addInterest(id,1);}
            profiles.addInterest(a,1);check(profiles.interestIds(a).size()==1,"idempotent interest add");
            check(!profiles.publicProfile(a).containsKey("department")&&!profiles.publicProfile(a).containsKey("yearOfStudy"),"hidden department and year defaults");
            var link=profiles.addSocialLink(a,"GitHub","student-a");check(!Boolean.TRUE.equals(link.get("shared")),"handles default private");rejected(()->profiles.setShared(((Number)link.get("linkId")).intValue(),b,true),"another student cannot change handle visibility");profiles.setShared(((Number)link.get("linkId")).intValue(),a,true);
            community.join(a,1);community.join(a,1);check(Db.integer(Db.query("SELECT COUNT(*) AS n FROM community_membership WHERE student_id=? AND community_id=1",a).get(0).get("n"))==1,"idempotent community join");community.leave(a,1);check(profiles.interestIds(a).size()==1,"leaving preserves interests");
            rejected(()->requests.sendRequest(a,a,null),"self request");var first=requests.sendRequest(a,b,1);int pair=((Number)first.get("connectionId")).intValue();check(first.get("result").equals("PENDING"),"new request pending");
            rejected(()->Db.tx(tx->{Db.exec(tx,"INSERT INTO `connection`(requester_id,receiver_id,status) VALUES(?,?,'PENDING')",b,a);return null;}),"reverse duplicate pair key");
            rejected(()->Db.tx(tx->{Db.exec(tx,"INSERT INTO `connection`(requester_id,receiver_id,status) VALUES(?,?,'INVALID')",a,c);return null;}),"invalid connection status");
            rejected(()->Db.tx(tx->{Db.exec(tx,"INSERT INTO `connection`(requester_id,receiver_id,status) VALUES(?,?,'PENDING')",a,a);return null;}),"same-student pair constraint");
            check(requests.sendRequest(a,b,1).get("result").equals("ALREADY_PENDING"),"same sender idempotent");rejected(()->requests.respond(pair,a,true),"sender cannot respond");rejected(()->requests.respond(pair,c,true),"unrelated account cannot respond");
            requests.respond(pair,b,false);check(!requests.listFor(a,"outgoing").get(0).containsKey("outcome"),"decline reason hidden from sender");check(((Number)requests.sendRequest(b,a,1).get("connectionId")).intValue()==pair,"closed request reopens same row");check(requests.sendRequest(a,b,1).get("result").equals("CONNECTED"),"mirrored requests auto-connect");check(requests.sendRequest(a,b,1).get("result").equals("ALREADY_CONNECTED"),"accepted pair idempotent");
            check(!((List<?>)requests.listFor(b,"accepted").get(0).get("socialLinks")).isEmpty(),"accepted connection sees opted-in handle");requests.block(pair,b);rejected(()->requests.sendRequest(a,b,null),"blocked pair permanently refused");
            var exp=requests.sendRequest(a,c,1);int expId=((Number)exp.get("connectionId")).intValue();Db.exec("UPDATE pending_request SET expires_at=UTC_TIMESTAMP()-INTERVAL 3 DAY WHERE connection_id=?",expId);requests.expireStale();check(Db.text(Db.query("SELECT outcome FROM closed_request WHERE connection_id=?",expId).get(0).get("outcome")).equals("EXPIRED"),"expiry timer transition");check(((Number)requests.sendRequest(c,a,1).get("connectionId")).intValue()==expId,"expired pair reopens same row");
            var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
            try{Future<Map<String,Object>> x=pool.submit(()->{gate.await();return requests.sendRequest(c,d,1);});Future<Map<String,Object>> y=pool.submit(()->{gate.await();return requests.sendRequest(d,c,1);});gate.countDown();var rx=x.get(20,TimeUnit.SECONDS);var ry=y.get(20,TimeUnit.SECONDS);check(rx.get("connectionId").equals(ry.get("connectionId")),"two simultaneous JDBC requests use one row");check(Db.text(Db.query("SELECT status FROM `connection` WHERE connection_id=?",rx.get("connectionId")).get(0).get("status")).equals("ACCEPTED"),"simultaneous mirrored requests accepted");}finally{pool.shutdownNow();}
            var matches=new MatchService(new SharedInterestStrategy());profiles.setPrivacy(d,false,false,false);check(matches.suggestFor(a,50).stream().noneMatch(p->((Number)p.get("studentId")).intValue()==d),"opted-out candidate excluded");
            check(Db.query("SELECT c.connection_id FROM `connection` c LEFT JOIN pending_request p ON p.connection_id=c.connection_id LEFT JOIN accepted_connection a ON a.connection_id=c.connection_id LEFT JOIN closed_request cr ON cr.connection_id=c.connection_id LEFT JOIN `block` b ON b.connection_id=c.connection_id WHERE (p.connection_id IS NOT NULL)+(a.connection_id IS NOT NULL)+(cr.connection_id IS NOT NULL)+(b.connection_id IS NOT NULL)<>1 OR (c.status='PENDING')<>(p.connection_id IS NOT NULL) OR (c.status='ACCEPTED')<>(a.connection_id IS NOT NULL) OR (c.status='CLOSED')<>(cr.connection_id IS NOT NULL) OR (c.status='BLOCKED')<>(b.connection_id IS NOT NULL)").isEmpty(),"exactly one matching subtype per connection");
            System.out.println("All MySQL integration checks passed. The separate test database is retained for review.");
        }finally{Db.close();}
    }
}
