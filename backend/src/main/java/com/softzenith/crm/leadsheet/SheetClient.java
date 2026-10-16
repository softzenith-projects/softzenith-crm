package com.softzenith.crm.leadsheet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Posts one row to a Google Sheet's Apps Script web app (deploy/google-sheet/lead-sheet.gs). Apps Script answers a POST
 * with a redirect to the script's output, so redirects are followed; the script replies {@code {"ok":true}} once the
 * row is in the sheet (or was already there). Retries a few times before giving up.
 */
@Component
class SheetClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Plain http is only accepted for a script running on this machine (tests, local development). */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final int attempts;
    private final Duration pause;

    SheetClient(@Value("${crm.lead-sheet.attempts:3}") int attempts,
                @Value("${crm.lead-sheet.retry-pause:PT3S}") Duration pause) {
        this.attempts = Math.max(1, attempts);
        this.pause = pause;
    }

    record Reply(Boolean ok, String error) {
    }

    /**
     * @param ref identifies the enquiry; the script skips a row whose ref it already has, so a retry never
     *            adds the same enquiry twice
     * @throws IllegalArgumentException if the address is not a usable https address
     * @throws IllegalStateException    if the script did not confirm the row after every attempt
     */
    void append(String url, String secret, List<String> columns, List<String> row, String ref) {
        var request = HttpRequest.newBuilder(checkedUri(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of(
                        "secret", secret == null ? "" : secret, "columns", columns, "row", row, "ref", ref))))
                .build();
        String problem = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            problem = send(request);
            if (problem == null) {
                return;
            }
            if (attempt < attempts) {
                pause();
            }
        }
        throw new IllegalStateException("The sheet did not accept the row after " + attempts + " attempts: " + problem);
    }

    /** @return null when the script confirmed the row, otherwise what went wrong */
    private String send(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var reply = reply(response.body());
            if (response.statusCode() == 200 && reply != null && Boolean.TRUE.equals(reply.ok())) {
                return null;
            }
            return "HTTP " + response.statusCode() + (reply != null && reply.error() != null ? ", " + reply.error() : "");
        } catch (IOException e) {
            return e.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing to the sheet", e);
        }
    }

    private static Reply reply(String body) {
        try {
            return JSON.readValue(body, Reply.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private void pause() {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing to the sheet", e);
        }
    }

    static URI checkedUri(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Lead sheet address is not a valid URL");
        }
        var https = "https".equalsIgnoreCase(uri.getScheme());
        var local = "http".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && LOCAL_HOSTS.contains(uri.getHost());
        if (uri.getHost() == null || !(https || local)) {
            throw new IllegalArgumentException("Lead sheet address must be an https URL");
        }
        return uri;
    }
}
