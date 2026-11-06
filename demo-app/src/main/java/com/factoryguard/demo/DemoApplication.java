package com.factoryguard.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A tiny stand-in for a factory monitoring application.
 * It listens for sensor messages over MQTT and counts how many it has processed.
 */
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
