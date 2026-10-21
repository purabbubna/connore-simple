package com.connore.web;
import com.sun.net.httpserver.HttpExchange;
import com.connore.util.*;
import java.util.*;
import static com.connore.util.ApiException.require;
public record Request(HttpExchange exchange,int studentId,Map<String,Object> body) {
    public String method(){return exchange.getRequestMethod();}
    public String path(){return exchange.getRequestURI().getPath().substring(4);}
    public String string(String key){Object value=body.get(key);require(value==null||value instanceof String,400,"INVALID_INPUT");return value==null?"":(String)value;}
    public boolean bool(String key){Object value=body.get(key);require(value instanceof Boolean,400,"INVALID_INPUT");return (Boolean)value;}
    public Integer optionalInt(String key){Object v=body.get(key);if(v==null)return null;require(v instanceof Number,400,"INVALID_INPUT");double d=((Number)v).doubleValue();require(Double.isFinite(d)&&d>=1&&d<=Integer.MAX_VALUE&&d==Math.rint(d),400,"INVALID_INPUT");return ((Number)v).intValue();}
    public int integer(String key){Integer n=optionalInt(key);require(n!=null,400,"INVALID_INPUT");return n;}
    public String query(String key,String defaultValue){String query=exchange.getRequestURI().getRawQuery();if(query!=null)for(String pair:query.split("&")){String[] p=pair.split("=",2);if(p[0].equals(key))try{return java.net.URLDecoder.decode(p.length>1?p[1]:"",java.nio.charset.StandardCharsets.UTF_8);}catch(IllegalArgumentException e){throw new ApiException(400,"INVALID_INPUT");}}return defaultValue;}
    public int queryInt(String key,int fallback){try{return Integer.parseInt(query(key,String.valueOf(fallback)));}catch(NumberFormatException e){throw new ApiException(400,"INVALID_INPUT");}}
    public static int pathId(String segment){try{int id=Integer.parseInt(segment);require(id>0,400,"INVALID_INPUT");return id;}catch(NumberFormatException e){throw new ApiException(400,"INVALID_INPUT");}}
}
