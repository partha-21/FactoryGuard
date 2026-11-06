package com.factoryguard.simulator;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Pretends to be factory machines. Publishes one sensor reading at a steady rate
 * (default 100 per minute) to the MQTT broker.
 */
@SpringBootApplication
public class SimulatorApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorApplication.class);

    @Value("${mqtt.host}")
    private String host;

    @Value("${mqtt.topic}")
    private String topic;

    @Value("${sim.messages-per-minute}")
    private int messagesPerMinute;

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        MqttClient client = connectWithRetry();
        long intervalNanos = 60_000_000_000L / Math.max(1, messagesPerMinute);
        long next = System.nanoTime();
        Random random = new Random();
        long sent = 0;
        log.info("Publishing {} messages/min to topic {}", messagesPerMinute, topic);

        while (true) {
            String machine = "M-" + (1 + random.nextInt(5));
            double temperature = 60 + random.nextDouble() * 25;
            String json = String.format(Locale.US,
                    "{\"machineId\":\"%s\",\"temperature\":%.1f,\"ts\":%d}",
                    machine, temperature, System.currentTimeMillis());
            try {
                client.publish(topic, new MqttMessage(json.getBytes(StandardCharsets.UTF_8)));
                sent++;
                if (sent % 100 == 0) {
                    log.info("Sent {} messages so far", sent);
                }
            } catch (Exception e) {
                log.warn("Publish failed (will keep trying): {}", e.getMessage());
            }
            next += intervalNanos;
            long sleepMs = (next - System.nanoTime()) / 1_000_000L;
            if (sleepMs > 0) {
                Thread.sleep(sleepMs);
            } else {
                next = System.nanoTime();
            }
        }
    }

    private MqttClient connectWithRetry() throws InterruptedException {
        while (true) {
            try {
                MqttClient c = new MqttClient("tcp://" + host + ":1883",
                        "simulator-" + UUID.randomUUID(), new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                c.connect(options);
                log.info("Connected to MQTT broker at {}", host);
                return c;
            } catch (Exception e) {
                log.warn("Broker not ready yet ({}). Retrying in 3 seconds...", e.getMessage());
                Thread.sleep(3000);
            }
        }
    }
}
