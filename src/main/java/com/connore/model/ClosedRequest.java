package com.connore.model;
import java.time.Instant;
public final class ClosedRequest extends Connection {
    private final String outcome;private final Instant closedAt;
    public ClosedRequest(int id,int from,int to,Integer interest,Instant created,String outcome,Instant closed){super(id,from,to,interest,created);this.outcome=outcome;closedAt=closed;}
    public String outcome(){return outcome;}public Instant closedAt(){return closedAt;}
    public String status(){return "CLOSED";}
    public PendingRequest reopen(int requesterId,Integer interestId){int receiver=requesterId==requesterId()?receiverId():requesterId();return new PendingRequest(connectionId(),requesterId,receiver,interestId,Instant.now(),Instant.now().plusSeconds(172800));}
}
