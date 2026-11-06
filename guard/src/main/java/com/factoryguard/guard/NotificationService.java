package com.factoryguard.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Alerts the team: always writes to the log, and posts to a webhook if one is configured. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final HttpClient http = HttpClient.newHttpClient();
    private final GuardConfig cfg;

    public NotificationService(GuardConfig cfg) {
        this.cfg = cfg;
    }

    public void alert(String message) {
        log.warn("ALERT: {}", message);
        String url = cfg.webhookUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            String safe = message.replace("\\", "\\\\").replace("\"", "'");
            String body = "{\"text\":\"" + safe + "\",\"content\":\"" + safe + "\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Could not send webhook: {}", e.getMessage());
        }
    }
}
