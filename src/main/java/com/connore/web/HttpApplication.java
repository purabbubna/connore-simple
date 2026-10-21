package com.connore.web;

import com.connore.matching.SharedInterestStrategy;
import com.connore.service.*;
import com.connore.util.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP routes, static files and background maintenance for the standalone application. */
public final class HttpApplication implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 65536;
    private static final List<String> HTML_ALIASES = List.of(
        "/home.html", "/profile.html", "/communities.html", "/community.html", "/login.html", "/register.html");

    private final HttpServer server;
    private final ExecutorService workers = Executors.newFixedThreadPool(12);
    private final MaintenanceScheduler maintenance;
    private final AuthFilter sessions;
    private final Path webRoot;
    private final String collegeDomain;
    private final Endpoint auth, profile, matches, connections, communities;

    public HttpApplication(Properties config) throws IOException {
        collegeDomain = config.getProperty("college.domain");
        webRoot = Path.of("dist").toRealPath();
        var profileService = new ProfileService();
        var connectionService = new ConnectionService();
        sessions = new AuthFilter(Long.parseLong(config.getProperty("session.minutes")));
        auth = new AuthEndpoint(new AuthService(collegeDomain), profileService, sessions);
        profile = new ProfileEndpoint(profileService);
        matches = new MatchEndpoint(new MatchService(new SharedInterestStrategy()), connectionService);
        connections = new ConnectionEndpoint(connectionService);
        communities = new CommunityEndpoint(new CommunityService());

        server = HttpServer.create(new InetSocketAddress(config.getProperty("server.host"),
            Integer.parseInt(config.getProperty("server.port"))), 0);
        server.setExecutor(workers);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        maintenance = new MaintenanceScheduler(connectionService, sessions);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    private Endpoint endpoint(String path) {
        if (path.startsWith("/auth/") || path.equals("/session")) return auth;
        if (path.startsWith("/profile") || path.equals("/interests")) return profile;
        if (path.equals("/matches")) return matches;
        if (path.startsWith("/connections")) return connections;
        if (path.startsWith("/communities")) return communities;
        throw new ApiException(404, "NOT_FOUND");
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            int student = sessions.authorize(exchange);
            Map<String, Object> body = Map.of();
            if (exchange.getRequestMethod().equals("POST") || exchange.getRequestMethod().equals("PUT")) {
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                ApiException.require(type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json"), 415, "JSON_REQUIRED");
                byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
                ApiException.require(bytes.length <= MAX_BODY_BYTES, 413, "REQUEST_TOO_LARGE");
                body = Json.object(new String(bytes, StandardCharsets.UTF_8));
            }
            var request = new Request(exchange, student, body);
            Reply reply = endpoint(request.path()).handle(request);
            json(exchange, reply.status(), reply.body());
        } catch (ApiException exception) {
            json(exchange, exception.status, Map.of("error", exception.code));
        } catch (Exception exception) {
            System.err.println("Request failed: " + exception.getClass().getSimpleName());
            json(exchange, 500, Map.of("error", "SERVER_ERROR"));
        } finally { exchange.close(); }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        try {
            securityHeaders(exchange);
            if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/js/config.js")) {
                byte[] config = ("window.CONNORE_CONFIG=" + Json.stringify(Map.of("collegeDomain", collegeDomain)) + ";")
                    .getBytes(StandardCharsets.UTF_8);
                send(exchange, "text/javascript; charset=utf-8", config);
                return;
            }
            if (path.equals("/") || HTML_ALIASES.contains(path)) path = "/index.html";
            Path file = webRoot.resolve(path.substring(1)).normalize();
            if (!file.startsWith(webRoot) || !Files.isRegularFile(file) || !file.toRealPath().startsWith(webRoot)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            send(exchange, contentType(file.getFileName().toString()), Files.readAllBytes(file));
        } catch (Exception exception) {
            if (exchange.getResponseCode() < 0) exchange.sendResponseHeaders(500, -1);
        } finally { exchange.close(); }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static void securityHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "same-origin");
        headers.set("Cache-Control", "no-store");
        headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
    }

    private static void send(HttpExchange exchange, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static void json(HttpExchange exchange, int status, Object body) throws IOException {
        securityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (status == 204) { exchange.sendResponseHeaders(204, -1);return; }
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public void close() {
        server.stop(1);
        maintenance.close();
        workers.shutdownNow();
        Db.close();
    }
}
