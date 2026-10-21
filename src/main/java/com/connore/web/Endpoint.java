package com.connore.web;
import java.sql.SQLException;
/** Feature adapter; uses JDK HttpServer instead of Jakarta/Tomcat. */
public interface Endpoint { Reply handle(Request request)throws SQLException; }
