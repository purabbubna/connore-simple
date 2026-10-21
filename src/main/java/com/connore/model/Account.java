package com.connore.model;
import java.time.Instant;
public record Account(int accountId,String collegeEmail,String passwordHash,boolean emailVerified,Instant createdAt) {}
