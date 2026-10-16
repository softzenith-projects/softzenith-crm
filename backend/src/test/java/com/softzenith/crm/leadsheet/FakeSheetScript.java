package com.softzenith.crm.leadsheet;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Stands in for the Google Sheet's Apps Script web app: records each POST body, answers with a redirect to the
 * output (as Apps Script does), and serves {@link #reply} with {@link #status} there.
 */
final class FakeSheetScript implements AutoCloseable {

    final List<String> posts = new CopyOnWriteArrayList<>();
    volatile String reply = "{\"ok\":true}";
    volatile int status = 200;
    volatile Duration delay = Duration.ZERO;

    private final HttpServer server;

    FakeSheetScript() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/exec", exchange -> {
            posts.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.getResponseHeaders().add("Location", "/out");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/out", exchange -> {
            var body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort() + "/exec";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
