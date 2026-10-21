package com.connore.model;
import java.util.*;
public record StudentProfile(int studentId,String fullName,String department,Integer yearOfStudy,String bio,boolean showDepartment,boolean showYear,boolean suggestible,List<Interest> interests) {
    public Map<String,Object> toMap(boolean owner){
        var m=new LinkedHashMap<String,Object>();m.put("studentId",studentId);m.put("fullName",fullName);m.put("bio",bio);
        if(owner||showDepartment)m.put("department",department);
        if(owner||showYear)m.put("yearOfStudy",yearOfStudy);
        if(owner){m.put("showDepartment",showDepartment);m.put("showYear",showYear);m.put("suggestible",suggestible);}
        m.put("interests",interests.stream().map(Interest::toMap).toList());return m;
    }
    public StudentProfile withInterests(List<Interest> list){return new StudentProfile(studentId,fullName,department,yearOfStudy,bio,showDepartment,showYear,suggestible,list);}
}
