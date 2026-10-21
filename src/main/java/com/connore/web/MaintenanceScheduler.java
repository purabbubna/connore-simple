package com.connore.web;
import com.connore.service.ConnectionService;
import java.util.concurrent.*;
/** Application lifecycle for request expiry and session cleanup. */
public final class MaintenanceScheduler implements AutoCloseable {
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    public MaintenanceScheduler(ConnectionService service,AuthFilter sessions){
        timer.scheduleWithFixedDelay(()->{try{service.expireStale();}catch(Exception e){System.err.println("Expiry check failed: "+e.getClass().getSimpleName());}sessions.cleanup();},0,2,TimeUnit.MINUTES);
    }
    public void close(){timer.shutdownNow();}
}
