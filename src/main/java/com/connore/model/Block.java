package com.connore.model;
import java.time.Instant;
public final class Block extends Connection {
    private final int blockedBy;private final Instant blockedAt;
    public Block(int id,int from,int to,Integer interest,Instant created,int by,Instant blocked){super(id,from,to,interest,created);blockedBy=by;blockedAt=blocked;}
    public int blockedBy(){return blockedBy;}public Instant blockedAt(){return blockedAt;}
    public String status(){return "BLOCKED";}
}
