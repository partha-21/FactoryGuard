package com.factoryguard.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends FactoryGuard's key numbers to Amazon CloudWatch so AWS alarms can watch them too.
 * Does nothing unless GUARD_CLOUDWATCH_ENABLED=true (the AWS deployment turns it on).
 * It runs in the background and can never slow down or break monitoring.
 *
 * Metrics (namespace "FactoryGuard", dimension Service=demo-app):
 *   EventsPerMinute     sensor throughput of the demo app
 *   HealthCheckPassing  1 if the app's health page answered, otherwise 0
 *   IncidentsDetected   1 each time FactoryGuard declares an incident
 */
@Component
public class CloudWatchPublisher {

    private static final Logger log = LoggerFactory.getLogger(CloudWatchPublisher.class);
    private static final String NAMESPACE = "FactoryGuard";

    private final boolean enabled;
    private final String region;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cloudwatch-publisher");
        t.setDaemon(true);
        return t;
    });
    private volatile CloudWatchClient client;
    private volatile long lastWarning = 0;

    public CloudWatchPublisher(@Value("${guard.cloudwatch-enabled:false}") boolean enabled,
                               @Value("${aws.region:}") String region) {
        this.enabled = enabled && region != null && !region.isBlank();
        this.region = region;
        if (enabled && !this.enabled) {
            log.warn("CloudWatch publishing is on but AWS_REGION is not set, so it stays off.");
        }
        if (this.enabled) {
            log.info("Publishing metrics to Amazon CloudWatch in {}", region);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void publishSample(Sample sample) {
        if (!enabled) {
            return;
        }
        send("EventsPerMinute", sample.eventsPerMinute(), StandardUnit.COUNT);
        send("HealthCheckPassing", sample.healthUp() ? 1 : 0, StandardUnit.COUNT);
    }

    public void publishIncident() {
        if (!enabled) {
            return;
        }
        send("IncidentsDetected", 1, StandardUnit.COUNT);
    }

    private void send(String name, double value, StandardUnit unit) {
        executor.submit(() -> {
            try {
                MetricDatum datum = MetricDatum.builder()
                        .metricName(name)
                        .value(value)
                        .unit(unit)
                        .timestamp(Instant.now())
                        .dimensions(Dimension.builder().name("Service").value("demo-app").build())
                        .build();
                client().putMetricData(PutMetricDataRequest.builder()
                        .namespace(NAMESPACE)
                        .metricData(datum)
                        .build());
            } catch (Exception e) {
                warnOncePerMinute(e);
            }
        });
    }

    private CloudWatchClient client() {
        CloudWatchClient c = client;
        if (c == null) {
            c = CloudWatchClient.builder()
                    .region(Region.of(region))
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .apiCallTimeout(Duration.ofSeconds(5))
                            .build())
                    .build();
            client = c;
        }
        return c;
    }

    private void warnOncePerMinute(Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastWarning > 60_000) {
            lastWarning = now;
            log.warn("Could not publish to CloudWatch (monitoring continues): {}", e.getMessage());
        }
    }
}
