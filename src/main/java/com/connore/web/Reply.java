package com.connore.web;
import java.util.Map;
public record Reply(int status,Object body) {
    public static Reply ok(Object body){return new Reply(200,body);}
    public static Reply ok(){return ok(Map.of());}
    public static Reply empty(){return new Reply(204,null);}
}
