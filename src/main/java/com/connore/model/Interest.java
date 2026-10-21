package com.connore.model;
import java.util.Map;
public record Interest(int interestId,String name,int categoryId,boolean approved) {
    public Map<String,Object> toMap(){return Map.of("interestId",interestId,"name",name);}
}
