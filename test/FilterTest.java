import com.connore.web.*;
import com.connore.util.ApiException;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class FilterTest {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    static void denied(AuthFilter filter,Exchange x,int status){try{filter.authorize(x);throw new AssertionError("Expected HTTP "+status);}catch(ApiException e){check(e.status==status,"authorization status");}}
    public static void main(String[] args){
        var filter=new AuthFilter(30);var loggedOut=new Exchange("GET","/api/profile");denied(filter,loggedOut,401);
        var login=new Exchange("POST","/api/auth/login");denied(filter,login,403);login.request.set("X-Connore-Request","1");check(filter.authorize(login)==0,"public login route");filter.create(login,42);String cookie=login.response.getFirst("Set-Cookie");check(cookie.startsWith("CONNORE_SIMPLE_SESSION="),"Simple version uses a separate cookie");check(cookie.contains("HttpOnly")&&cookie.contains("SameSite=Strict"),"session cookie protections");
        var originalCookie=new Exchange("GET","/api/profile");originalCookie.request.set("Cookie",cookie.replace("CONNORE_SIMPLE_SESSION=","CONNORE_SESSION="));denied(filter,originalCookie,401);
        var profile=new Exchange("GET","/api/profile");profile.request.set("Cookie",cookie);check(filter.authorize(profile)==42,"session owns student id");var r=new Request(profile,42,Map.of("studentId",999));check(r.studentId()==42,"request body cannot override session identity");
        var forged=new Exchange("POST","/api/connections");forged.request.set("Cookie",cookie);denied(filter,forged,403);forged.request.set("X-Connore-Request","1");forged.request.set("Origin","https://unrelated.example");denied(filter,forged,403);forged.request.set("Origin","http://localhost:8081");check(filter.authorize(forged)==42,"same-origin protected mutation");
        filter.invalidate(profile);denied(filter,profile,401);System.out.println("Java authorization checks passed: 401, session identity, CSRF headers, logout.");
    }
    static final class Exchange extends HttpExchange {
        final Headers request=new Headers(),response=new Headers();final String method;final URI uri;int status=-1;
        Exchange(String method,String path){this.method=method;uri=URI.create(path);request.set("Host","localhost:8081");}
        public Headers getRequestHeaders(){return request;}public Headers getResponseHeaders(){return response;}
        public URI getRequestURI(){return uri;}public String getRequestMethod(){return method;}public HttpContext getHttpContext(){return null;}public void close(){}
        public InputStream getRequestBody(){return new ByteArrayInputStream(new byte[0]);}public OutputStream getResponseBody(){return new ByteArrayOutputStream();}
        public void sendResponseHeaders(int code,long length){status=code;}public InetSocketAddress getRemoteAddress(){return new InetSocketAddress("127.0.0.1",8081);}public int getResponseCode(){return status;}
        public InetSocketAddress getLocalAddress(){return new InetSocketAddress("127.0.0.1",8081);}public String getProtocol(){return "HTTP/1.1";}public Object getAttribute(String name){return null;}public void setAttribute(String name,Object value){}public void setStreams(InputStream i,OutputStream o){}public HttpPrincipal getPrincipal(){return null;}
    }
}
