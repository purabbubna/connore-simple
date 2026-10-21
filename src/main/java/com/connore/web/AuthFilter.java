package com.connore.web;

import com.sun.net.httpserver.HttpExchange;
import com.connore.util.ApiException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.connore.util.ApiException.require;

/** JDK HttpServer session gate; replaces container servlet filtering. */
public final class AuthFilter {
    // Cookies are scoped by host, not port; keep both local Connore versions usable.
    private static final String COOKIE_NAME = "CONNORE_SIMPLE_SESSION";
    private record Session(int studentId,Instant expiresAt){}
    private record Attempt(int count,Instant expiresAt){}
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final Map<String,Attempt> attempts=new ConcurrentHashMap<>();
    private final long timeoutSeconds;
    private final SecureRandom random=new SecureRandom();
    public AuthFilter(long minutes){timeoutSeconds=minutes*60;}
    private String token(HttpExchange x){
        String cookies=x.getRequestHeaders().getFirst("Cookie");if(cookies!=null)for(String pair:cookies.split(";")){String[] p=pair.trim().split("=",2);if(p.length==2&&p[0].equals(COOKIE_NAME))return p[1];}return null;
    }
    public int authorize(HttpExchange x){
        String path=x.getRequestURI().getPath(),method=x.getRequestMethod();
        if(!method.equals("GET")){
            require("1".equals(x.getRequestHeaders().getFirst("X-Connore-Request")),403,"FORBIDDEN");
            String origin=x.getRequestHeaders().getFirst("Origin"),host=x.getRequestHeaders().getFirst("Host");
            require(origin==null||origin.equals("http://"+host)||origin.equals("https://"+host),403,"FORBIDDEN");
        }
        if(method.equals("POST")&&(path.equals("/api/auth/login")||path.equals("/api/auth/register"))){
            String ip=x.getRemoteAddress().getAddress().getHostAddress();Instant now=Instant.now();
            Attempt a=attempts.compute(ip,(k,old)->old==null||old.expiresAt().isBefore(now)?new Attempt(1,now.plusSeconds(600)):new Attempt(old.count()+1,old.expiresAt()));
            require(a.count()<=30,429,"TOO_MANY_ATTEMPTS");return 0;
        }
        String token=token(x);Session s=token==null?null:sessions.get(token);require(s!=null&&s.expiresAt().isAfter(Instant.now()),401,"UNAUTHENTICATED");
        sessions.replace(token,s,new Session(s.studentId(),Instant.now().plusSeconds(timeoutSeconds)));return s.studentId();
    }
    public void create(HttpExchange x,int studentId){
        String old=token(x);if(old!=null)sessions.remove(old);byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token,new Session(studentId,Instant.now().plusSeconds(timeoutSeconds)));x.getResponseHeaders().add("Set-Cookie",COOKIE_NAME+"="+token+"; Path=/; HttpOnly; SameSite=Strict");
    }
    public void invalidate(HttpExchange x){String token=token(x);if(token!=null)sessions.remove(token);x.getResponseHeaders().add("Set-Cookie",COOKIE_NAME+"=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");}
    public void cleanup(){Instant now=Instant.now();sessions.entrySet().removeIf(e->!e.getValue().expiresAt().isAfter(now));attempts.entrySet().removeIf(e->!e.getValue().expiresAt().isAfter(now));}
}
