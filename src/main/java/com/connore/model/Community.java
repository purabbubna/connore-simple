package com.connore.model;
import java.time.Instant;
public record Community(int communityId,int interestId,String description,Instant createdAt) {}
