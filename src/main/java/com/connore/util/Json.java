package com.connore.util;

import java.util.*;

/** Small JSON codec for objects, arrays, strings, booleans, null and numbers. */
public final class Json {
    private Json() {}
    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?,?> map) {
            var parts=new ArrayList<String>();
            map.forEach((k,v)->parts.add(stringify(k.toString())+":"+stringify(v)));
            return "{"+String.join(",",parts)+"}";
        }
        if (value instanceof Iterable<?> list) {
            var parts=new ArrayList<String>(); list.forEach(v->parts.add(stringify(v)));
            return "["+String.join(",",parts)+"]";
        }
        var out=new StringBuilder("\"");
        for(char c:value.toString().toCharArray()) {
            switch(c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if(c<32) out.append(String.format("\\u%04x",(int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
    public static Object parse(String source) {
        var parser=new Parser(source); Object result=parser.value(0);parser.ws();
        if(parser.pos!=source.length()) throw new ApiException(400,"INVALID_JSON");
        return result;
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> object(String source) {
        Object result=parse(source);
        if(!(result instanceof Map)) throw new ApiException(400,"INVALID_JSON");
        return (Map<String,Object>)result;
    }
    private static final class Parser {
        final String s; int pos=0;
        Parser(String s){this.s=s;}
        void ws(){while(pos<s.length()&&" \n\r\t".indexOf(s.charAt(pos))>=0)pos++;}
        ApiException invalid(){return new ApiException(400,"INVALID_JSON");}
        char take(){if(pos>=s.length())throw invalid();return s.charAt(pos++);}
        Object value(int depth){
            if(depth>30)throw invalid();ws();if(pos>=s.length())throw invalid();char c=s.charAt(pos);
            if(c=='"')return string();
            if(c=='{'){pos++;var map=new LinkedHashMap<String,Object>();ws();if(pos<s.length()&&s.charAt(pos)=='}'){pos++;return map;}
                while(true){ws();if(pos>=s.length()||s.charAt(pos)!='"')throw invalid();String key=string();ws();if(take()!=':')throw invalid();if(map.containsKey(key))throw invalid();map.put(key,value(depth+1));ws();char end=take();if(end=='}')return map;if(end!=',')throw invalid();}}
            if(c=='['){pos++;var list=new ArrayList<Object>();ws();if(pos<s.length()&&s.charAt(pos)==']'){pos++;return list;}
                while(true){list.add(value(depth+1));ws();char end=take();if(end==']')return list;if(end!=',')throw invalid();}}
            for(String token:List.of("true","false","null"))if(s.startsWith(token,pos)){pos+=token.length();return token.equals("null")?null:Boolean.valueOf(token);}
            int start=pos;while(pos<s.length()&&"-+0123456789.eE".indexOf(s.charAt(pos))>=0)pos++;
            String number=s.substring(start,pos);if(!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw invalid();
            try{if(number.contains(".")||number.contains("e")||number.contains("E"))return Double.valueOf(number);return Long.valueOf(number);}catch(NumberFormatException e){throw invalid();}
        }
        String string(){
            if(take()!='"')throw invalid();var out=new StringBuilder();
            while(true){char c=take();if(c=='"')return out.toString();if(c<32)throw invalid();if(c!='\\'){out.append(c);continue;}
                char escaped=take();switch(escaped){
                    case '"','\\','/' -> out.append(escaped);
                    case 'b' -> out.append('\b');case 'f' -> out.append('\f');case 'n' -> out.append('\n');case 'r' -> out.append('\r');case 't' -> out.append('\t');
                    case 'u' -> {if(pos+4>s.length())throw invalid();try{out.append((char)Integer.parseInt(s.substring(pos,pos+4),16));pos+=4;}catch(NumberFormatException e){throw invalid();}}
                    default -> throw invalid();
                }
            }
        }
    }
}
