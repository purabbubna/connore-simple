package com.connore.model;
import java.util.Map;
public record SocialLink(int linkId,int studentId,String platform,String handle,boolean shared) {
    public Map<String,Object> toMap(){return Map.of("linkId",linkId,"platform",platform,"handle",handle,"shared",shared);}
}
