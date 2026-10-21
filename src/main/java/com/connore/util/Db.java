package com.connore.util;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded JDBC pool. All SQL uses prepared statements. */
public final class Db {
    private static final BlockingQueue<Connection> POOL=new ArrayBlockingQueue<>(8);
    private static Properties config;
    private static int created=0;
    private Db(){}
    public static void configure(Properties properties){config=properties;}
    @FunctionalInterface public interface Work<T>{T run(Connection connection)throws SQLException;}
    private static Connection borrow() throws SQLException {
        Connection c;while((c=POOL.poll())!=null){if(c.isValid(2))return c;try{c.close();}catch(SQLException ignored){}synchronized(Db.class){created--;}}
        synchronized(Db.class){if(created<8){Connection n=DriverManager.getConnection(config.getProperty("db.url"),config.getProperty("db.user"),config.getProperty("db.password"));try(var stmt=n.createStatement()){stmt.execute("SET time_zone='+00:00'");}catch(SQLException e){n.close();throw e;}created++;return n;}}
        try{c=POOL.poll(10,TimeUnit.SECONDS);if(c==null)throw new SQLException("Database pool timeout");if(!c.isValid(2)){synchronized(Db.class){created--;}c.close();return borrow();}return c;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new SQLException(e);}
    }
    public static <T>T use(Work<T> work)throws SQLException{
        Connection c=borrow();try{return work.run(c);}finally{release(c);}
    }
    private static void release(Connection c){try{if(c.isClosed()) {synchronized(Db.class){created--;}return;}if(!c.getAutoCommit()){c.rollback();c.setAutoCommit(true);}if(!POOL.offer(c)){c.close();synchronized(Db.class){created--;}}}catch(SQLException e){try{c.close();}catch(SQLException ignored){}synchronized(Db.class){created--;}}}
    public static <T>T tx(Work<T> work)throws SQLException{
        return use(c->{c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);try{T result=work.run(c);c.commit();return result;}catch(SQLException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}});
    }
    private static PreparedStatement statement(Connection c,String sql,Object...args)throws SQLException{
        var ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;
    }
    public static List<Map<String,Object>> query(Connection c,String sql,Object...args)throws SQLException{
        try(var ps=statement(c,sql,args);var rs=ps.executeQuery()){
            var rows=new ArrayList<Map<String,Object>>();var md=rs.getMetaData();while(rs.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=md.getColumnCount();i++)row.put(md.getColumnLabel(i),rs.getObject(i));rows.add(row);}return rows;
        }
    }
    public static List<Map<String,Object>> query(String sql,Object...args)throws SQLException{return use(c->query(c,sql,args));}
    public static int exec(Connection c,String sql,Object...args)throws SQLException{try(var ps=statement(c,sql,args)){return ps.executeUpdate();}}
    public static int exec(String sql,Object...args)throws SQLException{return use(c->exec(c,sql,args));}
    public static int insert(Connection c,String sql,Object...args)throws SQLException{try(var ps=statement(c,sql,args)){ps.executeUpdate();try(var keys=ps.getGeneratedKeys()){if(!keys.next())throw new SQLException("Missing generated id");return keys.getInt(1);}}}
    public static int insert(String sql,Object...args)throws SQLException{return use(c->insert(c,sql,args));}
    public static int integer(Object value){return value==null?0:((Number)value).intValue();}
    public static boolean bool(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
    public static String text(Object value){return value==null?"":value.toString();}
    public static java.time.Instant instant(Object value){
        if(value instanceof Timestamp t)return t.toInstant();
        if(value instanceof java.time.LocalDateTime t)return t.toInstant(java.time.ZoneOffset.UTC);
        if(value instanceof java.time.Instant t)return t;
        return java.time.Instant.parse(value.toString());
    }
    public static void close(){Connection c;while((c=POOL.poll())!=null)try{c.close();}catch(SQLException ignored){}}
}
