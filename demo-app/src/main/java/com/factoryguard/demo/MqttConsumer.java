package com.factoryguard.demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Receives sensor messages from the MQTT broker and "processes" them.
 *
 * THE BUG: when BUG_MODE=true (release v1.1) every message is handled slowly,
 * so throughput collapses while the app still looks perfectly alive.
 */
@Component
public class MqttConsumer {

    private static final Logger log = LoggerFactory.getLogger(MqttConsumer.class);

    private final StatsService stats;

    @Value("${mqtt.host}")
    private String host;

    @Value("${mqtt.topic}")
    private String topic;

    @Value("${app.bug-mode}")
    private boolean bugMode;

    @Value("${app.bug-delay-ms}")
    private long bugDelayMs;

    private volatile MqttClient client;
    private volatile boolean running = true;

    public MqttConsumer(StatsService stats) {
        this.stats = stats;
    }

    @PostConstruct
    void start() {
        Thread t = new Thread(this::connectLoop, "mqtt-connect");
        t.setDaemon(true);
        t.start();
    }

    private void connectLoop() {
        while (running) {
            try {
                final MqttClient c = new MqttClient("tcp://" + host + ":1883",
                        "demo-app-" + UUID.randomUUID(), new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                c.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        stats.markConnected();
                        try {
                            c.subscribe(topic, 0);
                            log.info("Subscribed to {} (reconnect={})", topic, reconnect);
                        } catch (Exception e) {
                            log.warn("Subscribe failed: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
                    }

                    @Override
                    public void messageArrived(String t, MqttMessage message) throws Exception {
                        handle();
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                    }
                });
                c.connect(options);
                client = c;
                log.info("Connected to MQTT broker at {} (version bugMode={})", host, bugMode);
                return;
            } catch (Exception e) {
                log.warn("Broker not ready yet ({}). Retrying in 3 seconds...", e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handle() throws InterruptedException {
        if (bugMode) {
            Thread.sleep(bugDelayMs);   // the faulty release is slow
        }
        stats.recordProcessed();
    }

    @PreDestroy
    void stop() {
        running = false;
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception ignored) {
        }
    }
}
