package com.factoryguard.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Asks the demo app two questions:
 *   1. Is your health page OK?          (/actuator/health)
 *   2. How many sensor events per min?  (/api/stats)
 */
@Component
public class Prober {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final GuardConfig cfg;

    public Prober(GuardConfig cfg) {
        this.cfg = cfg;
    }

    public Sample probe(String version) {
        String base = "http://" + cfg.containerName() + ":" + cfg.demoPort();
        boolean up = false;
        double eventsPerMinute = 0;
        try {
            HttpResponse<String> r = get(base + "/actuator/health");
            up = r.statusCode() == 200 && "UP".equals(mapper.readTree(r.body()).path("status").asText());
        } catch (Exception ignored) {
            // app unreachable = not healthy
        }
        if (up) {
            try {
                HttpResponse<String> r = get(base + "/api/stats");
                eventsPerMinute = mapper.readTree(r.body()).path("eventsPerMinute").asDouble(0);
            } catch (Exception ignored) {
                // leave at 0
            }
        }
        return new Sample(System.currentTimeMillis(), version, up, eventsPerMinute);
    }

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
