package com.connore.model;
import java.time.Instant;
public final class PendingRequest extends Connection {
    private final Instant expiresAt;
    public PendingRequest(int id,int from,int to,Integer interest,Instant created,Instant expires){super(id,from,to,interest,created);expiresAt=expires;}
    public Instant expiresAt(){return expiresAt;}
    public String status(){return "PENDING";}
    public AcceptedConnection accept(){return new AcceptedConnection(connectionId(),requesterId(),receiverId(),contextInterestId(),createdAt(),Instant.now());}
    public ClosedRequest decline(){return close("DECLINED");}
    public ClosedRequest expire(){return close("EXPIRED");}
    private ClosedRequest close(String outcome){return new ClosedRequest(connectionId(),requesterId(),receiverId(),contextInterestId(),createdAt(),outcome,Instant.now());}
    public Block block(int studentId){return new Block(connectionId(),requesterId(),receiverId(),contextInterestId(),createdAt(),studentId,Instant.now());}
}
