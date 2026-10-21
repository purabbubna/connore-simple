package com.connore.model;
import java.time.Instant;
public final class AcceptedConnection extends Connection {
    private final Instant connectedAt;
    public AcceptedConnection(int id,int from,int to,Integer interest,Instant created,Instant connected){super(id,from,to,interest,created);connectedAt=connected;}
    public Instant connectedAt(){return connectedAt;}
    public String status(){return "ACCEPTED";}
    public Block block(int studentId){return new Block(connectionId(),requesterId(),receiverId(),contextInterestId(),createdAt(),studentId,Instant.now());}
}
