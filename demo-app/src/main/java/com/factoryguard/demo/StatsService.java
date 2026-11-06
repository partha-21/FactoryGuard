package com.factoryguard.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps a sliding window of "message processed" timestamps to compute events per minute. */
@Service
public class StatsService {

    private final Deque<Long> processedAt = new ArrayDeque<>();
    private long total = 0;
    /** When the app first connected to the broker (0 = not yet). Throughput is measured over the time since then, up to the window. */
    private long connectedAtMillis = 0;
    private static final double MIN_WINDOW_SECONDS = 5.0;

    @Value("${app.version}")
    private String version;

    @Value("${app.bug-mode}")
    private boolean bugMode;

    @Value("${app.window-seconds}")
    private int windowSeconds;

    public synchronized void markConnected() {
        if (connectedAtMillis == 0) {
            connectedAtMillis = System.currentTimeMillis();
        }
    }

    public synchronized void recordProcessed() {
        processedAt.addLast(System.currentTimeMillis());
        total++;
        trim();
    }

    private void trim() {
        long cutoff = System.currentTimeMillis() - windowSeconds * 1000L;
        while (!processedAt.isEmpty() && processedAt.peekFirst() < cutoff) {
            processedAt.removeFirst();
        }
    }

    public synchronized Map<String, Object> snapshot() {
        trim();
        double eventsPerMinute = 0;
        if (connectedAtMillis > 0) {
            // A freshly started app has not had a full window yet; do not call that a slowdown.
            double elapsed = (System.currentTimeMillis() - connectedAtMillis) / 1000.0;
            double effectiveWindow = Math.min(windowSeconds, Math.max(MIN_WINDOW_SECONDS, elapsed));
            eventsPerMinute = processedAt.size() * 60.0 / effectiveWindow;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", version);
        m.put("bugMode", bugMode);
        m.put("windowSeconds", windowSeconds);
        m.put("processedTotal", total);
        m.put("eventsPerMinute", Math.round(eventsPerMinute * 10) / 10.0);
        return m;
    }
}
