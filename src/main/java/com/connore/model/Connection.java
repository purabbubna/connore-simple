package com.connore.model;
import java.time.Instant;
public abstract class Connection {
    private final int connectionId,requesterId,receiverId;
    private final Integer contextInterestId;
    private final Instant createdAt;
    protected Connection(int id,int from,int to,Integer context,Instant created){connectionId=id;requesterId=from;receiverId=to;contextInterestId=context;createdAt=created;}
    public int connectionId(){return connectionId;}
    public int requesterId(){return requesterId;}
    public int receiverId(){return receiverId;}
    public Integer contextInterestId(){return contextInterestId;}
    public Instant createdAt(){return createdAt;}
    public boolean includes(int studentId){return requesterId==studentId||receiverId==studentId;}
    public abstract String status();
}
