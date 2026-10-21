package com.connore.model;
import java.util.List;
public record Category(int categoryId,String name,List<Interest> interests) {}
